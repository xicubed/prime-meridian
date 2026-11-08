(ns pm.jumeaux
  "Les jumeaux: the player and their twin, read from :jumeaux data (the
   estate, or the world) into one flat names map. Pure, portable (bb +
   cljs). Never throws; a missing or malformed block falls back to
   defaults."
  (:require [clojure.string :as str]))

(def default-twin "Your twin")
(def default-player "You")
(def max-name 32)

(def ^:private control-char-re #"[\x00-\x1f\x7f]")

(defn twin-word [sex]
  (if (= sex :f) "jumelle" "jumeau"))

(defn pair-word [sex]
  (if (= sex :f) "jumelles" "jumeaux"))

(defn- clean-name [n fallback]
  (if (and (string? n) (not (re-find control-char-re n)))
    (let [t (str/trim n)]
      (if (and (not (str/blank? t)) (<= (count t) max-name))
        t
        fallback))
    fallback))

(defn names
  "The names map for `data` (the estate, or the world), from its :jumeaux
   block. Never throws: anything malformed or missing falls back to the
   defaults, field by field."
  [data]
  (let [b      (let [v (when (map? data) (:jumeaux data))] (if (map? v) v {}))
        player (let [v (:player b)] (if (map? v) v {}))
        twin   (let [v (:twin b)] (if (map? v) v {}))
        sex    (let [s (:sex player)] (when (contains? #{:m :f} s) s))]
    {:player (clean-name (:name player) default-player)
     :sex sex
     :twin (clean-name (:name twin) default-twin)
     :twin-word (twin-word sex)
     :pair-word (pair-word sex)}))

(def defaults (names nil))

(defn twin
  "The twin's display name from a names map, or the default for a nil/bad
   one. Every display function goes through this."
  [jum]
  (if (and (map? jum) (string? (:twin jum)) (not (str/blank? (:twin jum))))
    (:twin jum)
    default-twin))
