(ns pm.talk
  "The talk log: what Hal and the creatures said, captioned and spoken. Pure:
   no atoms, no js/Date, no DOM. Never names a mirror."
  (:require [cljs.reader :as reader]
            [clojure.string :as str]
            [pm.world :as w]))

(def hal-name "Hal")
(def cap 100)
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
   :last-hal nil :last-say {}})

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
      (count (filter #(> (:n %) (:seen talk)) (:entries talk))))))

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
    (pr-str {:v 1 :n (:n talk) :seen (:seen talk)
             :entries (mapv #(select-keys % [:n :who :text :at :kind :mirror :event :chore :done? :asks])
                             (:entries talk))})))

(defn- valid-restored-entry? [e]
  (and (map? e)
       (pos-int? (:n e))
       (non-blank-str? (:who e))
       (non-blank-str? (:text e))
       (finite-number? (:at e))
       (keyword? (:kind e))
       (if (= :chore (:kind e))
         (and (some? (chore-record (:chore e))) (boolean? (:done? e)))
         true)))

(defn- clean-restored-entry [e]
  (let [base (cond-> (select-keys e [:n :who :text :at :kind])
               (keyword? (:mirror e)) (assoc :mirror (:mirror e))
               (keyword? (:event e))  (assoc :event (:event e))
               (= :chore (:kind e))   (assoc :chore (chore-record (:chore e)) :done? (:done? e)))]
    (if (keyword? (:asks e))
      (let [cleaned-chore (chore-record (:chore e))]
        (cond-> (assoc base :asks (:asks e)
                       :done? (and (true? (:done? e)) (some? cleaned-chore)))
          cleaned-chore (assoc :chore cleaned-chore)))
      base)))

(defn- drop-repeats
  "Drop an entry when the same speaker said the same text as its previous
   entry within quiet-ms (reloads used to log the scene again). Boxes
   (chore lines and requests) pass through untouched and aren't tracked, so
   two boxes with the same text a minute apart both stay."
  [entries]
  (:out (reduce (fn [{:keys [out last]} e]
                  (if (box? e)
                    {:out (conj out e) :last last}
                    (let [k [(:who e) (:mirror e)]
                          p (get last k)]
                      (if (and p (= (:text p) (:text e)) (< (- (:at e) (:at p)) quiet-ms))
                        {:out out :last last}
                        {:out (conj out e) :last (assoc last k e)}))))
                {:out [] :last {}}
                entries)))

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
                          n)]
        (assoc empty-talk :entries entries :n n :seen seen
               :last-say (last-says entries))))))
