(ns pm.talk
  "The talk log: what Hal, the creatures and the crew said, captioned and
   spoken. Pure: no atoms, no js/Date, no DOM. Never names a mirror."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [pm.world :as w]
            [pm.crew :as crew]
            [pm.crew-log :as cl]))

(def hal-name "Hal")
(def crew-kinds #{:crew-prompt :crew-status :crew-say :crew-log})
(def prompt-states #{:pending :sending :allowed :declined :stale})
(def say-states #{:sending :sent :failed})
(def you "You")
(def cap 100)
(def default-surface-after-s 60)
(def roster-log-n 3)
(def caption-min-ms 2500)
(def caption-max-ms 9000)
(def caption-base-ms 1200)
(def caption-ms-per-char 60)

(def default-voice {:rate 1 :pitch 1 :lang "en-US"})

(def quiet-ms
  "A reload is not a new visit: within this window Hal's greeting isn't
   logged again, and a speaker repeating its last line isn't logged twice."
  (* 30 60 1000))

(def once-per-visit
  "Hal events said once per visit (see quiet-ms)."
  #{:jack-in})

(def empty-talk
  {:entries [] :n 0 :seen 0 :open? false :voice? false :queue-end 0
   :last-hal nil :last-say {} :crew-last {} :log-at 0})

(defn caption-ms [text]
  (let [len (if (string? text) (count text) 0)]
    (-> (+ caption-base-ms (* caption-ms-per-char len))
        (max caption-min-ms)
        (min caption-max-ms))))

(defn say-text [world mirror-id now]
  (str/join " · " (keep :say (w/markers world mirror-id now))))

(defn- finite-number? [n]
  (and (number? n) (js/isFinite n)))

(defn- non-blank-str? [s]
  (and (string? s) (not (str/blank? s))))

(defn- valid-prev-at? [v]
  (or (nil? v) (finite-number? v)))

(defn- valid-consumed? [v]
  (or (nil? v)
      (and (vector? v) (= 2 (count v)) (keyword? (first v)) (finite-number? (second v)))))

(defn chore-record [entry]
  (when (map? entry)
    (let [r (select-keys entry [:id :mirror :chore :at :prev-at :consumed])]
      (when (and (non-blank-str? (:id r))
                 (keyword? (:mirror r))
                 (keyword? (:chore r))
                 (finite-number? (:at r))
                 (valid-prev-at? (:prev-at r))
                 (valid-consumed? (:consumed r)))
        r))))

(defn box?
  "True for a request (has a keyword :asks) or a click-chores chore line."
  [e]
  (boolean (and (map? e) (or (keyword? (:asks e)) (= :chore (:kind e))))))

(defn box-chore
  "The chore the box is for: (:asks e) for a request, (:chore (:chore e)) for
   a chore line. Nil for a non-box."
  [e]
  (when (map? e)
    (cond
      (keyword? (:asks e)) (:asks e)
      (= :chore (:kind e)) (:chore (:chore e))
      :else nil)))

(defn append [talk {:keys [who text kind mirror event asks]} now]
  (let [talk (or talk empty-talk)]
    (if-not (and (non-blank-str? who) (non-blank-str? text) (finite-number? now))
      talk
      (let [{:keys [n queue-end open?]} talk
            n'      (inc n)
            start   (max now queue-end)
            end     (+ start (caption-ms text))
            k       (if (keyword? kind) kind :other)
            entry   (cond-> {:n n' :who who :text text :at now :kind k
                              :show-at start :show-until end}
                      (keyword? mirror) (assoc :mirror mirror)
                      (keyword? event)  (assoc :event event)
                      (keyword? asks)   (assoc :asks asks :done? false))
            entries (vec (take-last cap (conj (:entries talk) entry)))]
        (cond-> (assoc talk :entries entries :n n' :queue-end end)
          open? (assoc :seen n'))))))

(defn add-chore
  "Append a checkbox entry for a counted chore. No show keys (never
   captioned), and :queue-end is untouched."
  [talk log-entry who now]
  (let [talk (or talk empty-talk)
        r    (chore-record log-entry)]
    (if-not (and r (non-blank-str? who) (finite-number? now))
      talk
      (let [{:keys [n open?]} talk
            n'      (inc n)
            entry   {:n n' :who who :text (str who "'s " (name (:chore r))) :at now :kind :chore
                      :mirror (:mirror r) :chore r :done? true}
            entries (vec (take-last cap (conj (:entries talk) entry)))]
        (cond-> (assoc talk :entries entries :n n')
          open? (assoc :seen n'))))))

(defn- record-less-open?
  "The only kind of box that can lose its box: a request with no answer yet."
  [e]
  (and (keyword? (:asks e)) (not (true? (:done? e))) (nil? (:chore e))))

(defn open-box
  "The latest open box for [m c]: the latest entry with mirror m, box chore
   c, and :done? not true. Else nil."
  [talk m c]
  (let [talk (or talk empty-talk)]
    (last (filter (fn [e] (and (box? e) (= m (:mirror e)) (= c (box-chore e))
                               (not (true? (:done? e)))))
                  (:entries talk)))))

(defn- strip-unasked [entries id asks]
  (let [asks-set (set asks)]
    (mapv (fn [e]
            (if (and (record-less-open? e) (= id (:mirror e)) (not (contains? asks-set (:asks e))))
              (dissoc e :asks :done?)
              e))
          entries)))

(defn- supersede [entries id c]
  (mapv (fn [e]
          (if (and (record-less-open? e) (= id (:mirror e)) (= c (:asks e)))
            (dissoc e :asks :done?)
            e))
        entries))

(defn- latest-mirror-entry [entries id]
  (last (filter #(and (= id (:mirror %)) (not= :chore (:kind %))) entries)))

(defn- adopt
  "Old histories: a standing line that matches today's request text, and has
   no box yet, becomes the request."
  [talk id s c]
  (let [latest (latest-mirror-entry (:entries talk) id)]
    (if (and latest (= s (:text latest)) (nil? (:asks latest)))
      (update talk :entries
              (fn [entries]
                (mapv (fn [e] (if (= (:n latest) (:n e)) (assoc e :asks c :done? false) e))
                      entries)))
      talk)))

(defn observe [talk world now]
  (let [talk (or talk empty-talk)
        t1   (let [h     (:hal world)
                    text  (:text h)
                    until (:until h)]
               (if (and (string? text) (number? until) (number? now) (< now until)
                        (not= [text until] (:last-hal talk)))
                 (let [ev      (:event h)
                       recent? (some (fn [e] (and (= :hal (:kind e))
                                                  (number? (:at e)) (< (- now (:at e)) quiet-ms)
                                                  (contains? once-per-visit ev)
                                                  (or (= ev (:event e))
                                                      ;; history saved before entries carried :event
                                                      (and (nil? (:event e)) (= text (:text e))))))
                                     (:entries talk))]
                   (cond-> (assoc talk :last-hal [text until])
                     (not recent?) (append {:who hal-name :text text :kind :hal :event ev} now)))
                 talk))]
    (reduce
      (fn [t id]
        (let [s    (say-text world id now)
              prev (get-in t [:last-say id])
              asks (w/asks world id now)
              c    (first asks)]
          (if (not= s prev)
            (let [t' (-> t
                         (assoc-in [:last-say id] s)
                         (update :entries strip-unasked id asks))]
              (cond
                (str/blank? s) t'
                (some? c)
                (let [m (get-in world [:mirrors id])]
                  (-> t'
                      (update :entries supersede id c)
                      (append {:who    (or (:name m) (name id))
                               :text   s
                               :kind   (if (keyword? (:kind m)) (:kind m) :creature)
                               :mirror id
                               :asks   c}
                              now)))
                :else
                (let [m (get-in world [:mirrors id])]
                  (append t' {:who    (or (:name m) (name id))
                              :text   s
                              :kind   (if (keyword? (:kind m)) (:kind m) :creature)
                              :mirror id}
                             now))))
            (if (and (non-blank-str? s) (some? c) (nil? (open-box t id c)))
              (adopt t id s c)
              t))))
      t1
      (sort (keys (:mirrors world))))))

(defn caption [talk now]
  (when (number? now)
    (let [talk (or talk empty-talk)]
      (last (filter (fn [e]
                      (let [sa (:show-at e) su (:show-until e)]
                        (and (number? sa) (number? su) (<= sa now) (< now su))))
                    (:entries talk))))))

(defn caption-text [entry]
  (when entry
    (str (:who entry) " — " (:text entry))))

(defn entry [talk n]
  (first (filter #(= n (:n %)) (:entries (or talk empty-talk)))))

(defn set-done [talk n done? record]
  (let [talk (or talk empty-talk)
        idx  (first (keep-indexed (fn [i e] (when (and (= n (:n e)) (box? e)) i))
                                   (:entries talk)))]
    (if (nil? idx)
      talk
      (update-in talk [:entries idx]
                 (fn [e] (cond-> (assoc e :done? done?)
                           (map? record) (assoc :chore record)))))))

(defn link-chore
  "A counted chore ticks the latest open box for [mirror chore], or, when
   nothing asked for it, gets its own line."
  [talk log-entry who now]
  (let [r (chore-record log-entry)]
    (if (nil? r)
      (or talk empty-talk)
      (let [talk (or talk empty-talk)]
        (if-let [e (open-box talk (:mirror r) (:chore r))]
          (set-done talk (:n e) true r)
          (add-chore talk log-entry who now))))))

(defn unread [talk]
  (let [talk (or talk empty-talk)]
    (if (:open? talk)
      0
      (count (filter #(and (> (:n %) (:seen talk)) (not= :crew-log (:kind %))) (:entries talk))))))

(defn badge-text [talk]
  (let [u (unread talk)]
    (cond
      (zero? u) ""
      (> u 99)  "99+"
      :else     (str u))))

(defn open-panel [talk]
  (let [talk (or talk empty-talk)]
    (assoc talk :open? true :seen (:n talk))))

(defn close-panel [talk]
  (let [talk (or talk empty-talk)]
    (assoc talk :open? false :seen (:n talk))))

(defn toggle-panel [talk]
  (let [talk (or talk empty-talk)]
    (if (:open? talk) (close-panel talk) (open-panel talk))))

(defn toggle-voice [talk]
  (update (or talk empty-talk) :voice? not))

(defn close-key? [key open? tag]
  (boolean (and (= key "Escape") open?
                (not (contains? #{"INPUT" "TEXTAREA" "SELECT"} tag)))))

(defn clock-text [at offset-min]
  (if-not (finite-number? at)
    ""
    (let [off  (if (number? offset-min) offset-min 0)
          ms   (mod (+ at (* off 60000)) 86400000)
          h    (js/Math.floor (/ ms 3600000))
          m    (js/Math.floor (/ (mod ms 3600000) 60000))
          h12  (let [r (mod h 12)] (if (zero? r) 12 r))
          mm   (if (< m 10) (str "0" m) (str m))
          ampm (if (< h 12) "AM" "PM")]
      (str h12 ":" mm " " ampm))))

(defn chore-label [entry offset-min]
  (when entry
    (str (:text entry) " · " (clock-text (:at (:chore entry)) offset-min))))

(defn box-label
  "The checkbox label for any box entry: a chore line keeps chore-label; a
   request shows who and what was said, plus the past tense and time once
   answered. Nil for a non-box."
  [entry offset-min past]
  (cond
    (= :chore (:kind entry)) (chore-label entry offset-min)
    (keyword? (:asks entry))
    (let [base (str (:who entry) " · " (:text entry))]
      (if (true? (:done? entry))
        (str base " · " past " " (clock-text (:at (:chore entry)) offset-min))
        base))
    :else nil))

(defn- append-crew
  "Append a crew-line entry. No show keys (never captioned), and
   :queue-end is untouched."
  [talk crew-entry]
  (let [{:keys [n open?]} talk
        n'      (inc n)
        entry'  (assoc crew-entry :n n')
        entries (vec (take-last cap (conj (:entries talk) entry')))]
    (cond-> (assoc talk :entries entries :n n')
      open? (assoc :seen n'))))

(defn- crew-prompt-text [p]
  (if (= :bash (:kind p))
    (str "wants to run " (:text p))
    (str "is waiting at a prompt: " (str/replace (:text p) "\n" " / "))))

(defn- stale-pending [entries member eff]
  (mapv (fn [e]
          (if (and (= :crew-prompt (:kind e)) (= member (:member e)) (= :pending (:state e))
                   (not= eff (:id (:prompt e))))
            (assoc e :state :stale)
            e))
        entries))

(defn- stale-missing [entries agent-ids]
  (mapv (fn [e]
          (if (and (= :crew-prompt (:kind e)) (= :pending (:state e))
                   (not (contains? agent-ids (:member e))))
            (assoc e :state :stale)
            e))
        entries))

(defn policy
  "What the fielding rules look like right now: whether the 'I'll answer'
   switch is on, and how long Hal gets before a benign prompt surfaces."
  [crew-talk hold]
  (let [n (:surface-after-s crew-talk)
        n (if (and (number? n) (<= 0 n 3600)) n default-surface-after-s)]
    {:hold? (cl/hold-on? hold) :surface-after-s n}))

(defn surfaced?
  "True when prompt should be shown to Michael under policy: hold is on,
   it's risky or unreadable, its age is unknown, or it's been waiting past
   :surface-after-s."
  [prompt now policy]
  (let [surface-s (let [n (:surface-after-s policy)]
                     (if (and (number? n) (<= 0 n 3600)) n default-surface-after-s))]
    (boolean
      (or (true? (:hold? policy))
          (not= :bash (:kind prompt))
          (true? (:risky prompt))
          (not (integer? (:since prompt)))
          (>= (- now (:since prompt)) (* 1000 surface-s))))))

(defn observe-crew
  "talk, folding in the latest crew.edn: a prompt line per newly surfaced
   member, a status line when one finishes, and staling of prompt lines
   that no longer apply. Identical to `talk` when nothing changes. The
   3-arity surfaces every prompt at once, matching v0's behaviour."
  ([talk crew-state now] (observe-crew talk crew-state now {:hold? true :surface-after-s 60}))
  ([talk crew-state now policy]
   (let [talk (or talk empty-talk)]
     (if (or (nil? crew-state) (not (crew/fresh? crew-state now)))
       talk
       (let [agents (:agents crew-state)
             titles (:titles crew-state)
             talk1  (update talk :entries stale-missing (set (keys agents)))
             result (reduce
                      (fn [t m]
                        (let [s       (:status (get agents m))
                              p       (:prompt (get agents m))
                              last    (get-in t [:crew-last m])
                              who     (get titles m (name m))
                              last-id (:prompt-id last)
                              eff     (cond
                                        (and (= s :blocked) p) (:id p)
                                        (= s :blocked) last-id
                                        :else nil)
                              t       (update t :entries stale-pending m eff)
                              id      (:id p)
                              show?   (and (= s :blocked) (some? p) (surfaced? p now policy))
                              t       (if (and show? (not= id last-id))
                                        (append-crew t {:who who :text (crew-prompt-text p) :at now
                                                         :kind :crew-prompt :member m :prompt p :state :pending})
                                        t)
                              t       (if (and (= s :done) (some? last) (not= :done (:status last)))
                                        (append-crew t {:who who :text "done" :at now :kind :crew-status :member m})
                                        t)
                              x       (cond
                                        (and (= s :blocked) p) (if (or show? (= id last-id)) id nil)
                                        (= s :blocked) last-id
                                        :else nil)]
                          (assoc-in t [:crew-last m] {:status s :prompt-id x})))
                      talk1
                      (sort (keys agents)))]
         (if (= result talk) talk result))))))

(defn allowable-kind?
  [e]
  (and (= :bash (:kind (:prompt e))) (false? (:risky (:prompt e)))))

(defn allowable? [e]
  (and (= :crew-prompt (:kind e)) (= :pending (:state e)) (allowable-kind? e)))

(defn- clean-note [note]
  (when (string? note)
    (let [t (str/trim note)]
      (when-not (str/blank? t) t))))

(defn crew-send
  "Allow, deny or reply to a pending crew-prompt entry n. Only that entry
   changes; anything else leaves talk unchanged (identical)."
  [talk n action note]
  (let [talk (or talk empty-talk)
        e    (entry talk n)]
    (if-not (and e (= :crew-prompt (:kind e)) (= :pending (:state e))
                 (contains? #{:allow :deny} action)
                 (or (= action :deny) (allowable? e)))
      talk
      (let [note' (when (= action :deny) (clean-note note))]
        (if (and note' (> (count note') 1000))
          talk
          (let [idx (first (keep-indexed (fn [i x] (when (= n (:n x)) i)) (:entries talk)))]
            (update-in talk [:entries idx]
                       (fn [x] (cond-> (-> x (dissoc :error :note) (assoc :state :sending :action action))
                                 note' (assoc :note note'))))))))))

(defn crew-result
  "Resolve a :sending crew-prompt or crew-say entry n with the bridge's
   outcome. Anything else leaves talk unchanged (identical)."
  [talk n outcome now]
  (let [talk (or talk empty-talk)
        e    (entry talk n)]
    (if-not (and e (= :sending (:state e)))
      talk
      (let [idx    (first (keep-indexed (fn [i x] (when (= n (:n x)) i)) (:entries talk)))
            result (:result outcome)
            reason (:reason outcome)]
        (update-in talk [:entries idx]
                   (fn [x]
                     (case (:kind x)
                       :crew-prompt
                       (case result
                         :ok (assoc x :state (if (= :allow (:action x)) :allowed :declined) :answered-at now)
                         :stale (assoc x :state :stale)
                         :error (-> x (dissoc :action :note) (assoc :state :pending :error reason))
                         x)
                       :crew-say
                       (case result
                         :ok (assoc x :state :sent)
                         (:stale :error) (assoc x :state :failed :error (or reason "refused"))
                         x)
                       x)))))))

(defn crew-say
  "Append a You → member line, in :sending state."
  [talk member who text now]
  (let [talk (or talk empty-talk)
        t    (when (string? text) (str/trim text))]
    (if-not (and (keyword? member) (non-blank-str? who) (string? t) (not (str/blank? t)) (<= (count t) 1000))
      talk
      (append-crew talk {:who you :text t :at now :kind :crew-say :member member :to who :state :sending}))))

(defn prompt-waiting?
  "True when some crew-prompt entry is waiting on the player or the
   bridge: drives the red badge."
  [talk]
  (let [talk (or talk empty-talk)]
    (boolean (some #(and (= :crew-prompt (:kind %)) (contains? #{:pending :sending} (:state %)))
                   (:entries talk)))))

(defn crew-controls [e bridge-on?]
  (when (and (= :crew-prompt (:kind e)) (contains? #{:pending :sending} (:state e)))
    (case (:state e)
      :sending {:allow? (allowable-kind? e) :disabled? true :hint "sending…"}
      :pending {:allow? (allowable? e) :disabled? (not bridge-on?) :hint (when-not bridge-on? "bridge off")})))

(defn- name-of [action]
  (case action :allowed "allowed" :declined "declined" nil))

(defn- mark-answered
  "Marks every pending/sending/stale, not-yet-marked crew-prompt entry for
   [member prompt-id] with who answered it, and stales it if it was pending."
  [entries member prompt-id at by]
  (mapv (fn [e]
          (if (and (= :crew-prompt (:kind e)) (= member (:member e))
                   (= prompt-id (:id (:prompt e)))
                   (contains? #{:pending :sending :stale} (:state e))
                   (nil? (:by e))
                   (let [since (let [s (:since (:prompt e))] (if (integer? s) s (:at e)))]
                     (>= at (- since 5000))))
            (cond-> (assoc e :by by)
              (= :pending (:state e)) (assoc :state :stale))
            e))
        entries))

(defn observe-log
  "talk, folding in new state/crew-log.edn entries: marks any matching
   surfaced prompt line as already answered, and logs a quiet line for each
   of Hal's recent answers. Identical to `talk` when there's nothing new."
  [talk log crew-state now]
  (let [talk   (or talk empty-talk)
        log-at (let [v (:log-at talk)] (if (integer? v) v 0))
        new    (when (sequential? log) (filter #(> (:at %) log-at) log))]
    (if (empty? new)
      talk
      (reduce
        (fn [t x]
          (let [t'  (update t :entries mark-answered (:member x) (:prompt-id x) (:at x) (:by x))
                who (get-in crew-state [:titles (:member x)] (name (:member x)))
                t'' (if (and (= :hal (:by x)) (> (:at x) (- now quiet-ms)))
                      (append-crew t' {:who hal-name :text (str (name-of (:action x)) " `" (:text x) "` for " who)
                                        :at (:at x) :kind :crew-log :member (:member x) :log x})
                      t')]
            (assoc t'' :log-at (:at x))))
        talk
        new))))

(defn log-label
  "The roster's past-entries line for a crew-log.edn entry."
  [x offset-min]
  (let [who (case (:by x) :hal "Hal" :michael "You" "")]
    (str who " " (name-of (:action x)) " `" (:text x) "` · " (clock-text (:at x) offset-min))))

(defn crew-label
  "The line for a crew entry, or nil for a non-crew entry."
  [e offset-min]
  (case (:kind e)
    :crew-prompt
    (let [risky (:risky (:prompt e))
          base  (str (when risky "⚠ ") (:who e) " · " (:text e))]
      (str base
           (case (:state e)
             :pending   (str (when risky " · Hal reviews this one")
                              (when-let [err (:error e)] (str " · not sent: " err)))
             :sending   " · sending…"
             :allowed   (str " · ☑ allowed " (clock-text (:answered-at e) offset-min))
             :declined  (str " · ✕ declined " (clock-text (:answered-at e) offset-min)
                              (when-let [note (:note e)] (str " · “" note "”")))
             :stale     (str " · already answered" (case (:by e) :hal " by Hal" :michael " by you" ""))
             "")))
    :crew-status (str (:who e) " · " (:text e))
    :crew-say
    (str "You → " (:to e) ": " (:text e)
         (case (:state e)
           :sending " · sending…"
           :sent    ""
           :failed  (str " · not sent: " (:error e))
           ""))
    :crew-log (str (:who e) " " (:text e) " · " (clock-text (:at e) offset-min))
    nil))

(defn crew-roster
  "The 💭 roster strip: every crew member, walker or not. The 5-arity also
   marks which rows are expanded and attaches each one's recent log."
  ([crew-state now bridge-on?]
   (if (nil? crew-state)
     []
     (let [fresh?  (crew/fresh? crew-state now)
           agents  (:agents crew-state)
           titles  (:titles crew-state)
           members (sort (into (set (keys titles)) (keys agents)))]
       (mapv (fn [m]
               (let [st (if (and fresh? (contains? agents m)) (:status (get agents m)) :unknown)]
                 {:member m :who (get titles m (name m)) :status st
                  :say? (and bridge-on? (contains? #{:idle :done} st))}))
             members))))
  ([crew-state now bridge-on? log expanded]
   (mapv (fn [row]
           (let [expanded? (contains? (or expanded #{}) (:member row))]
             (assoc row :expanded? expanded?
                    :log (if expanded? (cl/for-member log (:member row) roster-log-n) []))))
         (crew-roster crew-state now bridge-on?))))

(defn gone?
  "True when e belongs to a crew member who's no longer in a fresh
   crew-state (they left). Stale or missing crew-state never fades a line."
  [e crew-state now]
  (boolean
    (and (contains? crew-kinds (:kind e))
         (keyword? (:member e))
         (map? crew-state)
         (crew/fresh? crew-state now)
         (let [titles (:titles crew-state)
               present (if (map? titles) (set (keys titles)) (set (keys (:agents crew-state))))]
           (not (contains? present (:member e)))))))

(defn hold-switch
  "The 'I'll answer' checkbox's state, driven by hold.edn, never a local
   guess."
  [hold bridge-on? ui]
  (let [checked?  (cl/hold-on? hold)
        sending?  (true? (:sending? ui))
        disabled? (or (not bridge-on?) sending?)
        hint      (cond
                    (not bridge-on?) "bridge off"
                    sending? "sending…"
                    (string? (:error ui)) (str "not sent: " (:error ui))
                    checked? "you answer; Hal holds off"
                    :else "Hal answers the easy ones")]
    {:checked? checked? :disabled? disabled? :hint hint}))

(defn- valid-rate? [v] (and (number? v) (<= 0.1 v 10)))
(defn- valid-pitch? [v] (and (number? v) (<= 0 v 2)))
(defn- valid-lang? [v] (non-blank-str? v))

(defn- layer-voice [base layer]
  (cond-> base
    (valid-rate? (:rate layer))   (assoc :rate (:rate layer))
    (valid-pitch? (:pitch layer)) (assoc :pitch (:pitch layer))
    (valid-lang? (:lang layer))   (assoc :lang (:lang layer))))

(defn voice-for [world entry]
  (let [kind-layer   (get-in world [:voices (:kind entry)])
        mirror-layer (get-in world [:mirrors (:mirror entry) :voice])]
    (-> default-voice
        (layer-voice kind-layer)
        (layer-voice mirror-layer))))

(defn save-text [talk]
  (let [talk (or talk empty-talk)]
    (pr-str {:v 1 :n (:n talk) :seen (:seen talk) :crew-last (:crew-last talk) :log-at (:log-at talk)
             :entries (mapv #(select-keys % [:n :who :text :at :kind :mirror :event :chore :done? :asks
                                               :member :prompt :state :action :note :answered-at :error :to
                                               :by :log])
                             (:entries talk))})))

(defn- valid-restored-entry? [e]
  (and (map? e)
       (pos-int? (:n e))
       (non-blank-str? (:who e))
       (non-blank-str? (:text e))
       (finite-number? (:at e))
       (keyword? (:kind e))
       (cond
         (= :chore (:kind e))
         (and (some? (chore-record (:chore e))) (boolean? (:done? e)))

         (= :crew-log (:kind e))
         (and (keyword? (:member e)) (cl/valid-entry? (:log e)))

         (contains? crew-kinds (:kind e))
         (and (keyword? (:member e))
              (case (:kind e)
                :crew-prompt (and (crew/valid-prompt? (:prompt e)) (contains? prompt-states (:state e)))
                :crew-status true
                :crew-say (and (non-blank-str? (:to e)) (contains? say-states (:state e)))))

         :else true)))

(defn- clean-restored-entry [e]
  (if (contains? crew-kinds (:kind e))
    (case (:kind e)
      :crew-prompt
      (let [sending? (= :sending (:state e))
            base     (select-keys e [:n :who :text :at :kind :member :prompt])]
        (cond-> (assoc base :state (if sending? :pending (:state e)))
          (and (not sending?) (keyword? (:action e)))       (assoc :action (:action e))
          (and (not sending?) (string? (:note e)))          (assoc :note (:note e))
          (and (not sending?) (number? (:answered-at e)))   (assoc :answered-at (:answered-at e))
          (contains? cl/bys (:by e))                        (assoc :by (:by e))))
      :crew-status (select-keys e [:n :who :text :at :kind :member])
      :crew-say
      (let [sending? (= :sending (:state e))
            state    (if sending? :failed (:state e))]
        (cond-> (assoc (select-keys e [:n :who :text :at :kind :member :to]) :state state)
          sending? (assoc :error "not confirmed")
          (and (not sending?) (string? (:error e))) (assoc :error (:error e))))
      :crew-log (select-keys e [:n :who :text :at :kind :member :log]))
    (let [base (cond-> (select-keys e [:n :who :text :at :kind])
                 (keyword? (:mirror e)) (assoc :mirror (:mirror e))
                 (keyword? (:event e))  (assoc :event (:event e))
                 (= :chore (:kind e))   (assoc :chore (chore-record (:chore e)) :done? (:done? e)))]
      (if (keyword? (:asks e))
        (let [cleaned-chore (chore-record (:chore e))]
          (cond-> (assoc base :asks (:asks e)
                         :done? (and (true? (:done? e)) (some? cleaned-chore)))
            cleaned-chore (assoc :chore cleaned-chore)))
        base))))

(defn- drop-repeats
  "Drop an entry when the same speaker said the same text as its previous
   entry within quiet-ms (reloads used to log the scene again). Boxes
   (chore lines and requests) and crew lines pass through untouched and
   aren't tracked, so two with the same text a minute apart both stay."
  [entries]
  (:out (reduce (fn [{:keys [out last]} e]
                  (if (or (box? e) (contains? crew-kinds (:kind e)))
                    {:out (conj out e) :last last}
                    (let [k [(:who e) (:mirror e)]
                          p (get last k)]
                      (if (and p (= (:text p) (:text e)) (< (- (:at e) (:at p)) quiet-ms))
                        {:out out :last last}
                        {:out (conj out e) :last (assoc last k e)}))))
                {:out [] :last {}}
                entries)))

(defn- valid-crew-last-entry? [v]
  (and (map? v)
       (contains? crew/statuses (:status v))
       (let [p (:prompt-id v)]
         (or (nil? p) (and (string? p) (boolean (re-matches #"^[0-9a-f]{40}$" p)))))))

(defn- clean-crew-last [m]
  (if (map? m)
    (into {}
          (keep (fn [[k v]] (when (and (keyword? k) (valid-crew-last-entry? v)) [k v])))
          m)
    {}))

(defn- last-says
  "What each creature last said, from the history: a standing bubble that
   hasn't changed since isn't logged again. Boxes aren't bubbles."
  [entries]
  (into {} (for [e entries :when (and (keyword? (:mirror e)) (not= :chore (:kind e)))]
             [(:mirror e) (:text e)])))

(defn restore [text]
  (let [m (try (reader/read-string text) (catch :default _ nil))]
    (if-not (and (map? m) (= 1 (:v m)))
      empty-talk
      (let [raw         (:entries m)
            raw         (if (sequential? raw) raw [])
            entries     (->> raw
                              (filter valid-restored-entry?)
                              (map clean-restored-entry)
                              (sort-by :n)
                              drop-repeats
                              (take-last cap)
                              vec)
            max-entry-n (if (seq entries) (:n (peek entries)) 0)
            n-m         (:n m)
            n-candidate (if (and (integer? n-m) (>= n-m 0)) n-m 0)
            n           (max n-candidate max-entry-n 0)
            seen-m      (:seen m)
            seen        (if (integer? seen-m)
                          (-> seen-m (max 0) (min n))
                          n)
            log-at-m    (:log-at m)
            log-at      (if (and (integer? log-at-m) (>= log-at-m 0)) log-at-m 0)]
        (assoc empty-talk :entries entries :n n :seen seen :log-at log-at
               :last-say (last-says entries)
               :crew-last (clean-crew-last (:crew-last m)))))))
