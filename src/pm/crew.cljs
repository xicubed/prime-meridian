(ns pm.crew
  "Pure rules for the Herdr crew-status dot: parsing public/state/crew.edn,
   freshness, a dot per agent, label runs/text, HUD text, and the pulse. No
   js/Date, no fetch, no atoms — `now` is always an argument, in ms."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]))

(def statuses #{:idle :working :blocked :done :unknown})
(def stale-ms 15000)
(def pulse-ms 2000)
(def default-colors {:working 0xf2b134 :blocked 0xe5484d :done 0x46a758 :idle 0x9aa0a6 :unknown 0x9aa0a6})

(defn parse [text]
  (when (string? text)
    (let [v (try (reader/read-string text) (catch :default _ nil))]
      (when (and (map? v)
                 (let [a (:as-of v)] (and (integer? a) (not (neg? a))))
                 (map? (:agents v)))
        (let [agents (into {}
                            (keep (fn [[k vv]]
                                    (when (and (keyword? k) (map? vv))
                                      [k {:status (let [s (:status vv)]
                                                    (if (contains? statuses s) s :unknown))}])))
                            (:agents v))]
          {:as-of (:as-of v) :agents agents})))))

(defn fresh? [crew now]
  (and (map? crew) (<= (- now (:as-of crew)) stale-ms)))

(defn colors [world]
  (let [overrides (get-in world [:motion :status-dots])
        overrides (if (map? overrides) overrides {})]
    (into {}
          (map (fn [[k default]]
                 (let [v (get overrides k)]
                   [k (if (and (integer? v) (<= 0 v) (<= v 0xffffff)) v default)])))
          default-colors)))

(defn dot [crew agent now palette]
  (if (or (not (fresh? crew now)) (not (contains? (:agents crew) agent)))
    {:glyph "○" :color (:idle palette) :pulse? false}
    (let [s (get-in crew [:agents agent :status])]
      {:glyph "●" :color (or (get palette s) (:unknown palette)) :pulse? (= s :blocked)})))

(defn pulse-alpha [t]
  (let [a (+ 0.4 (* 0.6 (+ 0.5 (* 0.5 (js/Math.cos (/ (* 2 js/Math.PI t) pulse-ms))))))]
    (/ (js/Math.round (* a 10)) 10)))

(defn label-runs [d nm now]
  [{:text (str (:glyph d) " ") :color (:color d) :alpha (if (:pulse? d) (pulse-alpha now) 1)}
   {:text nm :color nil :alpha 1}])

(defn label-text [d nm]
  (str (:glyph d) " " nm))

(defn live-count [crew agents now]
  (if (fresh? crew now)
    (count (filter #(contains? (:agents crew) %) agents))
    0))

(defn hud-text [crew agents now]
  (cond
    (empty? agents)          nil
    (nil? crew)               "crew: no status"
    (not (fresh? crew now))   "crew: stale"
    :else (str "crew: " (live-count crew agents now) "/" (count agents) " live")))
