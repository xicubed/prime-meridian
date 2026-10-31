(ns pm.core
  "Entry point. The world is the purchased Hiroshi's Island kit (see `pm.kit`):
   the kit renders the estate, and the game layers its HUD, keys, rules and
   spatial brook on top, using the kit's own scene and THREE. Without the kit (a
   public clone), the week-1 scaffold runs instead: a cube over a ground plane.
   World state lives in one atom; the render loop reads it."
  (:require [pm.three :as t]
            [pm.audio :as audio]
            [pm.brook :as brook]
            [pm.kit :as kit]
            [pm.world :as world]))

(defonce !world (atom (merge {:t 0 :spin 0.6} (world/load-estate world/estate-edn))))

(defonce !gfx (atom nil)) ; {:renderer :scene :camera :cube}

(defn build-scene! []
  (let [scene (t/scene)
        cube  (t/box {:size [1.5 1.5 1.5] :color 0xe07a5f :pos [0 0.75 0]})]
    (t/add! scene (t/lights) (t/ground {}) cube)
    {:scene scene :cube cube}))

(defn tick! [dt]
  (swap! !world update :t + dt)
  (when-let [{:keys [renderer scene camera ^js cube]} @!gfx]
    (let [{:keys [t spin]} @!world
          hue (mod (* t 0.08) 1)]                       ; full rainbow every ~12 s
      (set! (.-y (.-rotation cube)) (* t spin))
      (set! (.-x (.-rotation cube)) (* t spin 0.4))
      (.setHSL (.-color (.-material cube)) hue 0.6 0.55)
      (t/render! renderer scene camera))))

(defonce !last (atom nil))
(defonce !frames (atom 0))

(defn hud! [text]
  (when-let [el (js/document.getElementById "hud")]
    (set! (.-textContent el) text)))

(defonce !backend (atom "init…"))
(defonce !mode (atom "loading")) ; "estate" (kit) or "scaffold"

(defn- hud-mirrors-suffix []
  (let [w @!world]
    (str
      (apply str
        (for [id (get-in w [:hud :mirrors])
              :let [line (world/status-line w id (js/Date.now))]
              :when line]
          (str " · " line)))
      (when-let [h (kit/handle)]
        (when-let [b (brook/status h)] (str " · " b))))))

(defn frame [now]
  (let [dt (if-let [l @!last] (/ (- now l) 1000) 0)]
    (reset! !last now)
    (tick! (min dt 0.1))
    (when (zero? (mod (swap! !frames inc) 30))
      (hud! (str "Prime Meridian · " @!mode " · " @!backend " · frame " @!frames
                 " · t=" (.toFixed (:t @!world) 1) "s" (hud-mirrors-suffix))))
    (js/requestAnimationFrame frame)))

(defonce !brook-on (atom false))

(defn- brook-source []
  (some-> (js/document.getElementById "brook-source") .-value keyword))

(defn- brook-start! []
  (case (brook-source)
    :synth (audio/start!)
    :file  (audio/start-file!)
    :both  (do (audio/start!) (audio/start-file!))))

(defn- brook-stop! [] (audio/stop!) (audio/stop-file!))

(defn wire-brook-ui! []
  ;; default the source picker to "file" when the loop exists
  (audio/file-available?
    (fn [ok?]
      (when-let [sel (js/document.getElementById "brook-source")]
        (set! (.-value sel) (if ok? "file" "synth"))
        (set! (.-title sel) (if ok? "brook.mp3 found" "no brook.mp3 in public/assets/audio/")))))
  (when-let [btn (js/document.getElementById "brook-toggle")]
    (.addEventListener btn "click"
      (fn []
        (if @!brook-on
          (do (brook-stop!) (reset! !brook-on false) (set! (.-textContent btn) "▶ brook"))
          (do (brook-start!) (reset! !brook-on true) (set! (.-textContent btn) "■ brook"))))))
  (when-let [sel (js/document.getElementById "brook-source")]
    (.addEventListener sel "change" (fn [] (when @!brook-on (brook-stop!) (brook-start!)))))
  (doseq [[id k] [["brook-flow" :flow] ["brook-depth" :depth]]]
    (when-let [el (js/document.getElementById id)]
      (.addEventListener el "input"
        (fn [e] (let [v (js/parseFloat (.. e -target -value))]
                  (audio/set-param! k v)
                  (when (= k :flow) (audio/set-file-volume! v))))))))

(defn on-key! [^js e]
  (when-not (or (.-repeat e) (.-ctrlKey e) (.-metaKey e) (.-altKey e)
                (some-> (.-target e) .-tagName #{"INPUT" "TEXTAREA" "SELECT"}))
    (when-let [ev (world/key-event @!world (.-key e) (js/Date.now))]
      (swap! !world world/chore-done ev)
      (js/console.log (pr-str (last (:log @!world)))))))

(defn wire-kit-brook-ui!
  "With the kit: the brook is the recorded loop placed on the kit's water
   (pm.brook). Flow sets the pitch; synth/depth only apply to the scaffold."
  [h]
  (doseq [id ["brook-source" "brook-depth-label"]]
    (when-let [^js el (js/document.getElementById id)]
      (set! (.. el -style -display) "none")))
  (when-let [btn (js/document.getElementById "brook-toggle")]
    (.addEventListener btn "click"
      (fn [^js e]
        (.stopPropagation e)
        (if (brook/playing?)
          (do (brook/stop!) (set! (.-textContent btn) "▶ brook"))
          (do (brook/play! h (:sounds @!world)) (set! (.-textContent btn) "■ brook"))))))
  (when-let [el (js/document.getElementById "brook-flow")]
    (set! (.-value el) "0.33")
    (.addEventListener el "input"
      (fn [^js e] (brook/set-flow! (js/parseFloat (.. e -target -value)))))))

(defn start-kit!
  "The kit's scene is live: show the game's layer on top of it."
  [h]
  (reset! !mode "estate")
  (reset! !backend (kit/backend-name h))
  (js/console.log "Prime Meridian: running on the kit's estate," @!backend)
  (wire-kit-brook-ui! h)
  (js/requestAnimationFrame frame))

(defn start-scaffold! []
  (reset! !mode "scaffold")
  (wire-brook-ui!)
  (let [renderer (t/renderer)
        camera   (t/camera)
        {:keys [scene cube]} (build-scene!)]
    (t/on-resize! camera renderer)
    (reset! !gfx {:renderer renderer :scene scene :camera camera :cube cube})
    (t/init-renderer! renderer
                      (fn [backend]
                        (reset! !backend backend)
                        (js/console.log "Prime Meridian: renderer ready on" backend)
                        (js/requestAnimationFrame frame)))))

(defn init! []
  ;; Capture phase: the kit's own controls handle keys too and may stop them.
  (.addEventListener js/window "keydown" (fn [^js e] (on-key! e)) true)
  (kit/load! start-kit! start-scaffold!))

(defn reload!
  "shadow-cljs after-load hook: rebuild the scaffold scene, keep renderer/camera/world.
   (With the kit there's nothing to rebuild: the kit owns its scene.)"
  []
  (when-let [{:keys [renderer camera]} @!gfx]
    (let [{:keys [scene cube]} (build-scene!)]
      (swap! !gfx assoc :scene scene :cube cube)
      (js/console.log "Prime Meridian: reloaded")
      (t/render! renderer scene camera))))
