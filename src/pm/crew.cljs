(ns pm.crew
  "Pure rules for the Herdr crew-status dot and bridge: parsing
   public/state/crew.edn and public/state/bridge.edn, freshness, a dot per
   agent, label runs/text, HUD text, and the pulse. No js/Date, no fetch, no
   atoms — `now` is always an argument, in ms."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]))

(def statuses #{:idle :working :blocked :done :unknown})
(def stale-ms 15000)
(def pulse-ms 2000)
(def default-colors {:working 0xf2b134 :blocked 0xe5484d :done 0x46a758 :idle 0x9aa0a6 :unknown 0x9aa0a6})

(def ^:private prompt-id-re #"^[0-9a-f]{40}$")
(def ^:private prompt-kinds #{:bash :unknown})
(def ^:private token-re #"^[0-9a-f]{64}$")

(defn valid-prompt?
  "Also used by pm.talk to vet a saved prompt on restore."
  [p]
  (and (map? p)
       (string? (:id p)) (boolean (re-matches prompt-id-re (:id p)))
       (contains? prompt-kinds (:kind p))
       (string? (:text p)) (<= (count (:text p)) 400)
       (boolean? (:risky p))))

(defn- clean-titles [titles]
  (when (map? titles)
    (into {}
          (keep (fn [[k v]]
                  (when (and (keyword? k) (string? v) (not (str/blank? v)) (<= (count v) 64))
                    [k v])))
          titles)))

(defn parse [text]
  (when (string? text)
    (let [v (try (reader/read-string text) (catch :default _ nil))]
      (when (and (map? v)
                 (let [a (:as-of v)] (and (integer? a) (not (neg? a))))
                 (map? (:agents v)))
        (let [agents (into {}
                            (keep (fn [[k vv]]
                                    (when (and (keyword? k) (map? vv))
                                      (let [s (:status vv)
                                            s (if (contains? statuses s) s :unknown)
                                            p (:prompt vv)]
                                        [k (cond-> {:status s}
                                             (and (= s :blocked) (valid-prompt? p))
                                             (assoc :prompt (select-keys p [:id :kind :text :risky])))]))))
                            (:agents v))
              titles (clean-titles (:titles v))]
          (cond-> {:as-of (:as-of v) :agents agents}
            (map? (:titles v)) (assoc :titles titles)))))))

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

(defn parse-bridge [text]
  (when (string? text)
    (let [v (try (reader/read-string text) (catch :default _ nil))]
      (when (and (map? v)
                 (integer? (:port v)) (<= 1 (:port v) 65535)
                 (string? (:token v)) (re-matches token-re (:token v))
                 (integer? (:as-of v)) (not (neg? (:as-of v))))
        {:port (:port v) :token (:token v) :as-of (:as-of v)}))))

(defn bridge-on? [bridge now]
  (and (map? bridge) (<= (- now (:as-of bridge)) stale-ms)))

(defn bridge-url [bridge member]
  (str "http://127.0.0.1:" (:port bridge) "/crew/" (name member)))

(defn request-body [bridge action prompt-id text]
  (cond-> {"token" (:token bridge) "action" (name action)}
    (some? prompt-id) (assoc "prompt-id" prompt-id)
    (some? text) (assoc "text" text)))

(defn outcome [status reason]
  (cond
    (nil? status) {:result :error :reason "bridge off"}
    (<= 200 status 299) {:result :ok}
    (= status 409) {:result :stale :reason reason}
    :else {:result :error :reason (or reason "refused")}))
