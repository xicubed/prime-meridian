(ns pm.world
  "Estate/world rules: pure data in, data out. No three.js, no js/Date, no
   atoms, no side effects — `now` is always passed in, in epoch ms."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [shadow.resource :as rc]
            [pm.content :as c]
            [pm.events :as events]
            [pm.jumeaux :as j]))

(def estate-edn (rc/inline "estate.edn"))

(def ^:private unit->ms
  {"s" 1000 "m" 60000 "h" 3600000 "d" 86400000 "w" 604800000})

(defn parse-every [s]
  (when (string? s)
    (when-let [[_ n-str u] (re-matches #"^(\d+)([smhdw])$" s)]
      (* (js/parseInt n-str 10) (get unit->ms u)))))

(defn load-estate [text]
  (let [data    (reader/read-string text)
        mirrors (reduce (fn [acc m]
                           (if-let [id (:id m)]
                             (assoc acc id m)
                             acc))
                         {} (:mirrors data))]
    (assoc data :mirrors mirrors :log [])))

(defn- chore-def [world mirror-id chore-id]
  (let [mirror (get-in world [:mirrors mirror-id])]
    (first (filter #(= chore-id (:id %)) (:chores mirror)))))

(defn- last-done [world mirror-id chore]
  (if-let [done-at (:done-at chore)]
    (get-in world [:mirrors mirror-id :state done-at])
    (get-in world [:mirrors mirror-id :done (:id chore)])))

(defn due? [world mirror-id chore-id now]
  (if-let [chore (chore-def world mirror-id chore-id)]
    (let [last-at (last-done world mirror-id chore)]
      (if (nil? last-at)
        true
        (if-let [interval (parse-every (:every chore))]
          (>= (- now last-at) interval)
          false)))
    false))

(defn- record-done [world mirror-id chore at]
  (if-let [done-at (:done-at chore)]
    (assoc-in world [:mirrors mirror-id :state done-at] at)
    (assoc-in world [:mirrors mirror-id :done (:id chore)] at)))

(defn- consume [world mirror-id [field n]]
  (if (nil? field)
    world
    (update-in world [:mirrors mirror-id :state]
               (fn [state]
                 (let [v (get state field)]
                   (if (number? v)
                     (assoc state field (max 0 (- v n)))
                     state))))))

(defn- consumed-amount [state [field n]]
  (when field
    (let [v (get state field)]
      (when (number? v)
        [field (- v (max 0 (- v n)))]))))

(defn- log-event [world event result & kvs]
  (update world :log conj (apply assoc event :result result kvs)))

(defn event-id [event]
  (let [{:keys [mirror chore at]} event]
    (when (and (keyword? mirror) (keyword? chore) (number? at))
      (str (name mirror) "/" (name chore) "@" at))))

(defn last-done-at [world mirror-id chore-id]
  (when-let [chore (chore-def world mirror-id chore-id)]
    (last-done world mirror-id chore)))

(defn next-due [world mirror-id chore-id]
  (when-let [chore (chore-def world mirror-id chore-id)]
    (let [last (last-done world mirror-id chore)]
      (when (number? last)
        (when-let [interval (parse-every (:every chore))]
          (+ last interval))))))

(defn chore-done [world event]
  (let [{mirror :mirror chore-id :chore at :at} event
        chore (chore-def world mirror chore-id)]
    (cond
      (nil? (get-in world [:mirrors mirror]))
      (log-event world event :ignored :reason :unknown-mirror)

      (nil? chore)
      (log-event world event :ignored :reason :unknown-chore)

      (not (number? at))
      (log-event world event :ignored :reason :no-time)

      (not (due? world mirror (:id chore) at))
      (log-event world event :ignored :reason :not-due)

      :else
      (let [prev-at  (last-done world mirror chore)
            consumed (consumed-amount (get-in world [:mirrors mirror :state]) (:consumes chore))
            world'   (-> world
                         (consume mirror (:consumes chore))
                         (record-done mirror chore at))
            ;; The snap (GDD §2.2, §4.8): a chore marked :fx :snap in its
            ;; estate.edn entry leaves `:snap` as data on `world` -- when and
            ;; which mirror -- for `pm.scene/play-snap!` to animate, and for
            ;; `pm.plan/snap-pose` to time it. Pure data; no three.js here.
            world'   (if (= :snap (:fx chore))
                       (assoc world' :snap {:mirror mirror :at at})
                       world')
            entry    (cond-> (assoc event :result :counted
                                     :id (event-id event)
                                     :prev-at prev-at)
                       consumed (assoc :consumed consumed))]
        (update world' :log conj entry)))))

(defn undo-chore [world record now]
  (let [{:keys [id mirror chore at prev-at consumed]} record
        ev (fn [result & kvs]
             (apply assoc {:event :chore-undone :mirror mirror :chore chore :at now :id id}
                    :result result kvs))]
    (cond
      (nil? (get-in world [:mirrors mirror]))
      (update world :log conj (ev :ignored :reason :unknown-mirror))

      (nil? (chore-def world mirror chore))
      (update world :log conj (ev :ignored :reason :unknown-chore))

      (not= (last-done-at world mirror chore) at)
      (update world :log conj (ev :ignored :reason :not-current))

      :else
      (let [c       (chore-def world mirror chore)
            world'  (if-let [done-at (:done-at c)]
                      (assoc-in world [:mirrors mirror :state done-at] prev-at)
                      (if (nil? prev-at)
                        (update-in world [:mirrors mirror :done] dissoc chore)
                        (assoc-in world [:mirrors mirror :done chore] prev-at)))
            [cf cd] consumed
            world'' (if (and (number? cd) (number? (get-in world' [:mirrors mirror :state cf])))
                      (update-in world' [:mirrors mirror :state cf] + cd)
                      world')]
        (update world'' :log conj (ev :undone))))))

(defn redo-chore [world record now]
  (chore-done world {:event :chore-done :mirror (:mirror record) :chore (:chore record)
                      :at now :via :tick :who :me}))

(defn- cond-holds? [world mirror-id state cond now]
  (when (vector? cond)
    (let [[op field n] cond]
      (case op
        :due? (due? world mirror-id field now)
        (:< :<= := :>= :>)
        (let [v (get state field)]
          (and (number? v) (number? n)
               (case op
                 :<  (< v n)
                 :<= (<= v n)
                 :=  (= v n)
                 :>= (>= v n)
                 :>  (> v n))))
        false))))

(defn fill [s state]
  (c/fill s state))

(defn line-for
  "Text for a mirror's rule/idle value `x` at `now`: a string is filled from
   its state; a keyword looks up its line in `(:lines world)` (or the
   default table); anything else -> nil."
  [world mirror-id x now]
  (let [state (get-in world [:mirrors mirror-id :state])
        table (let [t (:lines world)] (if (map? t) t c/mirror-lines))]
    (cond
      (string? x)  (c/fill x state)
      (keyword? x) (c/line table [:mirrors mirror-id x] state
                           (+ (c/day-number now) (c/name-code mirror-id)))
      :else nil)))

(defn asks
  "The distinct chore ids, in rule order, of mirror-id's :due? rules that
   hold at now and whose :say resolves to a non-nil string. [] for an
   unknown mirror."
  [world mirror-id now]
  (if-let [mirror (get-in world [:mirrors mirror-id])]
    (->> (:rules mirror)
         (keep (fn [rule]
                 (let [w (:when rule) say (:say rule)]
                   (when (and (vector? w) (= :due? (first w)))
                     (let [c (second w)]
                       (when (and (due? world mirror-id c now)
                                  (some? (line-for world mirror-id say now)))
                         c))))))
         distinct
         vec)
    []))

(defn chore-past
  "The chore's :past when it's a non-blank string; otherwise \"done\"."
  [world mirror-id chore-id]
  (let [p (:past (chore-def world mirror-id chore-id))]
    (if (and (string? p) (not (str/blank? p))) p "done")))

(defn markers [world mirror-id now]
  (if-let [mirror (get-in world [:mirrors mirror-id])]
    (vec
      (keep (fn [rule]
              (when (cond-holds? world mirror-id (:state mirror) (:when rule) now)
                (let [picked   (select-keys rule [:say :sign])
                      resolved (into {}
                                     (keep (fn [[k v]]
                                             (when-let [t (line-for world mirror-id v now)]
                                               [k t]))
                                           picked))]
                  (when (seq resolved) resolved))))
            (:rules mirror)))
    []))

;; Jack out / jack in (GDD §2.3, §4.8): the phone booth rings, the estate
;; freezes gently with its reason shown, and coming back in resumes with
;; what changed. A `:jack-out` event (GDD §3.1) is one of the four bridge
;; events; `apply-event` is where any of the four would land on `world` --
;; today only `:jack-out` does anything here.

(defn- jack-out-line
  "The phone booth's sign while frozen: the household's own words when
   that's the reason (its template needs only the event's own :text),
   else the event's :text as given, else a plain fallback. Never nil."
  [event]
  (or (when (= :household (:reason event))
        (c/line c/hal-lines [:jack-out-household] {:text (:text event)} 0))
      (:text event)
      "The phone booth rings."))

(defmulti apply-event
  "Applies one bridge event (GDD §3.1, §4.8) to `world` at `now` (epoch ms).
   Dispatches on which of the four shapes it is (`pm.events/event-kind`:
   :agent-event and :answer key off :type, :chore-done and :jack-out off
   :event). A `:chore-done` from the bridge (a canal click calls
   `chore-done` directly instead, since it's local and already trusted)
   must carry a valid :source -- the bridge refuses events from unknown
   processes -- and its :at is `now`, the moment the event reached the
   game, not anything the sender claims. Anything unrecognized, or an
   event of a known shape that doesn't validate, leaves `world` unchanged
   but for a log entry."
  (fn [_world event _now] (events/event-kind event)))

(defmethod apply-event :default [world _event _now] world)

(defmethod apply-event :chore-done
  [world event now]
  (if (events/valid-chore-done? event)
    (chore-done world (assoc event :at now))
    (log-event world event :ignored :reason :invalid)))

(defmethod apply-event :jack-out
  [world event now]
  (if (events/valid-jack-out? event)
    (let [logged (log-event world event :jacked-out)]
      (assoc logged :jack {:out? true :reason (:reason event) :who (:who event)
                            :line (jack-out-line event) :at now
                            ;; the ringing itself doesn't count as "what changed";
                            ;; only log entries from here on do.
                            :log-n (count (:log logged))}))
    (log-event world event :ignored :reason :invalid)))

;; ask_player (GDD §3.1, §2.1, §4.8): an agent raises "!" only by calling
;; this explicit tool, carried over the bridge as an `:agent-event` of
;; :kind :question; the player's typed reply comes back as an `:answer`.
;; Both are kept in `:asks-player`, keyed by the agent's name string, so
;; the scene can show the marker on whichever character it names and
;; clear it once answered.

(def say-ms 12000)   ; how long an agent's :say bubble stays over its walker

(defn twin-walker
  "The estate walker that is the twin: the one named as the twin in
   `:jumeaux` (the twin is data, never a name in code); nil when none."
  [world]
  (let [twin-name (j/twin (j/names world))]
    (first (filter #(and (keyword? (:agent %)) (= twin-name (:name %))) (:walkers world)))))

(defn- twin-agent?
  "True when `agent` (an event's agent name string) is the twin walker's."
  [world agent]
  (= agent (some-> (twin-walker world) :agent name)))

(defn says-now
  "Agent name -> text for every :say still showing at `now`."
  [world now]
  (into {} (keep (fn [[agent {:keys [text since]}]]
                   (when (and (number? since) (< (- now since) say-ms)) [agent text])))
        (:says world)))

(defn twin-says
  "A :say event for the twin spoken by the game itself (no bridge behind it:
   the demo, or a dev build with the bridge off), ready for `apply-event`.
   nil without a twin walker or a blank text."
  [world text]
  (when-let [tw (twin-walker world)]
    (when (and (string? text) (not (str/blank? text)))
      {:type :agent-event :agent (name (:agent tw)) :kind :say :text text
       :source {:agent "game" :session "sample" :machine "local"}})))

(defn no-bridge-line
  "What the twin answers to a say when no bridge can carry it (:twin-talk)."
  [world]
  (get-in world [:twin-talk :no-bridge]))

(defn sample-say
  "The n-th SAMPLE line of the twin's (:twin-talk :sample-says), rotating;
   nil when the estate declares none."
  [world n]
  (let [v (get-in world [:twin-talk :sample-says])]
    (when (and (sequential? v) (seq v) (integer? n))
      (nth v (mod n (count v))))))

(defmethod apply-event :agent-event
  [world event now]
  (if-not (events/valid-agent-event? event)
    (log-event world event :ignored :reason :invalid)
    (case (:kind event)
      :question (-> world
                    (assoc-in [:asks-player (:agent event)] {:text (:text event) :since now})
                    (log-event event :asked))
      ;; a :say: the bubble over the walker; the twin's line also goes to
      ;; the caption (and so the 💭 history and voice), like his other lines
      :say      (-> world
                    (assoc-in [:says (:agent event)] {:text (:text event) :since now})
                    (cond-> (twin-agent? world (:agent event))
                      (assoc :hal {:event :twin-say :text (:text event) :until (+ now c/hal-ms)}))
                    (log-event event :said)))))

(defmethod apply-event :answer
  [world event now]
  (if (events/valid-answer? event)
    (-> world
        (update :asks-player dissoc (:agent event))
        (log-event event :answered))
    (log-event world event :ignored :reason :invalid)))

(defn needs-you?
  "True when `agent` (a string, matching an agent-event's :agent) has a
   pending ask_player question (GDD §3.1) waiting on the player -- the
   \"!\" shown over its character."
  [world agent]
  (boolean (get-in world [:asks-player agent])))

(defn jacked-out?
  "True while the estate is frozen by a jack-out (GDD §2.3)."
  [world]
  (boolean (:out? (:jack world))))

(defn jack-out-text
  "The reason shown on the phone booth's sign while frozen; nil otherwise."
  [world]
  (when (jacked-out? world) (:line (:jack world))))

(defn jack-in
  "Coming back in (GDD §2.3): unfreezes the estate and shows what changed --
   how many log entries landed while you were out, or a plain welcome back
   when nothing did. No-op (returns `world` unchanged) when it wasn't
   jacked out."
  [world now]
  (let [j (:jack world)]
    (if (:out? j)
      (let [changed (max 0 (- (count (:log world)) (or (:log-n j) 0)))
            text    (if (pos? changed)
                      (str changed (if (= 1 changed) " thing" " things") " changed while you were out.")
                      (c/line c/hal-lines [:jack-in] {} (c/day-number now)))]
        (-> world
            (assoc :jack {:out? false :at now})
            (assoc :hal {:event :jack-in :text (or text "Welcome back.") :until (+ now c/hal-ms)})))
      world)))

(defn status-line [world mirror-id now]
  (when-let [mirror (get-in world [:mirrors mirror-id])]
    (let [name-part (or (:name mirror) (name mirror-id))
          state     (:state mirror)
          chore     (first (filter :consumes (:chores mirror)))
          field     (first (:consumes chore))
          value     (when field (get state field))
          counter   (when (some? value) (str (name field) " " value))
          marks     (mapcat (fn [m] (keep identity [(:say m) (:sign m)]))
                             (markers world mirror-id now))]
      (str/join " · " (remove nil? (concat [name-part counter] marks))))))
