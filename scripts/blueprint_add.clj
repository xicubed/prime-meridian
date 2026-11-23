#!/usr/bin/env bb
;; blueprint-add (GDD §2.1, §4.8, TASKS T-13b): an agent proposes a blueprint, into the
;; private state/blueprints.edn, so its ghost appears in the estate within one state
;; poll -- no page reload.
;;
;;   bb blueprint-add <member> <blueprint.edn>
;;   bb blueprint-add --remove <id>
;;
;; Validates with pm.blueprint-file (the one validator, shared with the game and the
;; bridge): refuses an id already in the estate or the file, or a blueprint that's not
;; well-formed, each with one line on stderr saying why. Writes atomically (temp file +
;; rename). Known limit: two agents adding in the same instant can lose one write;
;; removing a file blueprint that was made real keeps it for this session, but it won't
;; come back after a reload.
;;
;; All I/O; the logic is pm.blueprint-file's.

(require '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[pm.blueprint-file :as bf])

(import '[java.nio.file Files StandardCopyOption])

(defn- fail! [text]
  (binding [*out* *err*] (println (str "blueprint-add: " text)))
  (System/exit 1))

(defn- target-file []
  (or (System/getenv "PM_BLUEPRINTS_FILE") bf/state-path))

(defn- read-target [path]
  (let [f (io/file path)]
    (if-not (.exists f)
      nil
      (let [v (try (edn/read-string (slurp f)) (catch Exception _ ::bad))]
        (if (or (= v ::bad) (not (map? v)))
          (fail! "state/blueprints.edn is not readable EDN; fix or delete it")
          v)))))

(defn- estate-ids []
  (set (keep :id (:blueprints (edn/read-string (slurp "resources/estate.edn"))))))

(defn- read-blueprint [path]
  (try (edn/read-string (slurp path)) (catch Exception _ (fail! (str "can't read " path)))))

(defn- write-atomically! [path data]
  (let [f   (io/file path)
        tmp (io/file (str path ".tmp"))]
    (io/make-parents f)
    (spit tmp (pr-str data))
    (Files/move (.toPath tmp) (.toPath f)
                (into-array java.nio.file.CopyOption [StandardCopyOption/ATOMIC_MOVE StandardCopyOption/REPLACE_EXISTING]))))

(let [parsed (bf/parse-args *command-line-args*)]
  (when (:usage parsed)
    (binding [*out* *err*] (println (:usage parsed)))
    (System/exit 2))
  (let [path (target-file)
        file (read-target path)
        now  (System/currentTimeMillis)
        ids  (estate-ids)
        r    (case (:mode parsed)
               :add    (bf/add file ids (:member parsed) (read-blueprint (:path parsed)) now)
               :remove (bf/remove-id file ids (:id parsed) now))]
    (cond
      (:refused r) (fail! (:refused r))
      :else (do (write-atomically! path (:ok r))
                (println (str (if (= :add (:mode parsed)) "added " "removed ") (name (:id r))))
                (System/exit 0)))))
