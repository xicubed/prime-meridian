(ns pm.events
  "The four events that carry the bridge in both directions (GDD §3.1,
   §4.8, §4.2): agent-event, answer, chore-done, jack-out, written down as
   data and validated, plus `event->json`/`json->event` for carrying them
   as JSON over the bridge's websocket (GDD §4.1, §4.7). Portable (bb +
   cljs); pure: no atoms, no js/Date.

   Every event must carry its :source (GDD §3.1: 'every event carries its
   source: agent, session, machine'); a malformed event, or one with no
   :source at all, is refused by `valid-event?`."
  (:require [clojure.string :as str]
            #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as reader])))

;; The agent-event :kinds the game knows about: an explicit `ask_player`
;; call, which is what puts a "!" over someone (GDD §3.1), and a `:say`,
;; a line the agent speaks on the estate (a bubble over its walker; the
;; twin's also in the caption and the 💭 history), no answer expected; and an
;; `:errand`, a :say the agent walks somewhere first to deliver (:where names
;; the place: another agent, "mailbox", "phone-booth", "lobby", or a mirror).
(def agent-event-kinds #{:question :say :errand})

(def ^:private source-fields #{:agent :session :machine})
(def ^:private agent-re #"^[a-z][a-z0-9_-]{0,31}$")

(defn- non-blank-str? [v]
  (and (string? v) (not (str/blank? v))))

(defn- agent-name? [v]
  (and (string? v) (boolean (re-matches agent-re v))))

(defn valid-source?
  "A :source names at least one of :agent/:session/:machine, every value a
   non-blank string, no other keys -- so `nil` and `{}` both refuse."
  [s]
  (boolean
    (and (map? s)
         (seq s)
         (every? source-fields (keys s))
         (every? non-blank-str? (vals s)))))

(defn valid-agent-event? [e]
  (and (map? e)
       (= :agent-event (:type e))
       (agent-name? (:agent e))
       (contains? agent-event-kinds (:kind e))
       (non-blank-str? (:text e))
       (if (= :errand (:kind e)) (non-blank-str? (:where e)) (nil? (:where e)))
       (valid-source? (:source e))))

(defn valid-answer? [e]
  (and (map? e)
       (= :answer (:type e))
       (agent-name? (:agent e))
       (non-blank-str? (:text e))
       (valid-source? (:source e))))

(defn valid-chore-done? [e]
  (and (map? e)
       (= :chore-done (:event e))
       (keyword? (:mirror e))
       (keyword? (:chore e))
       (keyword? (:via e))
       (valid-source? (:source e))))

(defn valid-jack-out? [e]
  (and (map? e)
       (= :jack-out (:event e))
       (keyword? (:reason e))
       (keyword? (:who e))
       (or (nil? (:text e)) (non-blank-str? (:text e)))
       (valid-source? (:source e))))

(defn event-kind
  "Which of the four this looks like, from its :type or :event key, before
   the rest of it is checked; nil when it's neither shape."
  [e]
  (when (map? e)
    (cond
      (= :agent-event (:type e)) :agent-event
      (= :answer (:type e))      :answer
      (= :chore-done (:event e)) :chore-done
      (= :jack-out (:event e))   :jack-out
      :else nil)))

(defn valid-event?
  "True only for a well-formed agent-event, answer, chore-done or jack-out,
   each carrying a valid :source. Anything else -- the wrong shape, a
   missing field, or no :source -- is refused."
  [e]
  (boolean
    (case (event-kind e)
      :agent-event (valid-agent-event? e)
      :answer      (valid-answer? e)
      :chore-done  (valid-chore-done? e)
      :jack-out    (valid-jack-out? e)
      nil          false)))

(defn- read-edn [text]
  (try
    #?(:clj (edn/read-string text) :cljs (reader/read-string text))
    (catch #?(:clj Exception :cljs :default) _ nil)))

(defn parse
  "`text` read as EDN, kept only when it's a vector; else nil. Never throws."
  [text]
  (when (string? text)
    (let [v (read-edn text)]
      (when (vector? v) v))))

(defn valid-events
  "Just the well-formed events of `events` (a vector, possibly with
   malformed entries mixed in)."
  [events]
  (filterv valid-event? (filter map? (or events []))))

(defn- source->json [s]
  (into {} (map (fn [[k v]] [(name k) v])) s))

(defn- json->source [m]
  (when (map? m)
    (into {} (keep (fn [[k v]] (when (and (string? k) (string? v)) [(keyword k) v]))) m)))

(defn event->json
  "A valid event (one of the four shapes, GDD §3.1) as a plain
   string-keyed map, ready for a JSON encoder (cheshire on the bb side,
   js/JSON.stringify on the game side); nil for anything that doesn't
   validate, so a malformed event is never sent over the wire."
  [e]
  (when (valid-event? e)
    (case (event-kind e)
      :agent-event (cond-> {"type" "agent-event" "agent" (:agent e) "kind" (name (:kind e))
                            "text" (:text e) "source" (source->json (:source e))}
                     (:where e) (assoc "where" (:where e)))
      :answer      {"type" "answer" "agent" (:agent e) "text" (:text e)
                    "source" (source->json (:source e))}
      :chore-done  {"event" "chore-done" "mirror" (name (:mirror e)) "chore" (name (:chore e))
                    "via" (name (:via e)) "source" (source->json (:source e))}
      :jack-out    (cond-> {"event" "jack-out" "reason" (name (:reason e)) "who" (name (:who e))
                            "source" (source->json (:source e))}
                     (some? (:text e)) (assoc "text" (:text e))))))

(defn json->event
  "The reverse of `event->json`: a plain string-keyed map, as parsed from
   JSON text (cheshire's `parse-string` or `js->clj` over `JSON.parse`,
   neither keywordizing), back into one of the four event shapes; nil for
   anything that isn't a map or doesn't name one of the four. The result
   still goes through `valid-event?` before anything trusts it -- this
   function alone never lets a bad event through."
  [m]
  (when (map? m)
    (let [ty (get m "type") ev (get m "event")
          kw (fn [v] (when (string? v) (keyword v)))]
      (cond
        (= ty "agent-event") (cond-> {:type :agent-event :agent (get m "agent") :kind (kw (get m "kind"))
                                      :text (get m "text") :source (json->source (get m "source"))}
                               (contains? m "where") (assoc :where (get m "where")))
        (= ty "answer")      {:type :answer :agent (get m "agent") :text (get m "text")
                              :source (json->source (get m "source"))}
        (= ev "chore-done")  {:event :chore-done :mirror (kw (get m "mirror")) :chore (kw (get m "chore"))
                              :via (kw (get m "via")) :source (json->source (get m "source"))}
        (= ev "jack-out")    (cond-> {:event :jack-out :reason (kw (get m "reason")) :who (kw (get m "who"))
                                      :source (json->source (get m "source"))}
                               (some? (get m "text")) (assoc :text (get m "text")))
        :else nil))))

(defn hud-segment
  "A short count for the HUD, e.g. \"events 4/5 ok\"; nil when there's
   nothing to show."
  [events]
  (when (seq events)
    (str "events " (count (valid-events events)) "/" (count events) " ok")))
