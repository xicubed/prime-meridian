(ns pm.juice
  "The juice meter (GDD §2.1, §4.4, §4.8): each AI login's estimated
   share of its plan's usage window left and its refill time, on the
   HUD; once the estimate crosses 80% of the cap, the twin also says a
   one-line warning, so the player never has to leave the game to check
   usage in a browser. Pure: `now` is always passed in (epoch ms); no
   js/Date here.

   `world`'s `:logins` is declared data --
   `{id {:name :tokens-used :cap :refill-at}}`. `:tokens-used` and
   `:cap` are both token counts for the current usage window; v1
   estimates usage from the tokens the bridge has seen rather than each
   provider's own quota (GDD §4.4), via `estimate-juice`. `:refill-at`
   is the plan's own local refill time as an \"HH:MM\" 24h string, the
   same declared-clock convention `:bedtime` uses (`pm.closing`). The
   bridge fills these for real once the live bridge reports token
   counts; until then this is sample data, same as every other
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
       (number? (:tokens-used login))
       (number? (:cap login)) (pos? (:cap login))))

(defn estimate-juice
  "Estimates the share of a usage window a login has used so far (0-1),
   from the tokens the bridge has seen against the plan's cap (GDD
   §4.4: v1 estimates usage from tokens the bridge sees, not each
   provider's own quota). 0 when `tokens-used` or `cap` isn't a finite
   number, `tokens-used` is negative, or `cap` isn't positive."
  [tokens-used cap]
  (if (and (number? tokens-used) (number? cap) (pos? cap) (>= tokens-used 0))
    (min 1.0 (/ tokens-used cap))
    0))

(defn- pct-of [login]
  (estimate-juice (:tokens-used login) (:cap login)))

(defn juice-warning
  "The twin's one-line warning for one login's usage estimate
   (`{:name :tokens-used :cap :refill-at}`) -- nil below `warn-at`, or
   with unusable data (GDD §2.1: \"Claude's at 80%, refills at 2\", said
   as \"used 80% of its window\" so a glance can't read it as 80% left)."
  [login]
  (when (usable? login)
    (let [p (pct-of login)]
      (when (>= p warn-at)
        (let [pct (str (js/Math.round (* 100 p)) "%")
              r   (refill-text (:refill-at login))]
          ;; "used 83% of its window" (Michael: "is that done or full? make it clear")
          (str (:name login) "'s used " pct " of its window" (when r (str ", refills at " r)) "."))))))

(defn- over-ids
  "Ids of every login at or over `warn-at` right now."
  [logins]
  (set (keep (fn [[id login]] (when (and (usable? login) (>= (pct-of login) warn-at)) id))
             logins)))

(defn hud-segment
  "One HUD line per usable login: its estimated share of its usage
   window left and its refill time (GDD §2.1, §4.4, done-when), e.g.
   \"Claude 17% left, refills 2:00 PM\". nil when `logins` has nothing
   usable to show."
  [logins]
  (when (map? logins)
    (let [parts (keep (fn [[_ login]]
                         (when (usable? login)
                           (let [left (- 100 (js/Math.round (* 100 (pct-of login))))
                                 r    (refill-text (:refill-at login))]
                             (str (:name login) " " left "% left"
                                  (when r (str ", refills " r))))))
                       logins)]
      (when (seq parts) (str "juice: " (str/join "; " parts))))))

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
