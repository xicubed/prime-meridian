#!/usr/bin/env bb
;; blueprint-draft (TASKS T-13): a crew member draws a look with no :at/:yaw and asks to put it
;; near a landmark; this finds a level spot on open ground or under a tree's crown, searching
;; out to :max-search-m, clear of everything else and every walker's route, and proposes the
;; full blueprint through the existing bb blueprint-add -- one writer, one validator.
;;
;;   bb blueprint-draft <member> <draft.edn> --near <landmark|x,z> [--dry-run]
;;   bb blueprint-draft <member> <draft.edn> --at <here|x,z> [--dry-run]
;;   bb blueprint-draft --landmarks
;;
;; --at puts the draft's base exactly at the middle of the player's screen
;; (state/view.edn's :center, at most 30 s old) or at x,z, its front (+z)
;; turned to face the camera; no ground checks.
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

(defn- finish!
  "The shared tail once a spot is found: validate `bp`, then either print
   (dry-run) or hand it to bb blueprint-add and print on success."
  [bp member dry-run print-lines!]
  (let [problems (bf/problems bp)]
    (when (seq problems)
      (fail! (str "invalid draft: " (clojure.string/join "; " problems))))
    (if dry-run
      (do (print-lines!)
          (System/exit 0))
      (let [tmp (java.io.File/createTempFile "blueprint-draft" ".edn")]
        (try
          (spit tmp (pr-str bp))
          (let [r (p/shell {:continue true} "bb" "scripts/blueprint_add.clj" (name member) (str tmp))]
            (if (zero? (:exit r))
              (do (print-lines!)
                  (System/exit 0))
              (System/exit 1)))
          (finally (.delete tmp)))))))

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
                  lm (lot/resolve-landmark (lot/landmarks ground estate file-bps view') near)
                  print-lines! (fn []
                                 (println (lot/placed-line draft (:word lm) result))
                                 (when-let [moved (lot/moved-line (get-in estate [:blueprint-text :moved])
                                                                   (:word lm) result)]
                                   (println moved)))]
              (finish! bp member dry-run print-lines!))))

        :at
        (let [{:keys [path member at dry-run]} parsed
              draft  (or (read-edn path) (fail! (str "can't read " path)))
              raw    (read-edn (view-file))
              result (if (= :here at)
                       (view/at-here raw (System/currentTimeMillis))
                       (view/at-xz at ground raw (System/currentTimeMillis)))]
          (if-not (:ok result)
            (fail! (:reason result))
            (let [bp (lot/blueprint draft result)
                  print-lines! (fn [] (println (lot/at-line draft result)))]
              (finish! bp member dry-run print-lines!))))))))
