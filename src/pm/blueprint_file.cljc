(ns pm.blueprint-file
  "The one validator for blueprints, shared by the game (`pm.blueprint`),
   the bridge and `bb blueprint-add`. Portable (bb + cljs): pure data in,
   pure data out, no atoms, no js/Date, no three.js. `now` is always an
   argument."
  (:require [clojure.string :as str]))

(def state-path "state/blueprints.edn")
(def empty-file {:as-of 0 :blueprints []})
(def id-re     #"[a-z0-9][a-z0-9-]{0,40}")   ; same as pm.bridge's real-id-re (bridge switches to this one)
(def member-re #"[a-z][a-z0-9-]{0,39}")
(def usage "usage: bb blueprint-add <member> <blueprint.edn> | bb blueprint-add --remove <id>")

(defn- valid-at? [at]
  (and (vector? at) (= 3 (count at)) (every? number? at)))

(defn problems
  "Every reason `bp` is not a well-formed blueprint, `[]` when it's fine.
   Exactly today's `pm.blueprint` rules, so `pm.blueprint/all` keeps every
   blueprint it keeps today."
  [bp]
  (if-not (map? bp)
    ["not a map"]
    (cond-> []
      (not (keyword? (:id bp)))                                      (conj ":id must be a keyword")
      (not (valid-at? (:at bp)))                                      (conj ":at must be [x y z], three numbers")
      (not (and (string? (:summary bp)) (not (str/blank? (:summary bp))))) (conj ":summary must be a non-blank string"))))

(defn valid? [bp]
  (empty? (problems bp)))

(defn normalize
  "`file` (the raw parsed `state/blueprints.edn`) as `{:as-of int :blueprints
   [map ...]}`: not a map (incl. nil) becomes `empty-file`; a non-integer
   :as-of becomes 0; :blueprints keeps only its map elements, in order, or
   `[]` when it isn't sequential. Other top-level keys dropped. Entries are
   not validated here."
  [file]
  (if-not (map? file)
    empty-file
    {:as-of (if (integer? (:as-of file)) (:as-of file) 0)
     :blueprints (if (sequential? (:blueprints file))
                   (vec (filter map? (:blueprints file)))
                   [])}))

(defn add
  "`file` with `bp` added by `member` at `now` ms:
   `{:ok file' :id id} | {:refused reason}`. `estate-ids` is a set of
   keywords; `now` ms. Checks in order, first failure wins: `bp` invalid,
   its id badly-formed, its id already in the estate, its id already in the
   file. `:as-of` always changes (`(max now (inc as-of))`), so the game
   never misses a write."
  [file estate-ids member bp now]
  (let [{:keys [as-of blueprints]} (normalize file)
        probs (problems bp)
        id    (:id bp)]
    (cond
      (seq probs)
      {:refused (str "invalid blueprint: " (str/join "; " probs))}

      (not (re-matches id-re (subs (str id) 1)))
      {:refused (str "id " id " must be lowercase letters, digits and dashes")}

      (contains? estate-ids id)
      {:refused (str "id " id " is already in the estate")}

      (some #(= id (:id %)) blueprints)
      {:refused (str "id " id " is already in state/blueprints.edn")}

      :else
      (let [entry (assoc (select-keys bp [:id :at :yaw :summary :look]) :by member)]
        {:ok {:as-of (max now (inc as-of)) :blueprints (conj blueprints entry)} :id id}))))

(defn remove-id
  "`file` with blueprint `id` removed: `{:ok file' :id id} | {:refused
   reason}`. Refused when `id` is in the estate (only file entries can be
   removed) or isn't in the file."
  [file estate-ids id now]
  (let [{:keys [as-of blueprints]} (normalize file)]
    (cond
      (contains? estate-ids id)
      {:refused (str "id " id " is in the estate; only state/blueprints.edn entries can be removed")}

      (not (some #(= id (:id %)) blueprints))
      {:refused (str "no blueprint " id " in state/blueprints.edn")}

      :else
      {:ok {:as-of (max now (inc as-of)) :blueprints (vec (remove #(= id (:id %)) blueprints))} :id id})))

(defn parse-args
  "`args` (a seq of strings, may be nil) as `{:mode :add :member kw :path
   s} | {:mode :remove :id kw} | {:usage usage}`."
  [args]
  (let [args (vec (or args []))]
    (cond
      (and (= 2 (count args)) (= "--remove" (first args)))
      (let [s (str/replace-first (second args) #"^:" "")]
        (if (re-matches id-re s)
          {:mode :remove :id (keyword s)}
          {:usage usage}))

      (and (= 2 (count args)) (not= "--remove" (first args)))
      (let [[m p] args]
        (if (and (re-matches member-re m) (not (str/blank? p)))
          {:mode :add :member (keyword m) :path p}
          {:usage usage}))

      :else
      {:usage usage})))
