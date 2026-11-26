(ns pm.today
  "The day's four goals (the final project's win loop, GDD 'win the day'):
   finish the tutorial, build something with your twin, do a creature's
   chore (click it in the world or tick it in 💭), jack out and back in. Pure: no atoms, no three.js, no DOM, no js/Date -- `now` (epoch ms)
   and `offset-min` (minutes east of UTC, `pm.core/tz-offset-min`, the same
   convention `pm.closing` uses) are always passed in. No person, pet or
   member is named anywhere in it."
  (:require [cljs.reader :as reader]
            [pm.content :as c]
            [pm.blueprint :as bp]))

(def goal-count 4)
(def margin 14)
(def card-w 300)
(def pill-h 40)
(def panel-top 72)
(def bottom-clear 250)
(def min-card-h 120)
(def narrow-vw 720)
(def fallback-win-line "Day won. What you built stays built.")   ;; TODO content

(defn- finite? [now]
  (and (number? now) (js/isFinite now)))

(defn- idiv
  "Truncating integer division (toward zero), as the civil-calendar algorithm
   below assumes."
  [a b]
  (js/Math.trunc (/ a b)))

(defn- civil-from-days
  "[year month day] for `z` days since the epoch (1970-01-01 = day 0).
   Howard Hinnant's days_from_civil, run in reverse; pure integer maths, so
   it works for a negative `z` too (no js/Date)."
  [z]
  (let [z   (+ z 719468)
        era (idiv (if (>= z 0) z (- z 146096)) 146097)
        doe (- z (* era 146097))
        yoe (idiv (- (+ (- doe (idiv doe 1460)) (idiv doe 36524)) (idiv doe 146096)) 365)
        y0  (+ yoe (* era 400))
        doy (- doe (- (+ (* 365 yoe) (idiv yoe 4)) (idiv yoe 100)))
        mp  (idiv (+ (* 5 doy) 2) 153)
        d   (+ (- doy (idiv (+ (* 153 mp) 2) 5)) 1)
        m   (+ mp (if (< mp 10) 3 -9))
        y   (+ y0 (if (<= m 2) 1 0))]
    [y m d]))

(defn- pad2 [n] (if (< n 10) (str "0" n) (str n)))

(defn day-of
  "\"YYYY-MM-DD\" for the local day of `now` shifted by `offset-min`
   minutes; nil for a bad `now`. A bad `offset-min` counts as 0."
  [now offset-min]
  (when (finite? now)
    (let [off  (if (number? offset-min) offset-min 0)
          days (js/Math.floor (/ (+ now (* off 60000)) 86400000))
          [y m d] (civil-from-days days)]
      (str y "-" (pad2 m) "-" (pad2 d)))))

(defn fresh [day tutorial?]
  {:day day :tutorial? (boolean tutorial?) :built? false :chore? false :out? false :back? false :won-at nil})

(defn won? [today]
  (boolean (and (:tutorial? today) (:built? today) (:chore? today) (:back? today))))

(defn- with-win [state now]
  (if (and (won? state) (nil? (:won-at state)))
    (assoc state :won-at now)
    state))

(defn current
  "`today` rolled forward to `now`'s local day: identical when it's still
   the same day, else a fresh day that keeps the tutorial goal (it's done
   once). `today` unchanged for a bad `now`."
  [today now offset-min]
  (if-not (finite? now)
    today
    (let [d (day-of now offset-min)]
      (if (= d (:day today))
        today
        (fresh d (:tutorial? today))))))

(defn tutorial-done [today now offset-min]
  (if-not (finite? now)
    today
    (-> (current today now offset-min)
        (assoc :tutorial? true)
        (with-win now))))

(defn build-real [today now offset-min opts]
  (if (or (not (finite? now)) (:practice? opts))
    today   ; nothing ticks while the Construct runs: not even the day rollover
    (let [s (current today now offset-min)]
      (-> (if (and (:tutorial? s) (not (:at-load? opts)))
            (assoc s :built? true)
            s)
          (with-win now)))))

(defn chore-done
  "A creature's chore done by the player (a click on the creature, or a
   tick in 💭); a click on a creature that isn't due counts too: they
   checked on it, and the twin has said when it's next due."
  [today now offset-min opts]
  (if (or (not (finite? now)) (:practice? opts))
    today
    (let [s (current today now offset-min)]
      (-> (if (:tutorial? s) (assoc s :chore? true) s)
          (with-win now)))))

(defn jacked-out [today now offset-min opts]
  (if (or (not (finite? now)) (:practice? opts))
    today   ; nothing ticks while the Construct runs: not even the day rollover
    (let [s (current today now offset-min)]
      (-> (if (:tutorial? s)
            (assoc s :out? true)
            s)
          (with-win now)))))

(defn jacked-in [today now offset-min opts]
  (if (or (not (finite? now)) (:practice? opts))
    today   ; nothing ticks while the Construct runs: not even the day rollover
    (let [s (current today now offset-min)]
      (-> (if (:out? s)
            (assoc s :back? true)
            s)
          (with-win now)))))

(defn just-won? [before after]
  (boolean (and (nil? (:won-at before)) (number? (:won-at after)))))

(defn done-count [today]
  (count (filter #(true? (get today %)) [:tutorial? :built? :chore? :back?])))

(defn rows
  "The card's three rows. `vars` fills `{word}` placeholders in the texts
   (`{twin}` = the twin's name, which is data, never code)."
  ([today texts] (rows today texts {}))
  ([today texts vars]
   (mapv (fn [[id k]]
           (let [ticked? (boolean (get today k))
                 f       #(when (string? %) (c/fill % vars))]
             {:id id
              :text (or (f (get-in texts [:rows id])) "")
              :hint (when-not ticked? (f (get-in texts [:hints id])))
              :ticked? ticked?}))
         [[:tutorial :tutorial?] [:build :built?] [:chore :chore?] [:jack :back?]])))

(defn- capitalize [s]
  (if (seq s) (str (.toUpperCase (subs s 0 1)) (subs s 1)) s))

(defn pet-vars
  "{:pet <name> :chore <its first chore, capitalised>} for the chore goal's
   text: the first creature on the HUD (`:hud :mirrors`, data) with a
   chore. Empty when there is none, so the texts keep their placeholders."
  [world]
  (or (first (for [id (get-in world [:hud :mirrors])
                   :let [m (get-in world [:mirrors id]) ch (first (:chores m))]
                   :when (and m ch)]
               {:pet (or (:name m) (name id))
                :chore (capitalize (name (:id ch)))}))
      {}))

(defn- pill-text [today texts]
  (let [template (if (string? (:pill texts)) (:pill texts) "{title} {done}/{total}")]
    (c/fill template {:title (or (:title texts) "") :done (done-count today) :total goal-count})))

(defn pill [today texts]
  (if (won? today)
    (or (:won texts) (pill-text today texts))
    (pill-text today texts)))

(defn card-open?
  "Whether the Today card is open: the player's last toggle (`true`/`false`),
   or, with no toggle yet, open until the day's won."
  [today choice]
  (if (boolean? choice)
    choice
    (not (won? today))))

(defn layout
  "Where the Today card sits: a card, or (below `narrow-vw`, with the 💭
   panel open, or without a usable viewport size) a collapsed pill."
  [{:keys [vw vh panel-open? open?]}]
  (if (and open? (not panel-open?) (number? vw) (number? vh) (>= vw narrow-vw))
    {:mode :card :left margin :top margin
     :width (min card-w (- vw 28)) :max-height (max min-card-h (- vh bottom-clear))}
    {:mode :pill :left margin :top margin :width nil :max-height nil}))

(defn win-line [table seed]
  (or (c/line table [:day-won] {} seed) fallback-win-line))

(defn win-card [texts line]
  {:title (:win-title texts) :line line :button (:button texts)})

(defn save-text [today]
  (pr-str today))

(defn restore [text]
  (when (string? text)
    (let [v (try (reader/read-string text) (catch :default _ nil))]
      (when (and (map? v) (string? (:day v)))
        v))))

(defn real-ids
  "The ids of `world`'s blueprints that are built: `:real`, or `:removing`
   (a removal preview still counts, so Keep it never looks like a new
   build)."
  [world]
  (set (keep (fn [bp] (when (contains? #{:real :removing} (:status bp)) (:id bp)))
             (bp/all world))))

(defn transitions
  "The events between two worlds, in order: `:build-real` for a newly real
   blueprint, then `:jacked-out`/`:jacked-in` for a change to `:jack`'s
   `:out?`. `[]` with no change; nil worlds are fine."
  [before after]
  (let [added      (seq (remove (real-ids before) (real-ids after)))
        out-before (boolean (:out? (:jack before)))
        out-after  (boolean (:out? (:jack after)))]
    (cond-> []
      added                            (conj :build-real)
      (and out-after (not out-before)) (conj :jacked-out)
      (and out-before (not out-after)) (conj :jacked-in))))
