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
(def ^:private left-border-re #"^│\s?(.*)$")
(def ^:private proceed-line "Do you want to proceed?")
(def ^:private bash-line "Bash command")
(def ^:private option-re #"^(❯\s*)?1\.\s+Yes\b")
(def ^:private rule-re #"^╌{3,}$")

(def ^:private question-re #"^Do you want to .+\?$")
(def ^:private numbered-re #"^(?:❯\s*)?(\d{1,2})\.\s+\S.*$")
(def ^:private footer-re   #"^Esc to cancel\b.*$")
(def ^:private context-stop-re #"^[─╌]{3,}$")

(def confirm-ms 2000)

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
                (let [r0 (first R) r1 (second R)
                      raw (subvec ls (inc r0) r1)
                      unbordered (mapv (fn [l]
                                          (if-let [[_ g1] (re-matches left-border-re l)]
                                            (str/trim g1)
                                            l))
                                        raw)]
                  (into [] (remove str/blank?) unbordered)))]
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

(def risk-reasons
  "Runs parallel to risky-res: the phrase for the first one that matches."
  ["changes git state" "deletes files" "reaches the network" "reaches the network"
   "reaches another machine" "reaches another machine" "installs software"
   "installs software" "installs software" "runs as root" "changes system services"])

(defn risk-reason
  "Why a prompt is risky, for the twin's chat: the first match wins, nil
   when nothing explains it (the caller falls back to \"flagged risky\")."
  [kind text repo]
  (cond
    (not= kind :bash) "not a plain shell command"
    :else
    (or (first (keep (fn [[re phrase]] (when (re-find re text) phrase))
                      (map vector risky-res risk-reasons)))
        (when (and (re-find write-marker-re text)
                   (some #(outside-path? repo %) (re-seq token-re text)))
          "writes outside the repo")
        (when (>= (count text) max-text) "too long to check"))))

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

(defn- numbered-n [line]
  (when-let [[_ n] (re-matches numbered-re line)]
    #?(:clj (Long/parseLong n) :cljs (js/parseInt n 10))))

(defn- dialog-info
  "When screen ends in a real permission dialog: {:ls trimmed-lines :q
   index-of-the-question-line}. Else nil."
  [screen]
  (when (string? screen)
    (let [ls0 (mapv strip-line (str/split-lines screen))
          ls  (loop [v ls0]
                (if (and (seq v)
                         (let [l (peek v)] (or (str/blank? l) (boolean (re-matches footer-re l)))))
                  (recur (pop v))
                  v))
          q   (last (keep-indexed (fn [i l] (when (re-matches question-re l) i)) ls))]
      (when q
        (let [opts (subvec ls (inc q))
              nums (keep numbered-n opts)]
          (when (and (seq opts)
                     (not-any? str/blank? opts)
                     (= 1 (numbered-n (first opts)))
                     (>= (count nums) 2)
                     (= nums (vec (range 1 (inc (count nums))))))
            {:ls ls :q q}))))))

(defn prompt-screen? [screen]
  (boolean (dialog-info screen)))

(def ^:private opt-line-re #"^(?:❯\s*)?(\d{1,2})\.\s+(.*)$")
(def ^:private always-text-re #"(?i)^Yes, (?:and don['’]t ask again\b|allow\b)")

(defn always-option
  "The option number the dialog offers for \"don't ask again\" / \"allow …
   this session/project\", or nil (Yes/No only, or not a dialog)."
  [screen]
  (when-let [{:keys [ls q]} (dialog-info screen)]
    (first (keep (fn [l]
                   (when-let [[_ ns txt] (re-matches opt-line-re (str/trim l))]
                     (let [n #?(:clj (Long/parseLong ns) :cljs (js/parseInt ns 10))]
                       (when (and (>= n 2) (re-find always-text-re txt)) n))))
                 (subvec ls (inc q))))))

(defn with-always
  "agents with [:prompt :always] set to always-option's screen for every
   blocked member whose screen offers one. Every other entry unchanged."
  [agents screens]
  (into {}
        (map (fn [[m v]]
               (let [scr (get screens m)]
                 (if (and (= :blocked (:status v)) (map? (:prompt v)) (string? scr)
                          (integer? (always-option scr)))
                   [m (assoc-in v [:prompt :always] (always-option scr))]
                   [m v]))))
        agents))

(defn read-prompt [screen repo]
  (when-let [{:keys [ls q]} (dialog-info screen)]
    (let [p (prompt-of screen repo)]
      (if (= :bash (:kind p))
        p
        (let [question-line (nth ls q)
              ctx (loop [i (dec q) acc []]
                    (if (or (< i 0) (= (count acc) 3))
                      (vec (reverse acc))
                      (let [l (nth ls i)]
                        (if (or (str/blank? l) (re-matches context-stop-re l))
                          (vec (reverse acc))
                          (recur (dec i) (conj acc l))))))
              t (str/join " · " (conj ctx question-line))]
          {:id (prompt-id :unknown t) :kind :unknown :text (cap-text t) :risky true})))))

(defn observe-prompts [agents screens repo seen now]
  (reduce
    (fn [acc [m v]]
      (if (not= :blocked (:status v))
        (update acc :agents assoc m v)
        (let [scr (get screens m)]
          (if-not (string? scr)
            (update acc :agents assoc m v)
            (let [p (read-prompt scr repo)]
              (if (nil? p)
                (update acc :agents assoc m {:status :working})
                (let [s (get seen m)]
                  (cond
                    (and s (= (:id s) (:id p)) (>= (- now (:at s)) confirm-ms))
                    (-> acc
                        (update :agents assoc m {:status :blocked :prompt (assoc p :since (:at s))})
                        (update :seen assoc m s))

                    (and s (= (:id s) (:id p)))
                    (-> acc
                        (update :agents assoc m {:status :working})
                        (update :seen assoc m s))

                    :else
                    (-> acc
                        (update :agents assoc m {:status :working})
                        (update :seen assoc m {:id (:id p) :at now}))))))))))
    {:agents {} :seen {}}
    agents))
