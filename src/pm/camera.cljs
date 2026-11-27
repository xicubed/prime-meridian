(ns pm.camera
  "Remember the player's camera across page reloads (TASKS U-1). The kit's
   rig (`window.hiroshi.rig`) is a camera plus orbit controls with a
   `.target`; we save the camera position and the orbit target to
   localStorage every couple of seconds and put them back once the kit is
   ready. A `?view=<id>` in the URL (the kit's own preset) wins over the
   saved camera. Pure rules first (tested); interop at the bottom.")

(def storage-key "pm.camera.v1")
(def save-every-ms 2000)
(def max-coord 2000)   ; metres: anything beyond is junk, not a camera

(def show-defaults
  "Show me (placement-v1) defaults for `fly-decision`/`ghost-flight`,
   overridden by the estate's `:build-rules :show-me`."
  {:near-m 10 :quiet-ms 2000 :lift-m 0.6 :back 1.4 :low 0.5})

(def center-m
  "Metres: a new ghost this close (xz) to the screen's centre point is
   \"here\" (placement-v2) -- no flight."
  1.0)

(defn- finite? [x] (and (number? x) (js/isFinite x) (<= (js/Math.abs x) max-coord)))
(defn- vec3? [v] (and (vector? v) (= 3 (count v)) (every? finite? v)))

;; -- Show me (placement-v1): fly the camera to a new ghost ------------------

(defn- nums3? [v] (and (vector? v) (= 3 (count v)) (every? number? v)))
(defn- finite-nums3? [v] (and (nums3? v) (every? js/isFinite v)))

(defn on-screen?
  "True iff `ndc` is a vector of 3 finite numbers, each within [-1 1] (a
   point behind the camera projects to z > 1, so that excludes it too)."
  [ndc]
  (boolean (and (finite-nums3? ndc) (every? (fn [v] (<= -1 v 1)) ndc))))

(defn- dist-xz [[ax _ az] [bx _ bz]]
  (js/Math.sqrt (+ (* (- ax bx) (- ax bx)) (* (- az bz) (- az bz)))))

(defn fly-decision
  "Show me (placement-v1, placement-v2): whether the camera should fly to
   a new ghost at `m`'s `:ghost`. `show` is merged over `show-defaults`
   (nil is fine). The first match wins: no sane `:ghost` -> :no-ghost; a
   trial card open -> :card-open; the player moved the camera within
   `:quiet-ms` -> :player-moving; on screen and within `center-m` (xz) of
   `m`'s `:center` -> :at-center (placement-v2: a here build, already in
   the middle of the screen, never flies); already on screen and within
   `:near-m` of the camera -> :in-view; else :fly."
  [show {:keys [ghost camera on-screen? card-open? last-move-ms now-ms center]}]
  (let [{:keys [near-m quiet-ms]} (merge show-defaults show)]
    (cond
      (not (finite-nums3? ghost)) :no-ghost
      card-open? :card-open
      (and (number? last-move-ms) (< (- now-ms last-move-ms) quiet-ms)) :player-moving
      (and on-screen? (finite-nums3? center) (<= (dist-xz ghost center) center-m)) :at-center
      (and on-screen? (nums3? camera)
           (<= (js/Math.sqrt (reduce + (map (fn [a b] (* (- a b) (- a b))) camera ghost))) near-m))
      :in-view
      :else :fly)))

(defn flight
  "The CREW key's framing maths (pm.core's old `fly!`), moved here unchanged
   so the CREW key and a new ghost's camera share one flight: {:target
   [x y z] :offset [dx dy dz]}, or nil unless `at` and `offset` are 3
   numbers each. The camera always comes in from the creek's side."
  [at lift back offset]
  (when (and (nums3? at) (nums3? offset))
    (let [[x y z] at
          [ox oy oz] offset
          ox' (if (pos? x) (- (js/Math.abs ox)) (js/Math.abs ox))]
      {:target [x (+ y lift) z] :offset [(* back ox') (* back oy) (* back oz)]})))

(defn ghost-flight
  "`flight` framed for a new ghost at `at` (placement-v1): from the side and
   lower, under a canopy, per `show`'s `:lift-m`/`:back`/`:low` (merged over
   `show-defaults`)."
  [at offset show]
  (let [{:keys [lift-m back low]} (merge show-defaults show)]
    (when (nums3? offset)
      (let [[ox oy oz] offset]
        (flight at lift-m back [ox (* low oy) oz])))))

(defn encode
  "The text to store for a camera at `pos` looking at `target` (both [x y z])."
  [pos target]
  (js/JSON.stringify (clj->js {:v 1 :pos pos :target target})))

(defn decode
  "{:pos [x y z] :target [x y z]} from stored text, or nil for anything that
   isn't a sane saved camera (missing, old format, NaN, absurd, eye on target)."
  [text]
  (when (string? text)
    (let [d (try (js->clj (js/JSON.parse text) :keywordize-keys true) (catch :default _ nil))]
      (when (and (map? d) (= 1 (:v d)))
        (let [pos (vec (:pos d)) target (vec (:target d))]
          (when (and (vec3? pos) (vec3? target)
                     (> (reduce + (map (fn [a b] (* (- a b) (- a b))) pos target)) 1e-6))
            {:pos pos :target target}))))))

(defn changed?
  "True when the camera moved more than ~1 cm from what was last saved."
  [last pos target]
  (or (nil? last)
      (some (fn [[a b]] (> (js/Math.abs (- a b)) 0.01))
            (map vector (concat (:pos last) (:target last)) (concat pos target)))))

(defn restore?
  "Restore the saved camera unless the URL asks for a kit view."
  [search]
  (not (.has (js/URLSearchParams. (or search "")) "view")))

;; -- Interop (the kit's rig) ----------------------------------------------------

(defonce ^:private !last (atom {:at 0 :saved nil}))

(defn- orbit [^js h] (when-let [^js rig (.-rig h)] (.-orbit rig)))
(defn- free-mode? [^js h] (when-let [^js rig (.-rig h)] (when-let [^js st (.-state rig)] (.-free st))))

(defn- current [^js h]
  (when-let [^js o (orbit h)]
    (let [^js p (.. h -camera -position) ^js t (.-target o)]
      {:pos [(.-x p) (.-y p) (.-z p)] :target [(.-x t) (.-y t) (.-z t)]})))

(defn frame!
  "Points the kit's camera at `target` from `target` + `offset` (both
   [x y z], metres): the Construct's last beat uses it so the player sees
   their agent's building land. No-op without the kit's rig."
  [^js h target offset]
  (when-let [^js o (orbit h)]
    (when (and (vec3? target) (vec3? offset))
      (let [[tx ty tz] target [dx dy dz] offset]
        (.set (.-target o) tx ty tz)
        (.set (.-position (.-camera h)) (+ tx dx) (+ ty dy) (+ tz dz))
        (.update o)))))

(defn follow!
  "Keeps the camera on a moving `target` [x y z]: moves the orbit target and
   the camera by the same step, so the view keeps its angle and distance
   while the thing walks. No-op without the kit's rig."
  [^js h target]
  (when-let [^js o (orbit h)]
    (when (vec3? target)
      (let [^js t (.-target o) ^js p (.-position (.-camera h))
            [tx ty tz] target
            dx (- tx (.-x t)) dy (- ty (.-y t)) dz (- tz (.-z t))]
        (.set t tx ty tz)
        (.set p (+ (.-x p) dx) (+ (.-y p) dy) (+ (.-z p) dz))
        (.update o)))))

(defn restore!
  "Put the saved camera back (call once the kit is ready). Returns true if it did."
  [^js h]
  (let [saved (decode (try (.getItem js/localStorage storage-key) (catch :default _ nil)))
        ^js o (orbit h)]
    (if (and saved o (restore? (.. js/window -location -search))
             (not (free-mode? h)))
      (let [[px py pz] (:pos saved) [tx ty tz] (:target saved) ^js cam (.-camera h)]
        (.set (.-position cam) px py pz)
        (.set (.-target o) tx ty tz)
        (.lookAt cam (.-target o))
        (.update o 0)
        (reset! !last {:at 0 :saved saved})
        true)
      false)))

(defn save-tick!
  "Every save-every-ms, store the camera if it moved. Cheap; call each frame."
  [^js h now-ms]
  (let [{:keys [at saved]} @!last]
    (when (>= (- now-ms at) save-every-ms)
      (swap! !last assoc :at now-ms)
      (when-let [{:keys [pos target]} (current h)]
        (when (and (vec3? pos) (vec3? target) (changed? saved pos target))
          (try (.setItem js/localStorage storage-key (encode pos target)) (catch :default _ nil))
          (swap! !last assoc :saved {:pos pos :target target}))))))
