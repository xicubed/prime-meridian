#!/usr/bin/env bb
;; blueprint-draft (TASKS T-13): a crew member draws a look with no :at/:yaw and asks to put it
;; near a landmark; this finds a level, open spot on the kit's sampled ground map, clear of
;; everything else and every walker's route, and proposes the full blueprint through the
;; existing bb blueprint-add -- one writer, one validator.
;;
;;   bb blueprint-draft <member> <draft.edn> --near <landmark|x,z> [--dry-run]
;;   bb blueprint-draft --landmarks
;;
;; All I/O; every decision is pm.lot's.

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[babashka.process :as p]
         '[pm.blueprint-file :as bf]
         '[pm.lot :as lot]
         '[pm.view :as view])

(defn- fail! [text]
  (binding [*out* *err*] (println (str "blueprint-draft: " text)))
  (System/exit 1))

(defn- ground-file []
  (or (System/getenv "PM_GROUND_FILE") "state/ground.edn"))

(defn- blueprints-file []
  (or (System/getenv "PM_BLUEPRINTS_FILE") bf/state-path))

(defn- view-file []
  (or (System/getenv "PM_VIEW_FILE") "state/view.edn"))

(defn- read-edn [path]
  (let [f (io/file path)]
    (when (.exists f)
      (try (edn/read-string (slurp f)) (catch Exception _ nil)))))

(defn- read-estate []
  (edn/read-string (slurp "resources/estate.edn")))

(let [parsed (lot/parse-args *command-line-args*)]
  (when (:usage parsed)
    (binding [*out* *err*] (println (:usage parsed)))
    (System/exit 2))
  (let [estate (read-estate)
        rules (:build-rules estate)]
    (when-not rules
      (fail! "resources/estate.edn has no :build-rules"))
    (let [ground (read-edn (ground-file))
          file-bps (:blueprints (bf/normalize (read-edn (blueprints-file))))
          seen (view/fresh (read-edn (view-file)) (System/currentTimeMillis))
          view' (when (:ok seen) (:view seen))]
      (case (:mode parsed)
        :landmarks
        (do (doseq [line (lot/landmark-lines (lot/landmarks ground estate file-bps view'))]
              (println line))
            (System/exit 0))

        :draft
        (let [{:keys [path member near dry-run]} parsed
              draft (or (read-edn path) (fail! (str "can't read " path)))
              result (lot/place ground estate file-bps rules draft near seen)]
          (if-not (:ok result)
            (fail! (:reason result))
            (let [bp (lot/blueprint draft result)
                  problems (bf/problems bp)
                  lm (lot/resolve-landmark (lot/landmarks ground estate file-bps view') near)]
              (when (seq problems)
                (fail! (str "invalid draft: " (clojure.string/join "; " problems))))
              (if dry-run
                (do (println (lot/placed-line draft (:word lm) result))
                    (System/exit 0))
                (let [tmp (java.io.File/createTempFile "blueprint-draft" ".edn")]
                  (try
                    (spit tmp (pr-str bp))
                    (let [r (p/shell {:continue true} "bb" "scripts/blueprint_add.clj" (name member) (str tmp))]
                      (if (zero? (:exit r))
                        (do (println (lot/placed-line draft (:word lm) result))
                            (System/exit 0))
                        (System/exit 1)))
                    (finally (.delete tmp))))))))))))
