(ns pm.blueprint
  "Blueprints (GDD §2.1, §4.8): an agent-made change first appears in place
   as a ghosted version of itself with a one-line summary; approve it and
   it's real, reject it and it vanishes. Pure: world + now -> data. No
   three.js, no js/Date, no atoms; `pm.scene` turns `ghost-plan` into meshes
   through `pm.three/ghost!`."
  (:require [clojure.string :as str]
            [pm.blueprint-file :as bf]
            [pm.content :as c]
            [pm.plan :as p]))

(def statuses
  "Every status a blueprint can be in."
  #{:pending :approved :real :rejected})

(defn- valid-at? [at]
  (and (vector? at) (= 3 (count at)) (every? number? at)))

(defn- status-of [bp]
  (let [s (:status bp)]
    (if (contains? statuses s) s :pending)))

(defn all
  "Every well-formed blueprint in `world`, each defaulted to :pending."
  [world]
  (->> (:blueprints world)
       (filter bf/valid?)
       (mapv #(assoc % :status (status-of %)))))

(defn pending
  "Blueprints still waiting on the player, in order."
  [world]
  (filterv #(= :pending (:status %)) (all world)))

(defn- set-status [world id status]
  (update world :blueprints
          (fn [bps]
            (mapv (fn [bp] (if (= id (:id bp)) (assoc bp :status status) bp))
                  (or bps [])))))

(defn approve-blueprint
  "Approving a blueprint (GDD §2.1, §4.8): it stops being pending, so it
   drops out of `ghost-plan` and the change it previewed is the one in
   play -- `pm.scene` un-ghosts its mesh so it reads as real. No-op when
   `id` isn't a current blueprint's. Also records `id` as the latest trial
   in `:trial-order` (TASKS T-14, `card-owner`), moved to the end if it was
   already there; no `:trial-order` key is added for an unknown `id`."
  [world id]
  (let [w (set-status world id :approved)]
    (if (some #(= id (:id %)) (:blueprints world))
      (assoc w :trial-order (conj (vec (remove #{id} (:trial-order world))) id))
      w)))

(defn mark-failed
  "A blueprint whose approval was refused because it would break the build
   (`breaks-build?`): it stays a ghost, and its sign says why. No-op when
   `id` isn't a current blueprint's."
  [world id]
  (update world :blueprints
          (fn [bps] (mapv (fn [bp] (if (= id (:id bp)) (assoc bp :failed? true) bp)) (or bps [])))))

(defn refuse
  "Approval refused because the blueprint would break the build: it stays a
   ghost, its sign says why (`mark-failed`), and the twin says it in the
   caption too, so the refusal is something you notice (a silent no-op
   reads as a broken click). Wording: `:blueprint-text :failed` in
   estate.edn; no caption without it."
  [world id now]
  (let [w    (mark-failed world id)
        text (get-in world [:blueprint-text :failed])]
    (if (and (string? text) (not (str/blank? text)))
      (assoc w :hal {:event :blueprint-refused :text text :until (+ now c/hal-ms)})
      w)))

(defn sign-text
  "A ghost's sign: its one-line summary, then on a second line how to act on it, or why its
   approval was refused. The wording is data (`:blueprint-text` in
   estate.edn); without it, just the summary."
  [world bp]
  (let [{:keys [hint failed trial steep]} (:blueprint-text world)
        tail (cond (:failed? bp) failed
                   (and (:too-steep? bp) (= :pending (:status bp)) (string? steep)) steep
                   (= :approved (:status bp)) trial
                   :else hint)]
    (if (and (string? tail) (not (str/blank? tail)))
      (str (:summary bp) "\n" tail)   ; two lines: one long line shrank the font to fit (Michael)
      (:summary bp))))

(defn make-real
  "A trial build made real (GDD §2.1, §4.6): recorded in the private
   real-builds file, it's in the estate every session and can't be
   dismissed. Only a built (:approved) or already-real blueprint; no-op
   otherwise."
  [world id]
  (let [bp (first (filter #(= id (:id %)) (all world)))]
    (if (contains? #{:approved :real} (:status bp))
      (set-status world id :real)
      world)))

(defn apply-real-builds
  "The real-builds file (`{:real [id ...]}`, state/builds.edn) laid over a
   freshly loaded world: those blueprints are :real, whatever they were."
  [world builds]
  (let [ids (set (when (map? builds) (filter keyword? (:real builds))))]
    (if (empty? ids)
      world
      (update world :blueprints
              (fn [bps] (mapv #(if (contains? ids (:id %)) (assoc % :status :real) %) (or bps [])))))))

(defn status
  "The status of blueprint `id` in `world`, or nil."
  [world id]
  (:status (first (filter #(= id (:id %)) (all world)))))

(defn unapprove
  "Undo on a trial build (TASKS T-14): back to its blue ghost, so it can be
   tried again later. Not a reject."
  [world id]
  (if (= :approved (status world id))
    (set-status world id :pending)
    world))

(defn card-owner
  "The id of the trial build the trial card belongs to (TASKS T-14): the
   most recent one in `:trial-order` still `:approved`, falling back to the
   last `:approved` blueprint for a world with no `:trial-order`. nil when
   none is `:approved`."
  [world]
  (or (last (filter #(= :approved (status world %)) (:trial-order world)))
      (:id (last (filter #(= :approved (:status %)) (all world))))))

(def card-lift-m
  "Metres above the item's bounding-box top the trial card floats (TASKS T-14)."
  0.3)

(defn trial-card
  "The trial card for the current trial (TASKS T-14, `card-owner`), or nil
   without one or without all its words (`:make-real`/`:undo`/`:saving` in
   `:blueprint-text`). `saving` is the id whose make-real request is in
   flight, or nil; while any request is in flight both buttons are
   disabled, so nothing can post twice or undo a build being recorded."
  [world saving]
  (when-let [owner (card-owner world)]
    (let [{:keys [make-real undo] saving-text :saving} (:blueprint-text world)]
      (when (every? #(and (string? %) (not (str/blank? %))) [make-real undo saving-text])
        (let [disabled? (some? saving)]
          {:id owner
           :buttons [{:action :real :label (if (= saving owner) saving-text make-real) :disabled? disabled?}
                     {:action :undo :label undo :disabled? disabled?}]})))))

(defn reject-blueprint
  "Rejecting a blueprint (GDD §2.1, §4.8): it vanishes -- drops out of
   `ghost-plan`, and `pm.scene` removes its mesh, so it never becomes
   real. A pending ghost or a trial build; a real one stays (no-op), as
   does an `id` that isn't a current blueprint's."
  [world id]
  (if (contains? #{:pending :approved} (status world id))
    (set-status world id :rejected)
    world))

;; -- Live file merge (TASKS T-13b) --------------------------------------
;; An agent proposes a blueprint with `bb blueprint-add`, into the private
;; state/blueprints.edn; the 5 s state poll merges it into `!world` without
;; a reload. merge-file is the one pure step: new file ids arrive pending,
;; ids the file drops disappear unless they're :real or a trial is
;; standing, an estate id always wins over a file entry with the same id,
;; and an existing entry keeps everything about it (status, seat, look).

(defn- distinct-by-id
  "`entries` with every id after its first occurrence dropped, order kept."
  [entries]
  (first (reduce (fn [[out seen] e]
                   (if (contains? seen (:id e))
                     [out seen]
                     [(conj out e) (conj seen (:id e))]))
                 [[] #{}] entries)))

(defn merge-file
  "`state/blueprints.edn` (raw, or nil when it's missing) merged into
   `world`: `{:world w' :added [ids] :removed [ids] :announce [ids]}`.
   A no-op (world unchanged, `identical?`) when the file's `:as-of` hasn't
   moved since the last merge."
  [world file]
  (let [f (bf/normalize file)]
    (if (= (:as-of f) (:blueprints-as-of world))
      {:world world :added [] :removed [] :announce []}
      (let [first?       (nil? (:blueprints-as-of world))
            bps          (or (:blueprints world) [])
            estate-ids   (set (keep #(when-not (:from-file? %) (:id %)) bps))
            incoming     (->> (:blueprints f)
                               (filter bf/valid?)
                               (remove #(contains? estate-ids (:id %)))
                               distinct-by-id)
            incoming-ids (set (map :id incoming))
            drop?        (fn [bp] (and (:from-file? bp)
                                        (not (contains? incoming-ids (:id bp)))
                                        (not (contains? #{:real :approved} (status-of bp)))))
            kept         (vec (remove drop? bps))
            removed      (mapv :id (filter drop? bps))
            kept-ids     (set (map :id kept))
            new          (->> incoming
                               (remove #(contains? kept-ids (:id %)))
                               (mapv #(assoc (select-keys % [:id :at :yaw :summary :look :by])
                                             :from-file? true :status :pending)))
            announced    (or (:blueprints-announced world) #{})
            announce     (if first? [] (vec (remove announced (map :id new))))]
        {:world    (assoc world :blueprints (into kept new)
                           :blueprints-as-of (:as-of f)
                           :blueprints-announced (into announced (map :id new)))
         :added    (mapv :id new)
         :removed  removed
         :announce announce}))))

(defn author-line
  "The talk entry for a newly arrived blueprint's author (GDD §2.1, §4.8):
   `{:who :text :kind :blueprint :event :blueprint-drawn}`, or nil unless
   `id` is a current blueprint with a keyword `:by`. `:who` is that
   member's walker name, else its crew title, else its bare keyword name."
  [world id]
  (when-let [bp (first (filter #(= id (:id %)) (:blueprints world)))]
    (when (keyword? (:by bp))
      (let [by      (:by bp)
            who     (or (:name (first (filter #(= (name by) (name (:agent %))) (:walkers world))))
                        (let [title (get-in world [:crew :titles by])]
                          (when (and (string? title) (not (str/blank? title))) title))
                        (name by))
            summary (:summary bp)
            s       (if (str/ends-with? summary ".") (subs summary 0 (dec (count summary))) summary)]
        ;; TODO content: move to the content pipeline (no hal-lines event fits)
        {:who who :text (str "drew a blueprint: " s ". Click it to try it.")
         :kind :blueprint :event :blueprint-drawn}))))

(defn drawable-changes
  "What `pm.scene` must mount/unmount to catch up with `world`'s
   blueprints, given `drawn` (the set of ids it already has groups for,
   nil = #{}): `{:add [ids] :remove #{ids}}`. `:add` is drawable
   (:pending/:approved/:real) ids not yet drawn, in `all`'s order;
   `:remove` is drawn ids that are no longer any blueprint's at all (a
   :rejected one is neither -- the status sync already removed its mesh)."
  [drawn world]
  (let [drawn (or drawn #{})
        bps   (all world)
        ids   (set (map :id bps))]
    {:add    (mapv :id (filter #(and (contains? #{:pending :approved :real} (:status %))
                                      (not (contains? drawn (:id %))))
                                bps))
     :remove (set (remove ids drawn))}))

(def max-part-dim
  "Metres. Any part dimension past this reads as a generation gone wrong,
   not a deliberately large object -- the kind of change GDD §4.8 says
   must never reach the live estate."
  20)

;; -- Seating on the ground --------------------------------------------------
;; An agent guesses a blueprint's :at from one probe at its centre; on the
;; creek bank that left the mailbox bench's legs half a metre in the hill on
;; one side and in the air on the other (Michael, Nov 22). So before it is
;; drawn, every blueprint is seated: the game reads the ground under each
;; foot, keeps the item level at the highest one, and stretches the lower
;; feet down to touch. Ground that falls away more than `max-seat-drop-m`
;; under one item is too steep: it is still drawn seated, but it won't
;; build, and its sign says why.

(def max-seat-drop-m
  "Metres: the most a foot may be stretched to reach the ground."
  0.25)

(def ^:private foot-eps 0.02)

(defn- foot? [{[_ dy _] :at [_ sy _] :size}]
  (and (number? dy) (number? sy) (< (js/Math.abs (- dy (/ sy 2))) foot-eps)))

(defn- foot-xz
  "World x/z of `part`'s centre for an item at `at` turned `yaw` degrees
   (pm.scene's convention: +90 points the item's +x at world -z)."
  [[x _ z] yaw {[dx _ dz] :at}]
  (let [r (* (/ (or yaw 0) 180) js/Math.PI)
        c (js/Math.cos r) s (js/Math.sin r)]
    [(+ x (* c dx) (* s dz)) (+ z (- (* s dx)) (* c dz))]))

(defn seat
  "`bp` seated on the ground (`ground-at` is x z -> height or nil): :at's
   height becomes the highest ground under its feet (parts resting on the
   item's base, y 0), and each lower foot is stretched down to its own
   ground. `:seat-drop` is the largest stretch; `:too-steep?` is true past
   `max-seat-drop-m`. With no feet it sits on the ground at its centre.
   Unchanged if any ground read is missing, the look has no :parts list,
   or it's already seated."
  [bp ground-at]
  (let [{:keys [at yaw look]} bp
        parts (:parts look)]
    (if (or (:seated? bp) (not (valid-at? at)) (not (sequential? parts)) (empty? parts))
      bp
      (let [feet  (keep-indexed (fn [i part] (when (foot? part) [i (foot-xz at yaw part)])) parts)
            pts   (if (seq feet) (map second feet) [[(first at) (nth at 2)]])
            ys    (map (fn [[x z]] (ground-at x z)) pts)]
        (if (some #(not (number? %)) ys)
          bp
          (let [base  (apply max ys)
                drops (zipmap (map first feet) (map #(- base %) ys))
                most  (apply max 0 (vals drops))
                parts' (vec (map-indexed
                              (fn [i part]
                                (let [d (get drops i 0)]
                                  (if (pos? d)
                                    (-> part
                                        (update-in [:size 1] + d)
                                        (update-in [:at 1] - (/ d 2)))
                                    part)))
                              parts))]
            (-> bp
                (assoc :at [(first at) base (nth at 2)]
                       :seated? true
                       :seat-drop most
                       :too-steep? (> most max-seat-drop-m))
                (assoc-in [:look :parts] parts'))))))))

(defn refuse-steep
  "Approval refused because the ground under the blueprint is too steep
   (`seat`'s :too-steep?): it stays a ghost (its sign already says so) and
   the twin says it in the caption. Wording: `:blueprint-text :steep`."
  [world now]
  (let [text (get-in world [:blueprint-text :steep])]
    (if (and (string? text) (not (str/blank? text)))
      (assoc world :hal {:event :blueprint-refused :text text :until (+ now c/hal-ms)})
      world)))

(defn seat-all
  "Every blueprint in `world` seated on the ground (`seat`)."
  [world ground-at]
  (if (sequential? (:blueprints world))
    (update world :blueprints (fn [bps] (mapv #(if (map? %) (seat % ground-at) %) bps)))
    world))

(defn seat-ids
  "Like `seat-all`, but only blueprints whose id is in `ids` go through
   `seat`; `world` unchanged if `ids` is empty or matches none."
  [world ids ground-at]
  (let [idset (set ids)]
    (if (and (sequential? (:blueprints world)) (seq idset))
      (update world :blueprints
              (fn [bps] (mapv #(if (and (map? %) (contains? idset (:id %))) (seat % ground-at) %) bps)))
      world)))

(defn- valid-part-strict? [part]
  (and (map? part)
       (contains? p/shapes (:shape part))
       (vector? (:size part))
       (= 3 (count (:size part)))
       (every? #(and (number? %) (pos? %) (<= % max-part-dim)) (:size part))))

(defn breaks-build?
  "True when `bp`'s declared look parts could not become real geometry: an
   unknown shape, a malformed size, or a part scaled far past anything
   deliberate. GDD §4.8: 'a bad change can never take down the running
   game.' `apply-approved!` checks this before a blueprint goes live, and
   reverts instead of applying it when it's true."
  [bp]
  (let [parts (:parts (:look bp))]
    (boolean (and (sequential? parts)
                  (seq parts)
                  (not (every? valid-part-strict? parts))))))

(defn built-plan
  "Scene items for every built blueprint, drawn solid: a trial build
   (:approved) with a sign saying how to make it real, a real one
   (state/builds.edn, loaded every session) with none. Same shape as
   `ghost-plan`'s items, no ghost."
  [world]
  (vec
    (for [bp (all world) :when (contains? #{:approved :real} (:status bp))]
      (let [{:keys [id at yaw look]} bp
            [x y z] at
            parts (p/look-parts look nil)
            top   (p/parts-top parts)]
        (cond-> {:key    [:blueprint id]
                 :at     at
                 :yaw    (if (number? yaw) yaw 0)
                 :parts  parts
                 :center [x (+ y (/ top 2)) z]}
          (= :approved (:status bp))
          (assoc :label :sign :label-at [x (+ y top p/label-lift) z] :text (sign-text world bp)))))))

(defn ghost-plan
  "Scene items for every pending blueprint, shaped like `pm.plan/scene-plan`
   items (so `pm.scene` mounts them the same way) plus `:ghost?` true and a
   `:sign` label carrying the blueprint's one-line summary."
  [world]
  (vec
    (for [bp (pending world)]
      (let [{:keys [id at yaw look summary]} bp
            [x y z] at
            parts (p/look-parts look nil)
            top   (p/parts-top parts)
            yaw   (if (number? yaw) yaw 0)]
        {:key      [:blueprint id]
         :ghost?   true
         :at       at
         :yaw      yaw
         :parts    parts
         :center   [x (+ y (/ top 2)) z]
         :label    :sign
         :label-at [x (+ y top p/label-lift) z]
         :text     (sign-text world bp)}))))
