(ns pm.fielder
  "Pure decision for bb crew-fielder, the twin's automatic half: given a
   poll of state/crew.edn, state/hold.edn and state/crew-log.edn, which
   members have a routine prompt due to be answered, and whether a live
   re-read still allows it. No I/O, no js/Date, no atoms — `now` is always
   an argument. Never required by pm.core."
  (:require [clojure.string :as str]
            [pm.bridge :as b]
            [pm.crew-log :as cl]
            #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as reader])))

(def default-answer-after-s 5)
(def every-ms 2000)
(def stale-ms 15000)

(defn answer-after-s [crew-talk]
  (let [n (:answer-after-s crew-talk)]
    (if (and (number? n) (<= 0 n 3600)) n default-answer-after-s)))

(defn verdict
  "One keyword describing what's true of member right now, in this order
   (the first match wins): :hold, :stale, :not-in-roster, :not-blocked,
   :unknown, :risky, :no-since, :answered, :too-soon, or :answer."
  [ctx member]
  (let [crew (:crew ctx)
        a    (get-in ctx [:crew :agents member])
        p    (:prompt a)
        now  (:now ctx)]
    (cond
      (cl/hold-on? (:hold ctx))
      :hold

      (or (not (map? crew))
          (not (number? (:as-of crew)))
          (> (- now (:as-of crew)) stale-ms))
      :stale

      (not (contains? (or (:members ctx) #{}) member))
      :not-in-roster

      (or (not= :blocked (:status a)) (not (map? p)))
      :not-blocked

      (not= :bash (:kind p))
      :unknown

      (not (false? (:risky p)))
      :risky

      (not (integer? (:since p)))
      :no-since

      (or (= (get (:tried ctx) member) {:id (:id p) :since (:since p)})
          (some (fn [x] (and (= (:member x) member)
                              (= (:prompt-id x) (:id p))
                              (integer? (:at x))
                              (>= (:at x) (:since p))))
                (or (:log ctx) [])))
      :answered

      (< (- now (:since p)) (* 1000 (answer-after-s (:crew-talk ctx))))
      :too-soon

      :else
      :answer)))

(defn due
  "Every member (in sorted order) whose verdict is :answer right now, as
   {:member m :prompt p}. [] when there are none, including a nil crew."
  [ctx]
  (let [agents (get-in ctx [:crew :agents])]
    (if-not (map? agents)
      []
      (vec (keep (fn [m]
                   (when (= :answer (verdict ctx m))
                     {:member m :prompt (:prompt (get agents m))}))
                 (sort (keys agents)))))))

(defn attempt
  "The bridge's own allow decision, reused: not blocked / the live prompt
   changed (409), unknown / risky (403), else the allow steps. Never
   declines."
  [prompt live]
  (b/decide {:action :allow :prompt-id (:id prompt) :text nil} live))

(defn log-entry
  "A crew-log.edn entry for the twin's allow, or nil when live has no
   prompt to log against."
  [now member live]
  (when (map? (:prompt live))
    (cl/entry now member :allowed :twin (:prompt live))))

(defn remember
  "tried, with member's latest-seen prompt recorded so it's answered once
   per sighting."
  [tried member prompt]
  (assoc (or tried {}) member {:id (:id prompt) :since (:since prompt)}))

;; -- forwarding a prompt the fielder leaves to the twin's chat --

(defn forwardable
  "Every member (sorted) the fielder leaves alone — verdict :unknown or
   :risky — whose prompt hasn't already been forwarded (by id) or answered
   in the log since it started, as {:member :prompt}. [] on a nil crew or
   while the hold is on (verdict already covers both)."
  [ctx forwarded]
  (let [agents (get-in ctx [:crew :agents])]
    (if-not (map? agents)
      []
      (vec (keep (fn [m]
                   (when (contains? #{:unknown :risky} (verdict ctx m))
                     (let [p (:prompt (get agents m))]
                       (when (and (not= (:id p) (get-in forwarded [:forwarded m :id]))
                                  (not (some (fn [x]
                                               (and (= m (:member x))
                                                    (= (:id p) (:prompt-id x))
                                                    (or (not (integer? (:since p)))
                                                        (>= (:at x) (:since p)))))
                                             (or (:log ctx) []))))
                         {:member m :prompt p}))))
                 (sort (keys agents)))))))

(defn forward-text
  "The twin's chat line for a forwarded prompt: title is the member's
   display name (pm.jumeaux's titles, e.g. \"Un Automate\"); reason is
   pm.prompt/risk-reason's result, or nil."
  [title member p reason]
  (str "[from the crew] " title " asks to run: " (:text p)
       (when reason (str " (risky: " reason ")"))
       " · answer with: bb crew-answer " (name member) " "
       (if (integer? (:always p)) "allow|always|deny" "allow|deny")
       " [--note \"…\"]"))

(defn record-forward
  "forwarded, with member's prompt id recorded against now so it isn't
   forwarded again until it changes."
  [forwarded member p now]
  (-> (or forwarded {})
      (assoc :as-of now)
      (assoc-in [:forwarded member] {:id (:id p) :at now})))

(def ^:private forwarded-id-re #"^[0-9a-f]{40}$")

(defn parse-forwarded
  "state/crew-forwarded.edn's content, read defensively: nil unless it's a
   map, with every entry's :id a 40-hex string and :at a non-negative
   integer."
  [text]
  (when (string? text)
    (let [v (try
              #?(:clj (edn/read-string text) :cljs (reader/read-string text))
              (catch #?(:clj Exception :cljs :default) _ nil))]
      (when (map? v)
        (let [as-of (:as-of v)
              as-of (if (and (integer? as-of) (not (neg? as-of))) as-of 0)
              fwd   (into {}
                          (keep (fn [[k e]]
                                  (when (and (keyword? k) (map? e)
                                             (string? (:id e)) (re-matches forwarded-id-re (:id e))
                                             (integer? (:at e)) (not (neg? (:at e))))
                                    [k (select-keys e [:id :at])])))
                          (or (:forwarded v) {}))]
          {:as-of as-of :forwarded fwd})))))

;; -- bb crew-answer --

(def ^:private answer-usage "usage: bb crew-answer <member> allow|always|deny [--note \"…\"]")
(def ^:private answer-member-re #"^[a-z][a-z0-9_-]{0,31}$")
(def ^:private note-control-re #"[\x00-\x1f\x7f]")

(defn answer-request
  "bb crew-answer's argv, parsed and checked against the crew and the
   forwarded record, as {:ok true :member :action :prompt-id :text} or
   {:ok false :reason}. action is a string (\"approve\"/\"always\"/\"deny\")
   so the caller can pass it straight into the bridge's request body."
  [argv crew forwarded]
  (let [argv   (vec argv)
        m      (nth argv 0 nil)
        choice (nth argv 1 nil)]
    (cond
      (< (count argv) 2) {:ok false :reason answer-usage}
      (not (and (string? m) (re-matches answer-member-re m))) {:ok false :reason "bad member"}
      (not (contains? #{"allow" "always" "deny"} choice)) {:ok false :reason answer-usage}

      :else
      (let [rst (subvec argv 2)]
        (cond
          (not (or (empty? rst) (and (= 2 (count rst)) (= "--note" (first rst)))))
          {:ok false :reason answer-usage}

          (and (seq rst) (not= choice "deny"))
          {:ok false :reason "--note goes with deny"}

          :else
          (let [note-raw (second rst)
                note     (when note-raw (str/trim note-raw))]
            (cond
              (and note-raw (str/blank? note)) {:ok false :reason "empty note"}
              (and note-raw (> (count note) 1000)) {:ok false :reason "note too long"}
              (and note-raw (re-find note-control-re note-raw)) {:ok false :reason "control characters in note"}

              :else
              (let [mk     (keyword m)
                    fwd-id (get-in forwarded [:forwarded mk :id])
                    id     (if (and (string? fwd-id) (re-matches #"^[0-9a-f]{40}$" fwd-id))
                             fwd-id
                             (get-in crew [:agents mk :prompt :id]))]
                (if (nil? id)
                  {:ok false :reason (str "no prompt from " m " to answer")}
                  {:ok true :member mk
                   :action (get {"allow" "approve" "always" "always" "deny" "deny"} choice)
                   :prompt-id id :text note})))))))))

(defn answer-body
  "The bridge POST body for a successful answer-request, from the twin."
  [req token]
  (cond-> {"token" token "action" (:action req) "prompt-id" (:prompt-id req) "by" "twin"}
    (some? (:text req)) (assoc "text" (:text req))))
