#!/usr/bin/env bb
;; Crew status for the game: ask Herdr for every agent's lifecycle state, and for a
;; blocked member also read its screen and parse its permission prompt, then write it all
;; to state/crew.edn (gitignored, outside public/, served by pm.dev-http; like Hal's mirrors.edn), which the game polls.
;;
;;   bb crew-status          # every 2 s until stopped
;;   bb crew-status --once   # one write
;;
;; Writes names, states, titles and parsed prompts only — never a raw screen:
;;   {:as-of 1794100000000 :agents {:un-automate-monde {:status :blocked
;;                                                       :prompt {:id .. :kind :bash :text .. :risky false}}}
;;    :titles {:un-automate-monde "Un Automate (monde)" ...}}
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

(defn with-screens [agents members]
  (let [to-read (pr/screens-to-read agents members)
        screens (into {} (map (fn [m] [m (screen-for m)]) to-read))]
    (pr/with-prompts agents screens (repo-path))))

(defn write! [m titles]
  (let [f   (io/file out-file)
        tmp (io/file (str out-file ".tmp"))]
    (io/make-parents f)
    (spit tmp (pr-str {:as-of (System/currentTimeMillis) :agents m :titles titles}))
    (.renameTo tmp f)))

(defn -main [& args]
  (if (some #{"--once"} args)
    (let [members (b/roster (crew-json))
          a       (with-screens (agents) (set (keys members)))]
      (write! a members))
    (loop [last nil]
      (let [members (b/roster (crew-json))
            a       (with-screens (agents) (set (keys members)))]
        ;; rewrite when something changed (a prompt change counts), and every 10 s anyway
        ;; so :as-of stays fresh
        (when (or (not= a last) (zero? (mod (quot (System/currentTimeMillis) every-ms) 5)))
          (write! a members))
        (Thread/sleep every-ms)
        (recur a)))))

(apply -main *command-line-args*)
