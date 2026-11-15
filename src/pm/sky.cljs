;; The sky follows the player's clock (GDD §2.3; TASKS N-1).
(ns pm.sky
  "The estate's sky follows local time: `:sky` in estate.edn lists the times
   the scenery kit's lighting changes (its own four states: day, golden,
   blue, night), so at night it's actually night, lamps and windows lit.
   Declared data, like :bedtime: a player in another season or latitude
   writes different times. Pure: the local minute of the day is passed in.

   `follow` is the whole rule, kept here so it's testable: set the hour the
   schedule wants whenever that changes, and otherwise leave the sky alone,
   so a player who picks Golden with the kit's own buttons keeps it until
   the next scheduled change."
  (:require [clojure.string :as str]))

(def hours #{:day :golden :blue :night})

(def ^:private hhmm-re #"^([01]\d|2[0-3]):[0-5]\d$")

(defn- minute-of [hhmm]
  (let [[h m] (map #(js/parseInt % 10) (str/split hhmm #":"))]
    (+ (* 60 h) m)))

(defn schedule
  "`:sky` from `world` as [[minute-of-day hour] ...], sorted; entries that
   aren't [\"HH:MM\" one-of-the-kit's-hours] are dropped. Empty = no sky
   following."
  [world]
  (->> (:sky world)
       (keep (fn [e] (when (and (sequential? e) (= 2 (count e))
                                (string? (first e)) (re-matches hhmm-re (first e))
                                (contains? hours (second e)))
                       [(minute-of (first e)) (second e)])))
       (sort-by first)
       vec))

(defn hour-at
  "The kit hour for local `minute` (0-1439): the last entry at or before
   it, wrapping past midnight to the day's last entry. nil with no
   schedule."
  [sched minute]
  (when (seq sched)
    (second (or (last (filter #(<= (first %) minute) sched))
                (peek sched)))))

(defn local-minute
  "Minute of the local day for `now` (epoch ms) with `offset-min` minutes
   added to UTC (pm.closing's convention)."
  [now offset-min]
  (quot (mod (+ now (* (or offset-min 0) 60000)) 86400000) 60000))

(defn follow
  "One step of following: `last` is the hour this rule set last time (nil
   before the first). Returns {:set hour :immediate? bool} when the kit
   should change, else nil -- the first call sets it at once (no fade on
   load); later ones fade, and only when the schedule's hour changes."
  [last want]
  (when (and want (not= want last))
    {:set want :immediate? (nil? last)}))
