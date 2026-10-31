(ns pm.world
  "Estate/world rules: pure data in, data out. No three.js, no js/Date, no
   atoms, no side effects — `now` is always passed in, in epoch ms."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [shadow.resource :as rc]))

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

(defn- log-event [world event result & kvs]
  (update world :log conj (apply assoc event :result result kvs)))

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
      (log-event (-> world
                      (consume mirror (:consumes chore))
                      (record-done mirror chore at))
                  event :counted))))

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

(defn- fill [s state]
  (when s
    (str/replace s #"\{([a-z0-9-]+)\}"
                 (fn [[whole field-name]]
                   (let [v (get state (keyword field-name))]
                     (if (nil? v) whole (str v)))))))

(defn markers [world mirror-id now]
  (if-let [mirror (get-in world [:mirrors mirror-id])]
    (let [state (:state mirror)]
      (vec
        (keep (fn [rule]
                (when (cond-holds? world mirror-id state (:when rule) now)
                  (let [picked (select-keys rule [:say :sign])]
                    (when (seq picked)
                      (into {} (map (fn [[k v]] [k (fill v state)]) picked))))))
              (:rules mirror))))
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

(defn key-event [world key now]
  (when key
    (when-let [[m c] (get-in world [:hud :keys (str/lower-case key)])]
      {:event :chore-done :mirror m :chore c :at now :via :key :who :me})))
