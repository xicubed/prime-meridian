(ns pm.closing
  "Closing time (GDD §2.3, §4.8): bedtime is declared data (`:bedtime` on the
   world, default 22:30) -- a rule the player set, not a lock the software
   imposes. Last call shows 15 minutes before it; at bedtime the estate goes
   to night and stays there for the rest of that local day, whether or not
   the player stays late. Pure: `now` (epoch ms) and `offset-min` (minutes to
   add to `now` to get local wall-clock time -- same convention as
   `pm.talk/clock-text`) are always passed in; no js/Date here."
  (:require [clojure.string :as str]
            [pm.content :as c]))

(def default-bedtime "22:30")
(def last-call-before-m 15)

(def ^:private bedtime-re #"^([01]\d|2[0-3]):[0-5]\d$")

(defn bedtime
  "The world's `:bedtime`, or the default (22:30) when it's missing or not a
   valid \"HH:MM\" 24h string."
  [world]
  (let [b (:bedtime world)]
    (if (and (string? b) (re-matches bedtime-re b)) b default-bedtime)))

(defn- hh-mm [hhmm]
  (mapv #(js/parseInt % 10) (str/split hhmm #":")))

(defn- minute-of-day [hhmm]
  (let [[h m] (hh-mm hhmm)]
    (+ (* 60 h) m)))

(defn bedtime-text
  "Bedtime in 12-hour clock form (\"10:30 PM\"), for filling the twin's
   {bedtime} lines."
  [world]
  (let [[h m] (hh-mm (bedtime world))
        h12  (let [r (mod h 12)] (if (zero? r) 12 r))
        mm   (if (< m 10) (str "0" m) (str m))
        ampm (if (< h 12) "AM" "PM")]
    (str h12 ":" mm " " ampm)))

(defn- local-minute [now offset-min]
  (let [off (if (number? offset-min) offset-min 0)
        ms  (mod (+ now (* off 60000)) 86400000)]
    (quot ms 60000)))

(defn- local-day [now offset-min]
  (let [off (if (number? offset-min) offset-min 0)]
    (quot (+ now (* off 60000)) 86400000)))

(defn last-call?
  "True in the 15-minute window before bedtime, up to (not including)
   bedtime itself."
  [world now offset-min]
  (if-not (number? now)
    false
    (let [b (minute-of-day (bedtime world))
          n (local-minute now offset-min)]
      (and (>= n (- b last-call-before-m)) (< n b)))))

(defn closing-time?
  "True at bedtime, and for the rest of that local day (GDD §2.3): the
   estate's gone to night."
  [world now offset-min]
  (and (number? now)
       (>= (local-minute now offset-min) (minute-of-day (bedtime world)))))

(defn asking-to-stay?
  "True once bedtime's hit and the twin's one-time stay-late ask is still
   open (GDD §2.3, §4.8): shown from the moment `tick` marks the day closed
   until `stay-late` answers it. Never re-opens that same local day."
  [world]
  (let [c (:closing world)]
    (and (boolean (:closed? c)) (not (:stay-late? c)) (not (:closed-up? c)))))

(defn stay-late
  "Answers the twin's ask: the player's staying, so the estate stays at
   night with no further prompt tonight (GDD §2.3, §4.8) -- `asking-to-stay?`
   goes false for the rest of this local day, and the twin says so once over
   the same :hal banner path as every other closing-time line."
  [world now]
  (if-not (number? now)
    world
    (-> world
        (update :closing assoc :stay-late? true)
        (c/hal-say c/hal-lines :stay-late {} now))))

(defn close-up
  "The other answer to the twin's ask: the player's done for the night. The
   twin says goodnight and the estate jacks out (the same freeze as the
   phone booth, GDD §2.3), the booth's sign reading `sign` -- it's still the
   player's call: clicking the booth comes back in (`pm.world/jack-in`), and
   the ask doesn't return tonight."
  [world sign now]
  (if-not (number? now)
    world
    (-> world
        (update :closing assoc :closed-up? true)
        (assoc :jack {:out? true :reason :closing :line sign :at now
                      :log-n (count (:log world))})
        (c/hal-say c/hal-lines :close-up {} now))))

(defn tick
  "Advances closing time's own little bit of state in `world`: fires the
   twin's last-call line once as that window opens, and its goodnight line
   once at bedtime (same `:hal` banner path as jack-in, GDD §4.8) --
   `pm.content/hal-say` under `c/hal-lines` -- each once per local day. A
   no-op once both have already fired today; a new local day resets them."
  [world now offset-min]
  (if-not (number? now)
    world
    (let [day   (local-day now offset-min)
          prev  (:closing world)
          state (if (= day (:day prev)) prev {:day day})
          vars  {:bedtime (bedtime-text world)}]
      (cond
        (and (closing-time? world now offset-min) (not (:closed? state)))
        (-> world
            (assoc :closing (assoc state :closed? true :last-call? true))
            (c/hal-say c/hal-lines :closing-time vars now))

        (and (last-call? world now offset-min) (not (:last-call? state)))
        (-> world
            (assoc :closing (assoc state :last-call? true))
            (c/hal-say c/hal-lines :last-call vars now))

        (not= state prev)
        (assoc world :closing state)

        :else world))))
