(ns pm.juice
  "The juice meter's warning (GDD §2.1, §4.4, §4.8): once an AI login's
   usage estimate crosses 80% of its plan's cap, the twin says a one-line
   warning with the refill time, so the player never has to leave the
   game to check usage in a browser. Pure: `now` is always passed in
   (epoch ms); no js/Date here.

   `world`'s `:logins` is declared data -- `{id {:name :pct :refill-at}}`.
   `:pct` is the usage estimate's fraction of the cap used (0-1; v1
   estimates it from the tokens the bridge sees, GDD §4.4). `:refill-at`
   is the plan's own local refill time as an \"HH:MM\" 24h string, the
   same declared-clock convention `:bedtime` uses (`pm.closing`). The
   bridge fills both for real once the juice meter itself is built
   (`:juice-meter`); until then this is sample data, same as every other
   connection in demo mode (GDD §4.6)."
  (:require [clojure.string :as str]
            [pm.content :as c]))

(def warn-at
  "The estimate crosses this fraction of the cap before the twin warns
   (GDD done-when: 80%)."
  0.8)

(def ^:private hhmm-re #"^([01]\d|2[0-3]):[0-5]\d$")

(defn- refill-text
  "\"HH:MM\" 24h -> 12-hour clock text (\"2:00 PM\"); nil when it isn't one."
  [hhmm]
  (when (and (string? hhmm) (re-matches hhmm-re hhmm))
    (let [[h m] (mapv #(js/parseInt % 10) (str/split hhmm #":"))
          h12   (let [r (mod h 12)] (if (zero? r) 12 r))
          mm    (if (< m 10) (str "0" m) (str m))
          ampm  (if (< h 12) "AM" "PM")]
      (str h12 ":" mm " " ampm))))

(defn- usable? [login]
  (and (map? login)
       (string? (:name login)) (not (str/blank? (:name login)))
       (number? (:pct login))))

(defn juice-warning
  "The twin's one-line warning for one login's usage estimate
   (`{:name :pct :refill-at}`) -- nil below `warn-at`, or with unusable
   data (GDD §2.1: \"Claude's at 80%, refills at 2\")."
  [login]
  (when (and (usable? login) (>= (:pct login) warn-at))
    (let [pct (str (js/Math.round (* 100 (:pct login))) "%")
          r   (refill-text (:refill-at login))]
      (str (:name login) "'s at " pct (when r (str ", refills at " r)) "."))))

(defn- over-ids
  "Ids of every login at or over `warn-at` right now."
  [logins]
  (set (keep (fn [[id login]] (when (and (usable? login) (>= (:pct login) warn-at)) id))
             logins)))

(defn tick
  "Advances the juice warning's own state in `world`: the first login (by
   id, for a stable pick when more than one crosses on the same tick)
   that's newly at or over `warn-at` since the last tick gets the twin's
   one-line warning -- once per crossing. It fires again for that same
   login only after it drops back below `warn-at` (a refill) and crosses
   again. A no-op when `world` has no `:logins` map or `now` isn't a
   number."
  [world now]
  (if-not (and (map? (:logins world)) (number? now))
    world
    (let [logins (:logins world)
          warned (set (:warned (:juice world)))
          over   (over-ids logins)
          fresh  (first (sort-by name (remove warned over)))
          world' (assoc world :juice {:warned over})]
      (if-let [text (and fresh (juice-warning (get logins fresh)))]
        (assoc world' :hal {:event :juice-warning :text text :until (+ now c/hal-ms)})
        world'))))
