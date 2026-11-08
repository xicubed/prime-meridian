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

(defn- finite? [x] (and (number? x) (js/isFinite x) (<= (js/Math.abs x) max-coord)))
(defn- vec3? [v] (and (vector? v) (= 3 (count v)) (every? finite? v)))

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
