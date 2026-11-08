(ns pm.prompt
  "Parsing Claude Code's permission screen into a bash command or an unknown
   prompt, with a stable id and a risk heuristic. Pure, portable (bb + cljs
   via the :test build). Never required by pm.core: the cljs branch needs
   Node's crypto, which the browser build must not pull in."
  (:require [clojure.string :as str]
            #?(:cljs ["crypto" :as crypto]))
  #?(:clj (:import [java.security MessageDigest])))

(def max-text 400)
(def tail-lines 6)

(def ^:private box-edge-re #"^\s*[╭╰]─+[╮╯]$")
(def ^:private box-side-re #"^\s*│(.*)│$")
(def ^:private proceed-line "Do you want to proceed?")
(def ^:private bash-line "Bash command")
(def ^:private option-re #"^(❯\s*)?1\.\s+Yes\b")
(def ^:private rule-re #"^╌{3,}$")

(defn- strip-line [line]
  (let [t (str/trimr line)
        side (re-matches box-side-re t)]
    (str/trim
      (cond
        (re-matches box-edge-re t) ""
        side (second side)
        :else t))))

(defn parse-screen [screen]
  (when (string? screen)
    (let [ls (mapv strip-line (str/split-lines screen))
          proceed-idxs (keep-indexed (fn [i l] (when (= l proceed-line) i)) ls)
          P  (last proceed-idxs)
          has-option? (and P (some #(re-matches option-re %) (subvec ls (inc P))))
          bash-idxs (when P (keep-indexed (fn [i l] (when (and (= l bash-line) (< i P)) i)) ls))
          H  (when (seq bash-idxs) (last bash-idxs))
          R  (when (and H P)
               (keep-indexed (fn [i l] (when (and (> i H) (< i P) (re-matches rule-re l)) i)) ls))
          cmd (when (and P has-option? H (>= (count R) 2))
                (let [r0 (first R) r1 (second R)]
                  (into [] (remove str/blank?) (subvec ls (inc r0) r1))))]
      (if (seq cmd)
        {:kind :bash :lines cmd :full (str/join " " cmd)}
        {:kind :unknown :full (str/join "\n" (take-last tail-lines (remove str/blank? ls)))}))))

(defn cap-text [s]
  (if (<= (count s) max-text)
    s
    (str (subs s 0 (dec max-text)) "…")))

(defn prompt-id [kind full]
  (let [s (str (name kind) "\n" full)]
    #?(:clj
       (let [^MessageDigest md (MessageDigest/getInstance "SHA-1")
             digest (.digest md (.getBytes s "UTF-8"))]
         (apply str (map #(format "%02x" (bit-and % 0xff)) digest)))
       :cljs
       (-> (.createHash crypto "sha1") (.update s "utf8") (.digest "hex")))))

(def ^:private risky-res
  [#"(?i)\bgit\b.*\b(add|commit|push|reset|checkout|stash|rm|clean|restore)\b"
   #"(?i)\brm\s+-"
   #"(?i)\bcurl\b"
   #"(?i)\bwget\b"
   #"(?i)\bssh\b"
   #"(?i)\bscp\b"
   #"(?i)\bnpm\s+(i|install|publish)\b"
   #"(?i)\bpip3?\s+install\b"
   #"(?i)\bbrew\s+install\b"
   #"(?i)\bsudo\b"
   #"(?i)\blaunchctl\b"])

(def ^:private write-marker-re
  #"(?i)>|\b(rm|tee|cp|mv|mkdir|touch|ln|rsync|dd|install|chmod|chown|truncate)\b|\bsed\s+-i")

(def ^:private token-re #"[^\s'\"`;&|<>()=]+")
(def ^:private dotdot-re #"(^|/)\.\.(/|$)")
(def ^:private safe-paths #{"/dev/null" "/dev/stdout" "/dev/stderr"})

(defn- outside-path? [repo tok]
  (and (not (contains? safe-paths tok))
       (or (str/starts-with? tok "~")
           (boolean (re-find dotdot-re tok))
           (and (str/starts-with? tok "/")
                (not (and (string? repo)
                          (or (= tok repo) (str/starts-with? tok (str repo "/")))))))))

(defn risky? [text repo]
  (boolean
    (or (some #(re-find % text) risky-res)
        (and (boolean (re-find write-marker-re text))
             (some #(outside-path? repo %) (re-seq token-re text))))))

(defn prompt-of [screen repo]
  (when-let [{:keys [kind lines full]} (parse-screen screen)]
    (let [risky (if (= kind :bash)
                  (boolean (or (> (count full) max-text)
                               (risky? full repo)
                               (risky? (str/join "" lines) repo)))
                  true)]
      {:id (prompt-id kind full) :kind kind :text (cap-text full) :risky risky})))

(defn screens-to-read [agents members]
  (vec (sort (keep (fn [[k v]] (when (and (= :blocked (:status v)) (contains? members k)) k))
                   agents))))

(defn with-prompts [agents screens repo]
  (into {}
        (map (fn [[k v]]
               (if (and (= :blocked (:status v)) (string? (get screens k)))
                 [k (assoc v :prompt (prompt-of (get screens k) repo))]
                 [k v])))
        agents))
