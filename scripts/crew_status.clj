#!/usr/bin/env bb
;; Crew status for the game: ask Herdr for every agent's lifecycle state and write it to
;; public/state/crew.edn (gitignored, like Hal's mirrors.edn), which the game polls.
;;
;;   bb crew-status          # every 2 s until stopped
;;   bb crew-status --once   # one write
;;
;; Writes only names and states, nothing from the panes (no titles, no screens):
;;   {:as-of 1794100000000 :agents {:un-automate-monde {:status :working} ...}}
;; States are Herdr's: :idle :working :blocked :done :unknown. Herdr not running → :agents {}.

(require '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.java.io :as io])

(def out-file "public/state/crew.edn")
(def every-ms 2000)
(def herdr (or (System/getenv "HERDR") "herdr"))

(defn agents []
  (try
    (let [{:keys [exit out]} (p/sh [herdr "agent" "list"])]
      (if (zero? exit)
        (into (sorted-map)
              (for [a (get-in (json/parse-string out true) [:result :agents])
                    :let [n (:name a) s (:agent_status a)]
                    :when (and (string? n) (re-matches #"[a-z][a-z0-9_-]{0,31}" n))]
                [(keyword n) {:status (keyword (or s "unknown"))}]))
        {}))
    (catch Exception _ {})))

(defn write! [m]
  (let [f   (io/file out-file)
        tmp (io/file (str out-file ".tmp"))]
    (io/make-parents f)
    (spit tmp (pr-str {:as-of (System/currentTimeMillis) :agents m}))
    (.renameTo tmp f)))

(defn -main [& args]
  (if (some #{"--once"} args)
    (write! (agents))
    (loop [last nil]
      (let [m (agents)]
        ;; rewrite when something changed, and every 10 s anyway so :as-of stays fresh
        (when (or (not= m last) (zero? (mod (quot (System/currentTimeMillis) every-ms) 5)))
          (write! m))
        (Thread/sleep every-ms)
        (recur m)))))

(apply -main *command-line-args*)
