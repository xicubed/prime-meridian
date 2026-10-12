(ns pm.core
  "Entry point. Week 1: a lit, flat-shaded cube spinning over a ground plane.
   World state lives in one atom; the render loop reads it. Hot reload keeps the
   renderer and rebuilds the scene."
  (:require [pm.three :as t]))

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
    (let [{:keys [t spin]} @!world]
      (set! (.-y (.-rotation cube)) (* t spin))
      (set! (.-x (.-rotation cube)) (* t spin 0.4))
      (t/render! renderer scene camera))))

(defonce !last (atom nil))
(defonce !frames (atom 0))

(defn hud! [text]
  (when-let [el (js/document.getElementById "hud")]
    (set! (.-textContent el) text)))

(defn frame [now]
  (let [dt (if-let [l @!last] (/ (- now l) 1000) 0)]
    (reset! !last now)
    (tick! (min dt 0.1))
    (when (zero? (mod (swap! !frames inc) 30))
      (hud! (str "Prime Meridian · week 1 scaffold · frame " @!frames
                 " · t=" (.toFixed (:t @!world) 1) "s")))
    (js/requestAnimationFrame frame)))

(defn init! []
  (let [renderer (t/renderer)
        camera   (t/camera)
        {:keys [scene cube]} (build-scene!)]
    (t/on-resize! camera renderer)
    (reset! !gfx {:renderer renderer :scene scene :camera camera :cube cube})
    (js/requestAnimationFrame frame)
    (js/console.log "Prime Meridian: init")))

(defn reload!
  "shadow-cljs after-load hook: rebuild the scene, keep renderer/camera/world."
  []
  (when-let [{:keys [renderer camera]} @!gfx]
    (let [{:keys [scene cube]} (build-scene!)]
      (swap! !gfx assoc :scene scene :cube cube)
      (js/console.log "Prime Meridian: reloaded")
      (t/render! renderer scene camera))))
