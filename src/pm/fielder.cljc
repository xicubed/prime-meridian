(ns pm.fielder
  "Pure decision for bb crew-fielder, the twin's automatic half: given a
   poll of state/crew.edn, state/hold.edn and state/crew-log.edn, which
   members have a routine prompt due to be answered, and whether a live
   re-read still allows it. No I/O, no js/Date, no atoms — `now` is always
   an argument. Never required by pm.core."
  (:require [pm.bridge :as b]
            [pm.crew-log :as cl]))

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
