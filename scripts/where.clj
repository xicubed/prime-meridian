#!/usr/bin/env bb
;; bb where (TASKS T-19): prints where the player is looking and where every
;; walker is, from the game's own view (state/view.edn) -- so the twin can
;; read a few lines instead of asking for a screenshot.
;;
;;   bb where
;;
;; All I/O; every decision is pm.view's / pm.lot's.

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[pm.blueprint-file :as bf]
         '[pm.lot :as lot]
         '[pm.view :as view])

(defn- fail! [text]
  (binding [*out* *err*] (println (str "where: " text)))
  (System/exit 1))

(defn- view-file []
  (or (System/getenv "PM_VIEW_FILE") "state/view.edn"))

(defn- ground-file []
  (or (System/getenv "PM_GROUND_FILE") "state/ground.edn"))

(defn- blueprints-file []
  (or (System/getenv "PM_BLUEPRINTS_FILE") bf/state-path))

(defn- read-edn [path]
  (let [f (io/file path)]
    (when (.exists f)
      (try (edn/read-string (slurp f)) (catch Exception _ nil)))))

(when (seq *command-line-args*)
  (binding [*out* *err*] (println "usage: bb where"))
  (System/exit 2))

(let [now (System/currentTimeMillis)
      seen (view/fresh (read-edn (view-file)) now)]
  (if-not (:ok seen)
    (fail! (:reason seen))
    (let [ground (read-edn (ground-file))
          estate (edn/read-string (slurp "resources/estate.edn"))
          file-bps (:blueprints (bf/normalize (read-edn (blueprints-file))))]
      (doseq [line (view/where-lines (:view seen) now ground (lot/landmarks ground estate file-bps))]
        (println line))
      (System/exit 0))))
