(ns pm.crew
  "Pure rules for the Herdr crew-status dot and bridge: parsing
   state/crew.edn and state/bridge.edn, freshness, a dot per
   agent, label runs/text, HUD text, and the pulse. No js/Date, no fetch, no
   atoms — `now` is always an argument, in ms."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]))

(def statuses #{:idle :working :blocked :done :unknown :stalled})
(def stale-ms 15000)
;; An agent can sit "busy" (:working or :blocked) for a long time on its own merits, so
;; this has to be well past ordinary stale-ms before the dot calls it quiet (GDD §4.4,
;; §4.8, §3.1): "the bridge marks it stalled on its own when it stops reporting (for
;; example when its usage cap runs out), so nothing shows busy forever."
(def stall-ms 120000)
(def pulse-ms 2000)
(def default-colors {:working 0xf2b134 :blocked 0xe5484d :done 0x46a758 :idle 0x9aa0a6 :unknown 0x9aa0a6
                      ;; the ask_player "!" (GDD §3.1, §2.1): amber like :working, never red --
                      ;; red means :blocked, something gone wrong, not a real question.
                      :needs-you 0xf5a623
                      ;; a resting pose (GDD §4.4): neither busy's amber nor blocked's red.
                      :stalled 0x5e7a96})

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
       (boolean? (:risky p))
       (let [s (:since p)] (or (nil? s) (and (integer? s) (not (neg? s)))))))

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
                                            p (:prompt vv)
                                            since (:since vv)]
                                        [k (cond-> {:status s}
                                             (and (= s :blocked) (valid-prompt? p))
                                             (assoc :prompt (select-keys p [:id :kind :text :risky :since]))
                                             ;; when this agent last changed status (scripts/crew_status.clj),
                                             ;; so a long-unchanged "busy" status can be read as stalled below.
                                             (and (integer? since) (not (neg? since)))
                                             (assoc :since since))]))))
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

(defn stalled?
  "Whether an agent entry's own status has sat unchanged, busy (:working or
   :blocked), well past stall-ms -- the bridge's own sign that it stopped
   reporting (GDD §4.4, §3.1), not something Herdr ever says itself."
  [entry now]
  (and (map? entry)
       ;; working only: a blocked agent is waiting on the player, and keeps
       ;; its red "!" however long that takes
       (= :working (:status entry))
       (integer? (:since entry)) (not (neg? (:since entry)))
       (> (- now (:since entry)) stall-ms)))

(defn effective-status
  "The status to show for one crew agent: its ordinary status, unless it
   has gone quiet past stall-ms while busy, in which case :stalled -- a
   resting pose, not busy and not a red error."
  [entry now]
  (if (stalled? entry now) :stalled (:status entry)))

(defn dot [crew agent now palette]
  (if (or (not (fresh? crew now)) (not (contains? (:agents crew) agent)))
    {:glyph "○" :color (:idle palette) :pulse? false}
    (let [s (effective-status (get-in crew [:agents agent]) now)]
      {:glyph "●" :color (or (get palette s) (:unknown palette)) :pulse? (= s :blocked)})))

(defn pulse-alpha [t]
  (let [a (+ 0.4 (* 0.6 (+ 0.5 (* 0.5 (js/Math.cos (/ (* 2 js/Math.PI t) pulse-ms))))))]
    (/ (js/Math.round (* a 10)) 10)))

(defn ask-dot
  "The \"!\" over an agent with a pending ask_player question (GDD §3.1,
   §2.1): overrides its ordinary status dot, since a real question --
   raised only by the agent's explicit `ask_player` tool call -- always
   means \"!\", whatever Herdr thinks it's doing. nil when nothing's
   pending, so the caller falls back to the ordinary `dot`."
  [asked? palette]
  (when asked?
    {:glyph "!" :color (:needs-you palette) :pulse? true}))

(defn label-runs [d nm now]
  [{:text (str (:glyph d) " ") :color (:color d) :alpha (if (:pulse? d) (pulse-alpha now) 1)}
   {:text nm :color nil :alpha 1}])

(defn label-text [d nm]
  (str (:glyph d) " " nm))

(defn live-count [crew agents now]
  (if (fresh? crew now)
    (count (filter #(contains? (:agents crew) %) agents))
    0))

(defn plate-name [crew w]
  (let [t (get-in crew [:titles (:agent w)])]
    (if (and (string? t) (not (str/blank? t))) t (:name w))))

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

(defn hold-url [bridge]
  (str "http://127.0.0.1:" (:port bridge) "/hold"))

(defn events-ws-url
  "The bridge's events websocket (GDD §4.1, §4.7): same host and port as
   the HTTP bridge, its own pairing token riding in the query string since
   a websocket handshake carries no body."
  [bridge]
  (str "ws://127.0.0.1:" (:port bridge) "/events?token=" (:token bridge)))

(defn real-url [bridge]
  (str "http://127.0.0.1:" (:port bridge) "/real"))

(defn real-build-body [bridge id]
  {"token" (:token bridge) "action" "build" "id" (name id)})

(defn real-mirror-body
  "A REAL mirror's new state for the bridge to write to the twin's file
   (GDD §4.6): numbers and times only, never :source."
  [bridge mirror state]
  {"token" (:token bridge) "action" "mirror" "mirror" (name mirror)
   "state" (into {} (keep (fn [[k v]] (when (and (not= k :source) (or (nil? v) (number? v))) [(name k) v]))) state)})

(defn hold-body [bridge on?]
  {"token" (:token bridge) "action" "hold" "on" (boolean on?)})

(defn outcome [status reason]
  (cond
    (nil? status) {:result :error :reason "bridge off"}
    (<= 200 status 299) {:result :ok}
    (= status 409) {:result :stale :reason reason}
    :else {:result :error :reason (or reason "refused")}))
