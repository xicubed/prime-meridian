(ns pm.three
  "Thin, reviewed wrapper over three.js interop. Everything in the game that touches
   three.js should go through here so interop mistakes live in one file."
  (:require ["three" :as THREE]))

(defn renderer
  "WebGL renderer sized to the window, attached to document.body."
  []
  (let [^js r (THREE/WebGLRenderer. #js {:antialias true})]
    (.setPixelRatio r (min 2 js/window.devicePixelRatio))
    (.setSize r js/window.innerWidth js/window.innerHeight)
    (set! (.-enabled (.-shadowMap r)) true)
    (.appendChild js/document.body (.-domElement r))
    r))

(defn scene
  ([] (scene 0x1b1f2a))
  ([bg-color]
   (let [^js s (THREE/Scene.)]
     (set! (.-background s) (THREE/Color. bg-color))
     s)))

(defn camera
  "Perspective camera looking at the origin from a diorama angle."
  []
  (let [^js c (THREE/PerspectiveCamera. 45 (/ js/window.innerWidth js/window.innerHeight) 0.1 200)]
    (.set (.-position c) 6 6 10)
    (.lookAt c 0 0 0)
    c))

(defn lights
  "Warm key + soft fill. Returns a seq of lights to add to the scene."
  []
  (let [^js sun (THREE/DirectionalLight. 0xfff1d6 2.2)
        ^js sky (THREE/HemisphereLight. 0xbcd8ff 0x3a2d22 0.8)]
    (.set (.-position sun) 5 10 6)
    (set! (.-castShadow sun) true)
    [sun sky]))

(defn box
  "Flat-shaded box mesh. Opts: :size [w h d], :color, :pos [x y z]."
  [{:keys [size color pos] :or {size [1 1 1] color 0xe07a5f pos [0 0 0]}}]
  (let [[w h d] size [x y z] pos
        ^js m (THREE/Mesh. (THREE/BoxGeometry. w h d)
                       (THREE/MeshStandardMaterial. #js {:color color :flatShading true}))]
    (.set (.-position m) x y z)
    (set! (.-castShadow m) true)
    (set! (.-receiveShadow m) true)
    m))

(defn ground
  [{:keys [size color] :or {size 20 color 0x81b29a}}]
  (let [^js m (THREE/Mesh. (THREE/PlaneGeometry. size size)
                       (THREE/MeshStandardMaterial. #js {:color color}))]
    (set! (.-x (.-rotation m)) (/ (- js/Math.PI) 2))
    (set! (.-receiveShadow m) true)
    m))

(defn add! [^js scene & objs]
  (doseq [o (flatten objs)] (.add scene o))
  scene)

(defn on-resize!
  "Keep camera + renderer in sync with the window."
  [^js camera ^js renderer]
  (.addEventListener js/window "resize"
                     (fn []
                       (set! (.-aspect camera) (/ js/window.innerWidth js/window.innerHeight))
                       (.updateProjectionMatrix camera)
                       (.setSize renderer js/window.innerWidth js/window.innerHeight))))

(defn render! [^js renderer scene camera]
  (.render renderer scene camera))
