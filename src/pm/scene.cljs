(ns pm.scene
  "Mounts the plan's items into the kit's (or scaffold's) scene, through
   `pm.three` only. Never touches `js/THREE`, the npm module, or
   `window.hiroshi` directly."
  (:require [pm.three :as t]
            [pm.plan :as p]
            [pm.blueprint :as bp]
            [pm.content :as c]
            [pm.materials :as mats]
            [pm.models :as md]
            [clojure.string :as str]))

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

(defn css-color
  "A palette key as a CSS colour (TASKS T-14, the trial card: the in-world
   plate's own colours, no hex literals in feature code)."
  [kw]
  (str "#" (.padStart (.toString (color kw) 16) 6 "0")))

(defonce ^:private !mounted (atom nil))

(defn- label-opts [label-kind pos text]
  (let [[paper ink] (if (= label-kind :sign)
                       [:sign-paper :sign-ink]
                       [:bubble-paper :bubble-ink])]
    ;; a two-line sign (a blueprint's) gets a taller plate, so each line keeps
    ;; the one-line font size, and rises by the extra so it never sinks into its item
    (let [n          (if (string? text) (count (str/split text #"\n")) 1)
          [cw ch]    (:canvas plate)
          k          (+ 1 (* 0.65 (dec n)))   ; each extra line adds 0.65 of a plate: lines sit close
          ch'        (js/Math.round (* ch k))
          height'    (* (:height plate) k)
          [x y z]    pos]
      (assoc plate
             :text   text
             :paper  (color paper)
             :ink    (color ink)
             :border (color ink)
             :canvas [cw ch']
             :height height'
             :pos    (if pos [x (+ y (/ (- height' (:height plate)) 2)) z] pos)))))

(defonce ^:private !model-warned (atom #{}))

(defn- model-warn! [token msg]
  (when-not (contains? @!model-warned token)
    (swap! !model-warned conj token)
    (js/console.warn msg)))

(defn- look-mesh-name?
  "True for a mesh name this namespace gave a `key`'s look part: `\"<key>/<i>\"`."
  [key name]
  (let [prefix (str (pr-str key) "/")]
    (and (string? name) (str/starts-with? name prefix)
         (boolean (re-matches #"\d+" (subs name (count prefix)))))))

(defn- swap-in-model!
  "A prop's `:model` (TASKS T-18) arriving: replaces `item`'s look meshes,
   still parented under its own group (same name/key, position, yaw), with
   the loaded model's -- so clicks on it still resolve to its key -- then
   applies any named material overrides and re-glows. Keeps the `:look`
   (and warns once) when `g` is gone, or the model has no usable meshes."
  [item gltf]
  (let [key (:key item)]
    (when-let [^js g (t/by-name (:root @!mounted) (pr-str key))]
      (when (.-parent g)
        (let [meshes (t/model-meshes gltf)]
          (if (or (nil? meshes) (zero? (.-length meshes)))
            (model-warn! [:model-empty key] (str "pm.scene: model for " (pr-str key) " had no usable meshes"))
            (let [^js holder (t/group {})]
              (doseq [^js m meshes] (t/add! holder m))
              (let [top      (t/top-y holder)
                    kit-mats (:kit-mats @!mounted)]
                (doseq [^js old (vec (.-children g))]
                  (when (look-mesh-name? key (.-name old))
                    (.removeFromParent old)
                    (when-let [^js geo (.-geometry old)] (.dispose geo))
                    (let [^js m (.-material old)]
                      (when m
                        (if (array? m) (doseq [^js mm m] (.dispose mm)) (.dispose m))))))
                (doseq [[i ^js m] (map-indexed vector (array-seq meshes))]
                  (set! (.-name m) (str (pr-str key) "/" i))
                  (let [^js mat  (.-material m)
                        mat-name (when (and mat (not (array? mat))) (.-name mat))
                        o        (md/material-override mat-name (get-in item [:model :materials]))]
                    (when-let [k (:kit o)]
                      (if-let [^js km (get (:index kit-mats) k)]
                        (set! (.-material m) (.clone km))
                        (model-warn! [:kit-missing k] (str "pm.scene: kit material not found: " k))))
                    (when (:glow o)
                      (set! (.. m -userData -glow) true)
                      (when (false? (:light o)) (set! (.. m -userData -glowLight) false))
                      (when (:light-m o) (set! (.. m -userData -glowRange) (:light-m o)))))
                  (t/add! g m))
                (when (:ghost? item) (t/ghost! g (:tint item)))
                (t/light-glows! g (:kit-lights @!mounted))
                (when-let [sprite (get (:labels @!mounted) key)]
                  (when-let [new-at (p/model-label-at item top)]
                    (let [dy (- (second new-at) (second (:label-at item)))]
                      (when (pos? dy) (t/add-y! sprite dy)))))))))))))

(defn- mount-item! [root item kit-mats]
  (let [{:keys [key at yaw parts label label-at text ghost? tint model]} item
        props? (mats/props-item? item)
        ^js item-group (t/group {:name (pr-str key) :pos at :rot [0 (p/radians yaw) 0]})]
    (doseq [[i part] (map-indexed vector parts)]
      (let [^js mesh (t/stand-in
                       (cond-> {:shape (:shape part)
                                :size  (:size part)
                                :color (color (:color part))
                                :pos   (:at part)
                                :rot   (mapv p/radians (:rot part))}
                         props? (assoc :bevel (when (= :box (:shape part)) (mats/bevel-radius (:size part)))
                                       :kit (mats/material-for part (:kit-materials kit-mats) (:index kit-mats)))))]
        (set! (.-name mesh) (str (pr-str key) "/" i))
        (when (:glow part) (set! (.. mesh -userData -glow) true))
        (t/add! item-group mesh)))
    (when ghost? (t/ghost! item-group tint))
    (t/add! root item-group)
    (when-let [{:keys [url]} model]
      (-> (t/load-model url)
          (.then (fn [gltf] (swap-in-model! item gltf)))
          (.catch (fn [_] (model-warn! [:load-failed url] (str "pm.scene: model failed to load: " url))))))
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

(def ^:private building-drop-height-m
  "Metres above its resting place the loaded program's building starts,
   falling from the white sky (GDD §2.6, §2.7) once your own agent program
   loads."
  15.0)

(def ^:private building-drop-key
  "The workshop stands for your own agent's project (GDD §2.4, §2.6, §2.7):
   this repo. It's the building that drops out of the white sky once your
   own agent program loads."
  [:workshop :box])

(defn- building-drop-step!
  "Mid-drop, at progress `p` (0..1) of `pm.plan/building-drop-ms`: the
   building falls from `building-drop-height-m` above `at` down to it."
  [group at p]
  (let [[x y z] at
        fall (* building-drop-height-m (max 0 (- 1 p)))]
    (when group (t/set-pos! group [x (+ y fall) z]))))

(defn play-building-drop!
  "The Construct's building drop on screen (GDD §2.6, §2.7): while
   `pm.plan/building-drop-pose` says one is live, the workshop's group
   falls from high in the white sky down to its resting place; put back at
   rest once the pose is nil (nothing live, or it already finished).
   Mutates three.js objects only through `pm.three`; never touches the
   scene plan itself (its positions never follow world state)."
  [world now]
  (when-let [{:keys [root plan]} @!mounted]
    (let [^js group (t/by-name root (pr-str building-drop-key))
          at        (:at (first (filter #(= building-drop-key (:key %)) plan)))
          p         (p/building-drop-pose world now)]
      (when (and group at)
        (if p
          (building-drop-step! group at p)
          (t/set-pos! group at)))))
  nil)

(defn- with-fixtures [world plan]
  (->> plan (with-lobby world) (with-workshop world) (with-mailbox world) (with-phone-booth world) (with-blueprints world)))

(defn- blueprint-key [bp] [:blueprint (:id bp)])

(defn- bp-ids-in
  "Every id a `[:blueprint id]` item in `plan` carries."
  [plan]
  (set (keep (fn [item] (let [k (:key item)] (when (and (vector? k) (= :blueprint (first k))) (second k)))) plan)))

(defn- sync-blueprint-draw!
  "A blueprint arriving live or dropping out of `state/blueprints.edn`
   (TASKS T-13b, `bp/drawable-changes`) has to be drawn or undrawn for the
   first time -- never a remount, and never anything else in the scene.
   Runs before `sync-blueprints!`'s status loop, which only ever flips a
   mesh already mounted here."
  [world]
  (when-let [{:keys [root labels texts bp-drawn kit-mats]} @!mounted]
    (let [{:keys [add remove]} (bp/drawable-changes bp-drawn world)]
      (doseq [id remove]
        (when-let [^js g (t/by-name root (pr-str [:blueprint id]))] (t/remove! root g))
        (when-let [sprite (get labels [:blueprint id])] (t/remove! root sprite)))
      (let [labels (apply dissoc labels (map #(vector :blueprint %) remove))
            texts  (apply dissoc texts (map #(vector :blueprint %) remove))
            add-ids (set add)
            items   (filter (fn [item] (let [k (:key item)]
                                          (and (vector? k) (= :blueprint (first k)) (contains? add-ids (second k)))))
                             (into (bp/ghost-plan world) (bp/built-plan world)))
            [labels texts] (reduce (fn [[labs txt] item]
                                      (let [sprite (mount-item! root item kit-mats)]
                                        [(cond-> labs sprite (assoc (:key item) sprite))
                                         (cond-> txt (:text item) (assoc (:key item) (:text item)))]))
                                    [labels texts] items)]
        (swap! !mounted assoc :labels labels :texts texts
               :bp-drawn (-> (apply disj bp-drawn remove) (into add))))))
  nil)

(defn- sync-blueprints!
  "Approving or rejecting a blueprint (GDD §2.1, §4.8) has to change a mesh
   already mounted as a ghost -- after `sync-blueprint-draw!` has drawn or
   undrawn whatever newly needs it, the one other case where this
   namespace creates or removes something after `mount!`. Runs once per
   status change (a pending blueprint going :approved un-ghosts its mesh
   so it reads as real; going :rejected removes its mesh and sign so it
   vanishes)."
  [world]
  (sync-blueprint-draw! world)
  (when-let [{:keys [root labels] bp-status :bp-status} @!mounted]
    (let [prev (or bp-status {})
          now  (into {} (map (fn [bp] [(blueprint-key bp) (:status bp)])) (bp/all world))]
      (doseq [[k status] now
              :when (not= status (get prev k))]
        (case status
          :approved (when-let [^js g (t/by-name root (pr-str k))]
                      (t/unghost! g)
                      ;; a built lamp lights like the kit's own lamps
                      (t/light-glows! g (:kit-lights @!mounted)))
          :real     (do (when-let [^js g (t/by-name root (pr-str k))]
                          (t/unghost! g)
                          (t/light-glows! g (:kit-lights @!mounted)))
                        (when-let [sprite (get labels k)] (t/set-visible! sprite false)))
          ;; A removal preview (TASKS T-15, from :real): a warm-red removal
          ;; ghost, its light off, and a sign saying so -- built fresh when
          ;; the build was mounted real and never had one (a real build
          ;; loaded straight from state/builds.edn), reused (just shown and
          ;; retexted) when it does (one approved, then made real, whose
          ;; sign the :real branch above only hid).
          :removing (when-let [^js g (t/by-name root (pr-str k))]
                      (t/unlight-glows! g)
                      (t/ghost! g :removal)
                      (let [item   (first (filter #(= k (:key %)) (bp/built-plan world)))
                            sprite (or (get labels k)
                                       (let [^js s (t/label (label-opts :sign (:label-at item) (:text item)))]
                                         (t/add! root s)
                                         (swap! !mounted update :labels assoc k s)
                                         (swap! !mounted update :texts assoc k (:text item))
                                         s))]
                        (t/set-visible! sprite true)
                        (t/set-label-text! sprite (:text item))))
          :rejected (do (when-let [^js g (t/by-name root (pr-str k))]
                          (t/unlight-glows! g)
                          (t/remove! root g))
                        (when-let [sprite (get labels k)] (t/remove! root sprite)))
          ;; Undo (TASKS T-14): only a real step back from :approved -- a
          ;; freshly-drawn :pending (no previous status, or previously
          ;; :rejected/:real) is already a ghost, or isn't drawn at all
          :pending  (when (= :approved (get prev k))
                      (when-let [^js g (t/by-name root (pr-str k))]
                        (t/unlight-glows! g)
                        (t/ghost! g)))
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
  (play-building-drop! world now)
  nil)

(defn mount!
  "Build the plan's meshes and labels into `scene` once. No-op if already
   mounted."
  [scene world now]
  (when-not @!mounted
    (let [index    (mats/index-materials (t/material-pairs scene))
          _        (when (seq index)
                     (doseq [nm (mats/missing-names (:kit-materials world) index)]
                       (js/console.warn "pm.scene: kit material not found:" nm)))
          kit-mats {:kit-materials (:kit-materials world) :index index}
          plan   (with-fixtures world (p/scene-plan world now))
          root   (t/group {:name "pm-mirrors"})
          labels (reduce (fn [acc item]
                            (if-let [sprite (mount-item! root item kit-mats)]
                              (assoc acc (:key item) sprite)
                              acc))
                          {} plan)]
      (t/add! scene root)
      (reset! !mounted {:root root :labels labels :texts (p/label-texts plan) :plan plan
                        :kit-lights (t/kit-light-sources scene (:kit-lights world))
                        :kit-mats kit-mats
                        :snap-worm (snap-worm! root)
                        :bp-drawn (bp-ids-in plan)})
      (update! world now)))
  nil)

(defn summary []
  (when-let [{:keys [plan]} @!mounted]
    (p/plan-summary plan)))

(defn mounted-root []
  (:root @!mounted))

(defn label-sprite
  "The three.js label Sprite mounted for `key` (e.g. `[:blueprint id]`), or
   nil (TASKS T-14: the trial card anchors above a blueprint's sign, not
   its item, when the sign has a sprite)."
  [key]
  (get (:labels @!mounted) key))

(defn current-plan []
  (:plan @!mounted))
