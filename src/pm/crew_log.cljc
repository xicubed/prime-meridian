(ns pm.crew-log
  "Pure rules for state/crew-log.edn (who answered a crew prompt, and when)
   and state/hold.edn (the 'I'll answer' switch). Portable (bb + cljs); may
   be required by pm.core."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as reader])))

(def max-entries 200)
(def max-text 400)
(def actions #{:allowed :declined})
(def bys #{:twin :player})
(def legacy-bys {:hal :twin :michael :player})

(def ^:private member-re #"^[a-z][a-z0-9_-]{0,31}$")
(def ^:private prompt-id-re #"^[0-9a-f]{40}$")

(defn- read-edn [text]
  (try
    #?(:clj (edn/read-string text) :cljs (reader/read-string text))
    (catch #?(:clj Exception :cljs :default) _ nil)))

(defn role
  "The new :by key for by: itself when already :twin/:player, the
   upgraded key for a legacy :hal/:michael, else nil."
  [by]
  (cond
    (contains? bys by) by
    (contains? legacy-bys by) (get legacy-bys by)
    :else nil))

(defn upgrade-entry
  "e with a legacy :by key upgraded to :twin/:player. Anything else about
   e, and e itself when it isn't a map, is returned unchanged."
  [e]
  (if (map? e)
    (let [r (role (:by e))]
      (if (some? r) (assoc e :by r) e))
    e))

(defn valid-entry? [e]
  (and (map? e)
       (integer? (:at e)) (not (neg? (:at e)))
       (keyword? (:member e)) (boolean (re-matches member-re (name (:member e))))
       (contains? actions (:action e))
       (contains? bys (:by e))
       (string? (:prompt-id e)) (boolean (re-matches prompt-id-re (:prompt-id e)))
       (string? (:text e)) (not (str/blank? (:text e))) (<= (count (:text e)) max-text)))

(defn parse [text]
  (when (string? text)
    (let [v (read-edn text)]
      (when (vector? v)
        (->> v
             (map upgrade-entry)
             (filter valid-entry?)
             (mapv #(select-keys % [:at :member :action :by :prompt-id :text]))
             (take-last max-entries)
             vec)))))

(defn entry [now member action by prompt]
  {:at now :member member :action action :by by :prompt-id (:id prompt) :text (:text prompt)})

(defn append [log e]
  (let [base (vec (filter valid-entry? (map upgrade-entry (or log []))))
        e    (upgrade-entry e)]
    (if-not (valid-entry? e)
      base
      (let [last-at (:at (peek base))
            e'      (if (and (seq base) (<= (:at e) last-at))
                      (assoc e :at (inc last-at))
                      e)]
        (vec (take-last max-entries (conj base e')))))))

(defn for-member [log member n]
  (if (or (nil? n) (<= n 0))
    []
    (vec (take-last n (filterv #(= member (:member %)) (or log []))))))

(defn hold-edn [on now]
  {:on (boolean on) :at now})

(defn parse-hold [text]
  (when (string? text)
    (let [v (read-edn text)]
      (when (and (map? v) (boolean? (:on v)) (integer? (:at v)) (not (neg? (:at v))))
        {:on (:on v) :at (:at v)}))))

(defn hold-on? [hold]
  (true? (:on hold)))
