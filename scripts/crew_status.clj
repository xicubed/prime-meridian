#!/usr/bin/env bb
;; Crew status for the game: ask Herdr for every agent's lifecycle state, and for a
;; blocked member also read its screen and parse its permission prompt, then write it all
;; to state/crew.edn (gitignored, outside public/, served by pm.dev-http; like the twin's mirrors.edn), which the game polls.
;;
;;   bb crew-status          # every 2 s until stopped
;;   bb crew-status --once   # one write
;;
;; Writes names, states, titles and parsed prompts only — never a raw screen:
;;   {:as-of 1794100000000 :agents {:un-automate-monde {:status :blocked :since 1794099000000
;;                                                       :prompt {:id .. :kind :bash :text .. :risky false :since ..}}}
;;    :titles {:un-automate-monde "Un Automate (monde)" ...}}
;; A prompt's map now has 5 keys (:since is the first poll that saw it): a
;; member only gets one once the same dialog has been seen on two
;; consecutive polls at least pr/confirm-ms apart (pm.prompt/observe-prompts).
;; An agent's own :since is when its current status began (kept across
;; writes while the status doesn't change); pm.crew/stalled? reads it to
;; catch an agent that stops reporting while busy past c/stall-ms (GDD
;; §4.4, §4.8, §3.1), so nothing shows busy forever.
;; States are Herdr's: :idle :working :blocked :done :unknown. Herdr not running → :agents {}.

(require '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.java.io :as io]
         '[pm.bridge :as b]
         '[pm.prompt :as pr])

(def out-file "state/crew.edn")
(def crew-json-file ".crew/crew.json")
(def every-ms 2000)
(def herdr (or (System/getenv "HERDR") "herdr"))

(defn- repo-path []
  (.getCanonicalPath (io/file ".")))

(defn- crew-json []
  (try (json/parse-string (slurp crew-json-file) true) (catch Exception _ nil)))

(defn agents []
  (try
    (let [{:keys [exit out]} (p/sh [herdr "agent" "list"])]
      (if (zero? exit)
        (b/agent-statuses (json/parse-string out true))
        {}))
    (catch Exception _ {})))

(defn- screen-for [member]
  (try
    (let [{:keys [exit out]} (p/sh (b/step-argv herdr member [:read]))]
      (when (zero? exit) out))
    (catch Exception _ nil)))

(defn screens-for [agents members]
  (let [to-read (pr/screens-to-read agents members)]
    (into {} (map (fn [m] [m (screen-for m)]) to-read))))

(defn working-screens
  "Screens of the members Herdr says are working: a busy agent's screen keeps
   changing (its spinner and timer tick), so one that stops changing has
   stopped reporting (with-since, pm.crew/stalled?)."
  [agents members]
  (into {} (keep (fn [[k v]] (when (and (= :working (:status v)) (contains? members k))
                               [k (screen-for k)])))
        agents))

(defn with-since
  "Attaches :since (ms) to each agent entry: the last time it showed any
   sign of life -- its status changed, or (while working) its screen did.
   Kept from the previous write while neither changes, set to `now` the
   moment one does, so pm.crew/stalled? marks only an agent that has gone
   silent mid-work (GDD §4.4, §4.8, §3.1), never one that's just busy."
  [agents prev now screens]
  (into {}
        (map (fn [[k v]]
               (let [pv    (get prev k)
                     act   (hash [(:status v) (get screens k)])
                     since (if (and pv (= (:act pv) act)) (:since pv) now)]
                 [k (assoc v :since since :act act)])))
        agents))

(defn write! [m titles]
  (let [f   (io/file out-file)
        tmp (io/file (str out-file ".tmp"))]
    (io/make-parents f)
    (spit tmp (pr-str {:as-of (System/currentTimeMillis) :agents m :titles titles}))
    (.renameTo tmp f)))

(defn- poll [members seen]
  (let [a   (agents)
        scr (screens-for a members)]
    (pr/observe-prompts a scr (repo-path) seen (System/currentTimeMillis))))

(defn -main [& args]
  (if (some #{"--once"} args)
    (let [members (b/roster (crew-json))
          mset    (set (keys members))
          r1      (poll mset nil)
          _       (Thread/sleep pr/confirm-ms)
          r2      (poll mset (:seen r1))]
      (write! (with-since (:agents r2) {} (System/currentTimeMillis) {}) members))
    (loop [last nil seen nil prev {}]
      (let [members (b/roster (crew-json))
            {:keys [agents seen]} (poll (set (keys members)) seen)
            now     (System/currentTimeMillis)
            agents' (with-since agents prev now (working-screens agents (set (keys members))))]
        ;; rewrite when something changed (a prompt change counts), and every 10 s anyway
        ;; so :as-of stays fresh
        (when (or (not= agents last) (zero? (mod (quot now every-ms) 5)))
          (write! agents' members))
        (Thread/sleep every-ms)
        (recur agents seen agents')))))

(apply -main *command-line-args*)
