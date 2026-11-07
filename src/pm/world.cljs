(ns pm.world
  "Estate/world rules: pure data in, data out. No three.js, no js/Date, no
   atoms, no side effects — `now` is always passed in, in epoch ms."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [shadow.resource :as rc]
            [pm.content :as c]))

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
