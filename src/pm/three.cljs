(ns pm.three
  "Thin, reviewed wrapper over three.js interop. Everything in the game that touches
   three.js should go through here so interop mistakes live in one file.

   Renderer: WebGPURenderer from `three/webgpu` (three r186). It uses WebGPU when the
   browser has it and falls back to WebGL2 on its own. Initialization is async —
   call `init-renderer!` and start rendering in its callback. Materials from
   `three/webgpu` are the same classes as classic three (MeshStandardMaterial etc.),
   plus TSL node materials become available for water/wind later.

   The kit's scene is built with the kit's own THREE, not this npm module — two
   three.js copies on one page break materials. `use-three!` points every
   constructor below at whichever THREE is current; `defonce` keeps that choice
   across hot reloads."
  (:require ["three/webgpu" :as THREE]))

(defonce ^:private !three (atom THREE))

(defn three
  "The THREE currently in use: the kit's, if `use-three!` was called with one,
   else this wrapper's own npm `three/webgpu`."
  []
  @!three)

(defn use-three!
  "Switch every constructor in this wrapper to `T`, or back to the npm module
   when `T` is nil. Returns the now-current THREE."
  [T]
  (reset! !three (or T THREE)))

(defn renderer
  "Create (but do not yet initialize) a WebGPU/WebGL2 renderer attached to document.body.
   Call `init-renderer!` before the first render."
  []
  (let [^js T (three)
        ^js Renderer (.-WebGPURenderer T)
        ^js r (Renderer. #js {:antialias true})]
    (.setPixelRatio r (min 2 js/window.devicePixelRatio))
    (.setSize r js/window.innerWidth js/window.innerHeight)
    (set! (.-enabled (.-shadowMap r)) true)
    (.appendChild js/document.body (.-domElement r))
    r))

(defn init-renderer!
  "Async backend init. Calls (on-ready backend-name) when the renderer can draw."
  [^js renderer on-ready]
  (-> (.init renderer)
      (.then (fn []
               (let [^js backend (.-backend renderer)
                     name (if (.-isWebGPUBackend backend) "webgpu" "webgl2")]
                 (on-ready name))))
      (.catch (fn [e] (js/console.error "renderer init failed" e)))))

(defn scene
  ([] (scene 0x1b1f2a))
  ([bg-color]
   (let [^js T (three)
         ^js Scene (.-Scene T)
         ^js Color (.-Color T)
         ^js s (Scene.)]
     (set! (.-background s) (Color. bg-color))
     s)))

(defn camera
  "Perspective camera looking at the origin from a diorama angle."
  []
  (let [^js T (three)
        ^js Camera (.-PerspectiveCamera T)
        ^js c (Camera. 45 (/ js/window.innerWidth js/window.innerHeight) 0.1 200)]
    (.set (.-position c) 6 6 10)
    (.lookAt c 0 0 0)
    c))

(defn lights
  "Warm key + soft fill. Returns a seq of lights to add to the scene."
  []
  (let [^js T (three)
        ^js Directional (.-DirectionalLight T)
        ^js Hemisphere (.-HemisphereLight T)
        ^js sun (Directional. 0xfff1d6 2.2)
        ^js sky (Hemisphere. 0xbcd8ff 0x3a2d22 0.8)]
    (.set (.-position sun) 5 10 6)
    (set! (.-castShadow sun) true)
    [sun sky]))

(defn box
  "Flat-shaded box mesh. Opts: :size [w h d], :color, :pos [x y z]."
  [{:keys [size color pos] :or {size [1 1 1] color 0xe07a5f pos [0 0 0]}}]
  (let [[w h d] size [x y z] pos
        ^js T (three)
        ^js Mesh (.-Mesh T)
        ^js BoxGeometry (.-BoxGeometry T)
        ^js StandardMaterial (.-MeshStandardMaterial T)
        ^js m (Mesh. (BoxGeometry. w h d)
                     (StandardMaterial. #js {:color color :flatShading true}))]
    (.set (.-position m) x y z)
    (set! (.-castShadow m) true)
    (set! (.-receiveShadow m) true)
    m))

(defn ground
  [{:keys [size color] :or {size 20 color 0x81b29a}}]
  (let [^js T (three)
        ^js Mesh (.-Mesh T)
        ^js PlaneGeometry (.-PlaneGeometry T)
        ^js StandardMaterial (.-MeshStandardMaterial T)
        ^js m (Mesh. (PlaneGeometry. size size)
                     (StandardMaterial. #js {:color color}))]
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

(defn group
  "A THREE.Group. Opts: :name (string), :pos [x y z] (default [0 0 0]),
   :rot [rx ry rz] in radians (default [0 0 0])."
  [{:keys [name pos rot] :or {pos [0 0 0] rot [0 0 0]}}]
  (let [^js T (three)
        ^js Group (.-Group T)
        ^js g (Group.)
        [x y z] pos
        [rx ry rz] rot]
    (when name (set! (.-name g) name))
    (.set (.-position g) x y z)
    (.set (.-rotation g) rx ry rz)
    g))

(defn stand-in
  "Flat-shaded placeholder mesh. :shape in pm.plan/shapes, :size [sx sy sz]
   metres, :color an int, :pos [x y z] the mesh's centre (parent frame),
   :rot [rx ry rz] in radians (default [0 0 0]), Euler XYZ."
  [{:keys [shape size color pos rot] :or {rot [0 0 0]}}]
  (let [[sx sy sz] size [x y z] pos [rx ry rz] rot
        ^js T (three)
        ^js Mesh (.-Mesh T)
        ^js BoxGeometry (.-BoxGeometry T)
        ^js SphereGeometry (.-SphereGeometry T)
        ^js CylinderGeometry (.-CylinderGeometry T)
        ^js ConeGeometry (.-ConeGeometry T)
        ^js CapsuleGeometry (.-CapsuleGeometry T)
        ^js StandardMaterial (.-MeshStandardMaterial T)
        ^js geom (case shape
                   :sphere   (SphereGeometry. 0.5 12 8)
                   :cylinder (CylinderGeometry. 0.5 0.5 1 12)
                   :cone     (ConeGeometry. 0.5 1 12)
                   :capsule  (let [^js g (CapsuleGeometry. (/ sy 2) (max 0 (- sx sy)) 4 8)]
                               (.rotateZ g (/ js/Math.PI 2))
                               g)
                   (BoxGeometry. sx sy sz))
        ^js m (Mesh. geom (StandardMaterial. #js {:color color :flatShading true}))]
    (when (#{:sphere :cylinder :cone} shape)
      (.set (.-scale m) sx sy sz))
    (.set (.-position m) x y z)
    (.set (.-rotation m) rx ry rz)
    (set! (.-castShadow m) true)
    (set! (.-receiveShadow m) true)
    m))

(defn- hex->css [n]
  (str "#" (.padStart (.toString (bit-and n 0xffffff) 16) 6 "0")))

(defn set-label-text!
  "Redraw `sprite`'s canvas with `text` (a rounded paper rectangle with the
   text centred in ink), or hide the sprite when `text` is empty/nil. Never
   makes a new texture or canvas."
  [^js sprite text]
  (let [^js data   (.-userData sprite)
        ^js canvas (.-canvas data)
        ^js ctx    (.getContext canvas "2d")
        ^js tex    (.-texture data)
        w          (.-width canvas)
        h          (.-height canvas)
        paper      (hex->css (.-paper data))
        ink        (hex->css (.-ink data))]
    (if (or (nil? text) (= text ""))
      (set! (.-visible sprite) false)
      (do
        (.clearRect ctx 0 0 w h)
        (.beginPath ctx)
        (set! (.-fillStyle ctx) paper)
        (.roundRect ctx 0 0 w h 24)
        (.fill ctx)
        (set! (.-fillStyle ctx) ink)
        (set! (.-textAlign ctx) "center")
        (set! (.-textBaseline ctx) "middle")
        (loop [size 56]
          (set! (.-font ctx) (str "bold " size "px sans-serif"))
          (if (and (> size 12) (> (.-width (.measureText ctx text)) 472))
            (recur (dec size))
            nil))
        (.fillText ctx text (/ w 2) (/ h 2))
        (set! (.-visible sprite) true)
        (set! (.-needsUpdate tex) true)))
    sprite))

(defn label
  "A Sprite that always faces the camera, showing `text` on a rounded-rect
   paper background. Opts: :text (default \"\"), :height metres (default 0.3),
   :paper :ink ints, :pos [x y z]."
  [{:keys [text height paper ink pos] :or {text "" height 0.3}}]
  (let [[x y z] pos
        width      (* 4 height)
        ^js T      (three)
        ^js Sprite (.-Sprite T)
        ^js SpriteMaterial (.-SpriteMaterial T)
        ^js CanvasTexture (.-CanvasTexture T)
        ^js canvas (js/document.createElement "canvas")
        _          (set! (.-width canvas) 512)
        _          (set! (.-height canvas) 128)
        ^js tex    (CanvasTexture. canvas)
        _          (set! (.-colorSpace tex) (.-SRGBColorSpace T))
        ^js mat    (SpriteMaterial. #js {:map tex :transparent true :depthWrite false})
        ^js s      (Sprite. mat)]
    (.set (.-scale s) width height 1)
    (.set (.-position s) x y z)
    (set! (.-userData s) #js {:canvas canvas :texture tex :paper paper :ink ink})
    (set-label-text! s text)
    s))

(defn pick-names
  "Raycast from `camera` through NDC [x y] against `root`: a vector, nearest
   hit first, of each hit's name chain (object, then parents up to and
   including root, skipping blank names). Never throws."
  [^js root ^js camera [x y]]
  (try
    (let [^js T (three)
          ^js Raycaster (.-Raycaster T)
          ^js Vector2 (.-Vector2 T)
          ^js rc (Raycaster.)]
      (.setFromCamera rc (Vector2. x y) camera)
      (vec
        (for [^js hit (.intersectObject rc root true)]
          (loop [^js o (.-object hit) chain []]
            (if (nil? o)
              chain
              (let [chain' (if (not-empty (.-name o)) (conj chain (.-name o)) chain)]
                (if (identical? o root)
                  chain'
                  (recur (.-parent o) chain'))))))))
    (catch :default _ [])))

(defn drop-ray
  "Cast a ray straight down at (x, z) from height `from` through `root` and
   return the first `n` hits as [[y name] ...] (y rounded to cm). Sprites need
   the camera to be hit-tested, so it's required. A verification tool: lets
   the agent learn ground heights without seeing the canvas."
  [^js root ^js camera x z from n]
  (let [T @!three
        ^js rc (new (.-Raycaster T)
                    (new (.-Vector3 T) x from z)
                    (new (.-Vector3 T) 0 -1 0))]
    (set! (.-camera rc) camera)
    (->> (.intersectObject rc root true)
         (map (fn [^js hit]
                (let [^js o (.-object hit) ^js m (.-material o)]
                  [(/ (js/Math.round (* 100 (.. hit -point -y))) 100)
                   (str (or (not-empty (.-name o)) (.-type o))
                        (when (and m (not-empty (.-name m))) (str "/" (.-name m))))])))
         (take n)
         vec)))
