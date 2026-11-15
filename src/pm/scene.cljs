(ns pm.scene
  "Mounts the plan's items into the kit's (or scaffold's) scene, through
   `pm.three` only. Never touches `js/THREE`, the npm module, or
   `window.hiroshi` directly."
  (:require [pm.three :as t]
            [pm.plan :as p]
            [pm.blueprint :as bp]
            [pm.content :as c]))

(def palette
  "The only hex literals. A look colour not in this map -> :stand-in."
  {:pink         0xff9ec4
   :brown        0x8a5a3b
   :glass        0xbfe3dc
   :gold         0xe3c46b
   :moss         0x7a9a5a
   :gill         0xe0678f
   :eye          0x1e1a1c
   :mane         0x3b2a1e
   :tin          0x9a9184
   :stand-in     0xb8b0a6
   :booth-red    0xc0392b
   :bubble-paper 0xfffaf0
   :bubble-ink   0x3a2d22
   :sign-paper   0xf6ead0   ; lighter parchment (was 0xe9d3a8), for contrast
   :sign-ink     0x4a3420
   :crew         0x5b7fa3
   :crew-head    0xf0e6d6
   :path-walker  0x4fd1c5
   :path-static  0xf6ad55
   :path-aim     0xf56565
   :feet-ground  0x48bb78
   :feet-target  0xed64a6
   :path-sight   0xfaf089
   :path-hear    0x9f7aea
   :path-glance  0x63b3ed
   :path-creature 0xf687b3})

(def label-height 0.3)   ; the old size; positions in tests and walker heads still key off it

;; Every label (creature speech, signs, walker nameplates) shares one look:
;; bigger, lighter, outlined, so it reads at a distance (Michael, Nov 7).
(def plate {:height 0.4 :canvas [640 128] :font-max 78})

(defn- color [kw]
  (get palette kw (:stand-in palette)))

(defonce ^:private !mounted (atom nil))

(defn- label-opts [label-kind pos text]
  (let [[paper ink] (if (= label-kind :sign)
                       [:sign-paper :sign-ink]
                       [:bubble-paper :bubble-ink])]
    (assoc plate
           :text   text
           :paper  (color paper)
           :ink    (color ink)
           :border (color ink)
           :pos    pos)))

(defn- mount-item! [root item]
  (let [{:keys [key at yaw parts label label-at text ghost?]} item
        ^js item-group (t/group {:name (pr-str key) :pos at :rot [0 (p/radians yaw) 0]})]
    (doseq [[i part] (map-indexed vector parts)]
      (let [^js mesh (t/stand-in {:shape (:shape part)
                                  :size  (:size part)
                                  :color (color (:color part))
                                  :pos   (:at part)
                                  :rot   (mapv p/radians (:rot part))})]
        (set! (.-name mesh) (str (pr-str key) "/" i))
        (when (:glow part) (set! (.. mesh -userData -glow) true))
        (t/add! item-group mesh)))
    (when ghost? (t/ghost! item-group))
    (t/add! root item-group)
    (when label
      (let [sprite (t/label (label-opts label label-at text))]
        (t/add! root sprite)
        sprite))))

(defn- with-lobby
  "`plan` plus the lobby's own item (GDD §2.4, §4.8), when `world` places
   one."
  [world plan]
  (if-let [lobby (p/lobby-item world)]
    (conj plan lobby)
    plan))

(defn- with-workshop
  "`plan` plus the workshop's own item (GDD §2.4, §4.8), when `world` places
   one."
  [world plan]
  (if-let [workshop (p/workshop-item world)]
    (conj plan workshop)
    plan))

(defn- with-mailbox
  "`plan` plus the mailbox's own item (GDD §2.4), when `world` places one."
  [world plan]
  (if-let [mailbox (p/mailbox-item world c/sample-inbox)]
    (conj plan mailbox)
    plan))

(defn- with-phone-booth
  "`plan` plus the phone booth's own item (GDD §2.3, §4.8), when `world`
   places one."
  [world plan]
  (if-let [booth (p/phone-booth-item world)]
    (conj plan booth)
    plan))

(defn- with-blueprints
  "`plan` plus a ghosted item for every pending blueprint (GDD §2.1, §4.8):
   an agent-made change shown in place, with its one-line summary, before
   the player approves it."
  [world plan]
  (-> plan (into (bp/ghost-plan world)) (into (bp/built-plan world))))

(def ^:private snap-drop-m
  "Metres the dropped worm falls before it's caught."
  1.0)

(def ^:private snap-lift-m
  "Metres the fed mirror's body shoots up at the peak of the snap."
  0.35)

(defn- snap-worm!
  "A small flat-shaded capsule for the snap's dropped worm (GDD §2.2, §4.8),
   hidden until `play-snap!` places and shows it. Added to `root` once, at
   `mount!` time."
  [root]
  (let [^js worm (t/stand-in {:shape :capsule :size [0.4 0.09 0.09] :color (color :brown) :pos [0 0 0]})]
    (set! (.-name worm) "pm-snap-worm")
    (t/set-visible! worm false)
    (t/add! root worm)
    worm))

(defn- snap-body-at
  "The `[mirror-id :body]` item's own `:at` in `plan` (the plan's positions
   never follow world state -- GDD §2.2, §4.8 -- so this is always the
   mirror's resting place, snap or no snap)."
  [plan mirror-id]
  (:at (first (filter #(= [mirror-id :body] (:key %)) plan))))

(defn- snap-reset!
  "No snap live for this mirror: hide the worm and put its body back at its
   resting `at`."
  [snap-worm body at]
  (when snap-worm (t/set-visible! snap-worm false))
  (when (and body at) (t/set-pos! body at)))

(defn- snap-step!
  "Mid-snap, at progress `p` (0..1) of `pm.plan/snap-ms`: the worm falls
   through the first half and is caught, the body shoots up to its peak at
   the midpoint and back down by the end."
  [snap-worm body at p]
  (let [[x y z] at
        lift (* snap-lift-m (js/Math.sin (* p js/Math.PI)))
        fall (* snap-drop-m (max 0 (- 1 (/ p 0.5))))]
    (when snap-worm
      (t/set-visible! snap-worm (< p 0.55))
      (t/set-pos! snap-worm [x (+ y fall) z]))
    (when body (t/set-pos! body [x (+ y lift) z]))))

(defn play-snap!
  "The snap on screen (GDD §2.2, §4.8): while `pm.plan/snap-pose` says one is
   live for some mirror, drops the worm onto the water and shoots that
   mirror's body group up and back down -- the bubble popping and the jar's
   new count are already handled by `update!`'s label refresh. Resets both
   once the pose is nil (nothing live, or it already finished). Mutates
   three.js objects only through `pm.three`; never touches the scene plan
   itself (its positions never follow world state)."
  [world now]
  (when-let [{:keys [root plan snap-worm]} @!mounted]
    (let [mirror-id (:mirror (:snap world))
          body      (and mirror-id (t/by-name root (pr-str [mirror-id :body])))
          at        (and mirror-id (snap-body-at plan mirror-id))
          p         (and mirror-id (p/snap-pose world mirror-id now))]
      (if p
        (snap-step! snap-worm body at p)
        (snap-reset! snap-worm body at))))
  nil)

(defn- with-fixtures [world plan]
  (->> plan (with-lobby world) (with-workshop world) (with-mailbox world) (with-phone-booth world) (with-blueprints world)))

(defn- blueprint-key [bp] [:blueprint (:id bp)])

(defn- sync-blueprints!
  "Approving or rejecting a blueprint (GDD §2.1, §4.8) has to change a mesh
   already mounted as a ghost -- the one case where this namespace creates
   or removes something after `mount!`. Runs once per status change (a
   pending blueprint going :approved un-ghosts its mesh so it reads as
   real; going :rejected removes its mesh and sign so it vanishes)."
  [world]
  (when-let [{:keys [root labels] bp-status :bp-status} @!mounted]
    (let [prev (or bp-status {})
          now  (into {} (map (fn [bp] [(blueprint-key bp) (:status bp)])) (bp/all world))]
      (doseq [[k status] now
              :when (not= status (get prev k))]
        (case status
          :approved (do (when-let [^js g (t/by-name root (pr-str k))]
                          (t/unghost! g)
                          ;; a built lamp lights like the kit's own lamps
                          (t/light-glows! g (:kit-lights @!mounted)))
                        (when-let [sprite (get labels k)] (t/set-visible! sprite false)))
          :rejected (do (when-let [^js g (t/by-name root (pr-str k))] (t/remove! root g))
                        (when-let [sprite (get labels k)] (t/remove! root sprite)))
          nil))
      (swap! !mounted assoc :bp-status now)))
  nil)

(defn update!
  "Refresh the labels whose text changed since the last call, and apply any
   blueprint approvals/rejections (`sync-blueprints!`). Otherwise never
   creates or removes meshes/labels."
  [world now]
  (when-let [{:keys [labels texts]} @!mounted]
    (let [plan    (with-fixtures world (p/scene-plan world now))
          changes (p/label-changes texts plan)]
      (doseq [[k text] changes]
        (when-let [sprite (get labels k)]
          (t/set-label-text! sprite text)))
      (swap! !mounted assoc :texts (merge texts changes) :plan plan)))
  (sync-blueprints! world)
  (when-let [{:keys [root kit-lights]} @!mounted] (t/sync-glows! root kit-lights))
  (play-snap! world now)
  nil)

(defn mount!
  "Build the plan's meshes and labels into `scene` once. No-op if already
   mounted."
  [scene world now]
  (when-not @!mounted
    (let [plan   (with-fixtures world (p/scene-plan world now))
          root   (t/group {:name "pm-mirrors"})
          labels (reduce (fn [acc item]
                            (if-let [sprite (mount-item! root item)]
                              (assoc acc (:key item) sprite)
                              acc))
                          {} plan)]
      (t/add! scene root)
      (reset! !mounted {:root root :labels labels :texts (p/label-texts plan) :plan plan
                        :kit-lights (t/kit-light-sources scene (:kit-lights world))
                        :snap-worm (snap-worm! root)})
      (update! world now)))
  nil)

(defn summary []
  (when-let [{:keys [plan]} @!mounted]
    (p/plan-summary plan)))

(defn mounted-root []
  (:root @!mounted))

(defn current-plan []
  (:plan @!mounted))
