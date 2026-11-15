(ns pm.blueprint
  "Blueprints (GDD §2.1, §4.8): an agent-made change first appears in place
   as a ghosted version of itself with a one-line summary; approve it and
   it's real, reject it and it vanishes. Pure: world + now -> data. No
   three.js, no js/Date, no atoms; `pm.scene` turns `ghost-plan` into meshes
   through `pm.three/ghost!`."
  (:require [clojure.string :as str]
            [pm.content :as c]
            [pm.plan :as p]))

(def statuses
  "Every status a blueprint can be in."
  #{:pending :approved :real :rejected})

(defn- valid-at? [at]
  (and (vector? at) (= 3 (count at)) (every? number? at)))

(defn- valid-blueprint? [bp]
  (and (map? bp)
       (keyword? (:id bp))
       (valid-at? (:at bp))
       (string? (:summary bp))
       (not (str/blank? (:summary bp)))))

(defn- status-of [bp]
  (let [s (:status bp)]
    (if (contains? statuses s) s :pending)))

(defn all
  "Every well-formed blueprint in `world`, each defaulted to :pending."
  [world]
  (->> (:blueprints world)
       (filter valid-blueprint?)
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
   `id` isn't a current blueprint's."
  [world id]
  (set-status world id :approved))

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
  "A ghost's sign: its one-line summary, then how to act on it, or why its
   approval was refused. The wording is data (`:blueprint-text` in
   estate.edn); without it, just the summary."
  [world bp]
  (let [{:keys [hint failed trial]} (:blueprint-text world)
        tail (cond (:failed? bp) failed
                   (= :approved (:status bp)) trial
                   :else hint)]
    (if (and (string? tail) (not (str/blank? tail)))
      (str (:summary bp) " · " tail)
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

(defn reject-blueprint
  "Rejecting a blueprint (GDD §2.1, §4.8): it vanishes -- drops out of
   `ghost-plan`, and `pm.scene` removes its mesh, so it never becomes
   real. A pending ghost or a trial build; a real one stays (no-op), as
   does an `id` that isn't a current blueprint's."
  [world id]
  (if (contains? #{:pending :approved} (status world id))
    (set-status world id :rejected)
    world))

(def max-part-dim
  "Metres. Any part dimension past this reads as a generation gone wrong,
   not a deliberately large object -- the kind of change GDD §4.8 says
   must never reach the live estate."
  20)

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
