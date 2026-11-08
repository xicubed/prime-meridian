#!/usr/bin/env bb
;; The twin's automatic half: answers the crew's routine permission prompts so
;; the twin doesn't have to click through every `ls`. Every 2 s it polls
;; state/crew.edn for a member whose published prompt (already
;; two-poll confirmed by bb crew-status) is a plain shell command the one risk
;; classifier (pm.prompt/risky?) calls safe, waits :answer-after-s (data in
;; the estate's :crew-talk, default 5 s), re-reads that member's screen right
;; now, and sends "1" only if it still shows the same prompt — reusing the
;; bridge's own decide rules, never a copy. It never declines, and never
;; answers a risky or unreadable prompt: those wait for the player or the
;; twin. It holds off entirely while state/hold.edn's "I'll answer" switch is
;; on. Replaces the twin's private driver scripts.
;;
;;   bb crew-fielder [--dry-run]
;;
;; Logs every attempt (never a screen or a token) to crew/fielder.log.

(require '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[pm.bridge :as b]
         '[pm.prompt :as pr]
         '[pm.crew-log :as cl]
         '[pm.fielder :as f])

(import '[java.time Instant])

(def herdr (or (System/getenv "HERDR") "herdr"))
(def crew-file "state/crew.edn")
(def hold-file "state/hold.edn")
(def crew-log-file "state/crew-log.edn")
(def crew-json-file ".crew/crew.json")
(def estate-file "resources/estate.edn")
(def log-file "crew/fielder.log")
(def start-message "bb crew-fielder: answering routine prompts every 2 s (--dry-run: nothing sent)")

(defn- repo-path []
  (.getCanonicalPath (io/file ".")))

(defn- slurp-or-nil [path]
  (try (slurp path) (catch Exception _ nil)))

(defn- read-edn [path]
  (try (edn/read-string (slurp-or-nil path)) (catch Exception _ nil)))

(defn- crew-json []
  (try (json/parse-string (slurp crew-json-file) true) (catch Exception _ nil)))

(defn- log! [member status reason]
  (try
    (io/make-parents (io/file log-file))
    (spit log-file (str (b/log-line (str (Instant/now)) member :allow status reason) "\n") :append true)
    (catch Exception _)))

(defn- live-for
  "The member's status and (when blocked) its prompt, read right now. Same
   rule set as crew_bridge.clj's own live-for."
  [member]
  (let [{:keys [exit out]} (p/sh (b/step-argv herdr member [:list]))
        status (if (zero? exit)
                 (:status (get (b/agent-statuses (json/parse-string out true)) member {:status :unknown}))
                 :unknown)]
    {:status status
     :prompt (when (= status :blocked)
               (let [{:keys [exit out]} (p/sh (b/step-argv herdr member [:read]))]
                 (when (zero? exit) (pr/read-prompt out (repo-path)))))}))

(defn- write-crew-log! [entry]
  (try
    (let [cur  (cl/parse (slurp-or-nil crew-log-file))
          log' (cl/append cur entry)
          f    (io/file crew-log-file)
          tmp  (io/file (str crew-log-file ".tmp"))]
      (io/make-parents f)
      (spit tmp (pr-str log'))
      (.renameTo tmp f))
    (catch Exception _ false)))

(defn- ctx-now [tried]
  {:crew      (read-edn crew-file)
   :hold      (cl/parse-hold (slurp-or-nil hold-file))
   :members   (set (keys (b/roster (crew-json))))
   :log       (cl/parse (slurp-or-nil crew-log-file))
   :tried     tried
   :now       (System/currentTimeMillis)
   :crew-talk (:crew-talk (read-edn estate-file))})

(defn- handle-one!
  "Answers one due member, unless hold has come on since `due` was
   computed. Returns tried, updated with this sighting (unless held)."
  [member prompt dry-run? tried]
  (if (cl/hold-on? (cl/parse-hold (slurp-or-nil hold-file)))
    tried
    (let [live  (live-for member)
          d     (f/attempt prompt live)
          now   (System/currentTimeMillis)
          tried (f/remember tried member prompt)]
      (cond
        dry-run?
        (if (:ok d)
          (log! member "-" "dry-run: would allow")
          (log! member (:status d) (:reason d)))

        (:ok d)
        (let [ok? (every? (fn [step] (zero? (:exit (p/sh (b/step-argv herdr member step))))) (:steps d))]
          (if ok?
            (do
              (when-let [e (f/log-entry now member live)]
                (write-crew-log! e))
              (log! member 200 "allowed"))
            (log! member 502 "herdr failed")))

        :else
        (log! member (:status d) (:reason d)))
      tried)))

(defn -main [& args]
  (let [dry-run? (boolean (some #{"--dry-run"} args))]
    (println start-message)
    (log! nil "-" start-message)
    (loop [tried {}]
      (let [tried' (try
                     (let [ctx (ctx-now tried)]
                       (reduce (fn [tr {:keys [member prompt]}]
                                 (handle-one! member prompt dry-run? tr))
                               tried
                               (f/due ctx)))
                     (catch Exception ex
                       (log! nil "-" (str "error: " (.getMessage ex)))
                       tried))]
        (Thread/sleep f/every-ms)
        (recur tried')))))

(apply -main *command-line-args*)
