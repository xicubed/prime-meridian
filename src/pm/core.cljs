(ns pm.core
  "Entry point. Week 1: a lit, flat-shaded cube spinning over a ground plane.
   World state lives in one atom; the render loop reads it. Hot reload keeps the
   renderer and rebuilds the scene."
  (:require [pm.three :as t]
            [pm.audio :as audio]))

(defonce !world (atom {:t 0 :spin 0.6}))

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

(defn frame [now]
  (let [dt (if-let [l @!last] (/ (- now l) 1000) 0)]
    (reset! !last now)
    (tick! (min dt 0.1))
    (when (zero? (mod (swap! !frames inc) 30))
      (hud! (str "Prime Meridian · week 1 scaffold · " @!backend " · frame " @!frames
                 " · t=" (.toFixed (:t @!world) 1) "s")))
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

(defn init! []
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

(defn reload!
  "shadow-cljs after-load hook: rebuild the scene, keep renderer/camera/world."
  []
  (when-let [{:keys [renderer camera]} @!gfx]
    (let [{:keys [scene cube]} (build-scene!)]
      (swap! !gfx assoc :scene scene :cube cube)
      (js/console.log "Prime Meridian: reloaded")
      (t/render! renderer scene camera))))
