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
  (:require ["three/webgpu" :as THREE]
            [clojure.string :as str]))

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

(def ghost-fill 0x1e7bff)  ; blueprint blue (Michael, Nov 22: "neon blue aka blue print")
(def ghost-glow 0x0a5cff)
(def ghost-edge 0x7ff3ff)  ; the neon outline

(defn- meshes-under [^js obj]
  (let [acc #js []]
    (.traverse obj (fn [^js o] (when (.-isMesh o) (.push acc o))))
    acc))

(defn ghost!
  "Makes every mesh under `obj` look like a pending blueprint (GDD §2.1,
   §4.8): a translucent, glowing blueprint-blue fill with neon cyan edges,
   so it reads as proposed, not real, and stays visible on grass (the old
   thin wireframe vanished there, TASKS N-3). Clones each mesh's material
   first (shared stand-in materials are never mutated in place) and keeps
   its real colours in userData for `unghost!`. Returns obj."
  [^js obj]
  (let [^js T (three)]
    (doseq [^js o (meshes-under obj)]
      (let [^js mat (.clone (.-material o))
            ^js em  (.-emissive mat)]
        (set! (.. o -userData -ghostWas)
              #js {:color (when (.-color mat) (.getHex (.-color mat)))
                   :emissive (when em (.getHex em))
                   :ei (.-emissiveIntensity mat)})
        (when (.-color mat) (.setHex (.-color mat) ghost-fill))
        (when em
          (.setHex em ghost-glow)
          (set! (.-emissiveIntensity mat) 0.9))
        (set! (.-transparent mat) true)
        (set! (.-opacity mat) 0.3)
        (set! (.-wireframe mat) false)
        (set! (.-depthWrite mat) false)
        (set! (.-material o) mat)
        (let [^js edges (new (.-EdgesGeometry T) (.-geometry o) 15)
              ^js lmat  (new (.-LineBasicMaterial T) #js {:color ghost-edge :transparent true :opacity 0.95 :depthWrite false})
              ^js lines (new (.-LineSegments T) edges lmat)]
          (set! (.-name lines) "pm-ghost-edges")
          (set! (.. lines -userData -ghostEdges) true)
          (set! (.-raycast lines) (fn [] nil))   ; clicks hit the fill, never the outline
          (.add o lines)))))
  obj)

(defn unghost!
  "Reverses `ghost!` (GDD §2.1, §4.8): once a blueprint is approved, its
   meshes go back to looking solid and real -- their own colours, opaque,
   normal depth-write -- on the materials `ghost!` already cloned per-mesh,
   and the neon edges are removed. Returns obj."
  [^js obj]
  (let [edges #js []]
    (.traverse obj (fn [^js o] (when (.. o -userData -ghostEdges) (.push edges o))))
    (doseq [^js l edges]
      (.removeFromParent l)
      (.dispose (.-geometry l))
      (.dispose (.-material l))))
  (doseq [^js o (meshes-under obj)]
    (let [^js mat (.-material o)]
      (when-let [^js was (.. o -userData -ghostWas)]
        (when (and (.-color mat) (some? (.-color was))) (.setHex (.-color mat) (.-color was)))
        (when (and (.-emissive mat) (some? (.-emissive was)))
          (.setHex (.-emissive mat) (.-emissive was))
          (set! (.-emissiveIntensity mat) (.-ei was)))
        (js-delete (.-userData o) "ghostWas"))
      (set! (.-transparent mat) false)
      (set! (.-opacity mat) 1)
      (set! (.-wireframe mat) false)
      (set! (.-depthWrite mat) true)))
  obj)

(defn- hex->css [n]
  (str "#" (.padStart (.toString (bit-and n 0xffffff) 16) 6 "0")))

(defn set-label-text!
  "Redraw `sprite`'s canvas with `text` (a rounded paper rectangle with the
   text centred in ink, plus the label's `:border` outline if any), or hide
   the sprite when `text` is empty/nil. Uses the label's `:font-max` (see
   `label`). Never makes a new texture or canvas."
  [^js sprite text]
  (let [^js data   (.-userData sprite)
        ^js canvas (.-canvas data)
        ^js ctx    (.getContext canvas "2d")
        ^js tex    (.-texture data)
        w          (.-width canvas)
        h          (.-height canvas)
        paper      (hex->css (.-paper data))
        ink        (hex->css (.-ink data))
        font-max   (or (.-fontMax data) 56)
        border     (.-border data)]
    (if (or (nil? text) (= text ""))
      (set! (.-visible sprite) false)
      (do
        (.clearRect ctx 0 0 w h)
        (.beginPath ctx)
        (set! (.-fillStyle ctx) paper)
        (.roundRect ctx 3 3 (- w 6) (- h 6) (* 0.22 h))
        (.fill ctx)
        (when border
          (set! (.-strokeStyle ctx) (hex->css border))
          (set! (.-lineWidth ctx) 4)
          (.stroke ctx))
        (set! (.-fillStyle ctx) ink)
        (set! (.-textAlign ctx) "center")
        (set! (.-textBaseline ctx) "middle")
        ;; one line per "\n", each fitted to the width, all one size
        (let [lines  (str/split text #"\n")
              n      (count lines)
              line-h (/ h n)
              cap    (min font-max (* 0.8 line-h))]
          (loop [size cap]
            (set! (.-font ctx) (str "bold " size "px sans-serif"))
            (if (and (> size 12)
                     (some #(> (.-width (.measureText ctx %)) (- w (* 0.3 (min h 128)))) lines))
              (recur (dec size))
              nil))
          (doseq [[i line] (map-indexed vector lines)]
            (.fillText ctx line (/ w 2) (* line-h (+ i 0.5)))))
        (set! (.-visible sprite) true)
        (set! (.-needsUpdate tex) true)))
    sprite))

(defn set-label-runs!
  "Redraw `sprite`'s canvas with `runs` ([{:text :color :alpha :scale} ...],
   drawn left to right, centred as a whole; `:scale` sizes a run's font
   relative to the rest, default 1), or hide the sprite when `runs` is
   empty/nil. Uses the label's `:font-max` and `:border` (see `label`).
   Never makes a new texture or canvas. `set-label-text!` is unchanged."
  [^js sprite runs]
  (let [^js data   (.-userData sprite)
        ^js canvas (.-canvas data)
        ^js ctx    (.getContext canvas "2d")
        ^js tex    (.-texture data)
        w          (.-width canvas)
        h          (.-height canvas)
        paper      (hex->css (.-paper data))
        ink        (hex->css (.-ink data))
        font-max   (or (.-fontMax data) 56)
        border     (.-border data)
        font!      (fn [size r] (set! (.-font ctx) (str "bold " (js/Math.round (* size (or (:scale r) 1))) "px sans-serif")))
        widths     (fn [size] (mapv (fn [r] (font! size r) (.-width (.measureText ctx (:text r)))) runs))]
    (if (empty? runs)
      (set! (.-visible sprite) false)
      (do
        (.clearRect ctx 0 0 w h)
        (.beginPath ctx)
        (set! (.-fillStyle ctx) paper)
        (.roundRect ctx 3 3 (- w 6) (- h 6) (* 0.22 h))
        (.fill ctx)
        (when border
          (set! (.-strokeStyle ctx) (hex->css border))
          (set! (.-lineWidth ctx) 4)
          (.stroke ctx))
        (set! (.-textBaseline ctx) "middle")
        (set! (.-textAlign ctx) "left")
        (let [size  (loop [size font-max]
                      (if (and (> size 12) (> (reduce + (widths size)) (- w (* 0.3 h))))
                        (recur (dec size))
                        size))
              ws    (widths size)
              total (reduce + ws)]
          (loop [x (/ (- w total) 2) rs runs ws ws]
            (when (seq rs)
              (let [r (first rs)]
                (font! size r)
                (set! (.-fillStyle ctx) (if (:color r) (hex->css (:color r)) ink))
                (set! (.-globalAlpha ctx) (:alpha r))
                (.fillText ctx (:text r) x (/ h 2))
                (recur (+ x (first ws)) (rest rs) (rest ws))))))
        (set! (.-globalAlpha ctx) 1)
        (set! (.-visible sprite) true)
        (set! (.-needsUpdate tex) true)))
    sprite))

(defn label
  "A Sprite that always faces the camera, showing `text` on a rounded-rect
   paper background. Opts: :text (default \"\"), :height metres (default 0.3),
   :paper :ink ints, :pos [x y z]; for `set-label-runs!` also :canvas [w h]
   pixels (default [512 128]; the sprite keeps that aspect), :font-max px
   (default 56) and :border (an ink int for a thin outline, default none)."
  [{:keys [text height paper ink pos font-max border] [cw ch] :canvas :or {text "" height 0.3}}]
  (let [[x y z] pos
        cw         (or cw 512)
        ch         (or ch 128)
        width      (* height (/ cw ch))
        ^js T      (three)
        ^js Sprite (.-Sprite T)
        ^js SpriteMaterial (.-SpriteMaterial T)
        ^js CanvasTexture (.-CanvasTexture T)
        ^js canvas (js/document.createElement "canvas")
        _          (set! (.-width canvas) cw)
        _          (set! (.-height canvas) ch)
        ^js tex    (CanvasTexture. canvas)
        _          (set! (.-colorSpace tex) (.-SRGBColorSpace T))
        ^js mat    (SpriteMaterial. #js {:map tex :transparent true :depthWrite false})
        ^js s      (Sprite. mat)]
    (.set (.-scale s) width height 1)
    (.set (.-position s) x y z)
    (set! (.-userData s) #js {:canvas canvas :texture tex :paper paper :ink ink :fontMax font-max :border border})
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

(defn vec3
  "new THREE.Vector3(x, y, z) with the current THREE."
  [[x y z]]
  (let [^js T (three)
        ^js Vector3 (.-Vector3 T)]
    (Vector3. x y z)))

(defn set-pos! [^js obj [x y z]]
  (.set (.-position obj) x y z)
  obj)

(defn set-yaw! [^js obj rad]
  (set! (.-y (.-rotation obj)) rad)
  obj)

(defn set-quat! [^js obj [x y z w]]
  (.set (.-quaternion obj) x y z w)
  obj)

(defn set-visible! [^js obj on?]
  (set! (.-visible obj) (boolean on?))
  obj)

(defn remove! [^js parent ^js obj]
  (.remove parent obj)
  parent)

(defn set-scale! [^js obj s]
  (.setScalar (.-scale obj) s)
  obj)

(defn top-y
  "The highest y of obj's world-space bounding box, or nil (never throws)."
  [^js obj]
  (try
    (.updateMatrixWorld obj true)
    (let [^js T (three)
          ^js Box3 (.-Box3 T)
          ^js box (.setFromObject (Box3.) obj)
          y (.-y (.-max box))]
      (when (js/isFinite y) y))
    (catch :default _ nil)))

(defn top-center
  "[cx max-y cz] of obj's world-space bounding box (TASKS T-14, the trial
   card), or nil (never throws)."
  [^js obj]
  (try
    (.updateMatrixWorld obj true)
    (let [^js T (three)
          ^js Box3 (.-Box3 T)
          ^js box (.setFromObject (Box3.) obj)
          ^js mn (.-min box) ^js mx (.-max box)
          cx (/ (+ (.-x mn) (.-x mx)) 2)
          cz (/ (+ (.-z mn) (.-z mx)) 2)
          y  (.-y mx)]
      (when (every? js/isFinite [cx y cz])
        [cx y cz]))
    (catch :default _ nil)))

(defn project-ndc
  "`[x y z]` (a world point) projected through `camera` into NDC space
   (`Vector3.project`), or nil (never throws; TASKS T-14, the trial card)."
  [^js camera [x y z]]
  (try
    (let [^js v (vec3 [x y z])]
      (.project v camera)
      (when (every? js/isFinite [(.-x v) (.-y v) (.-z v)])
        [(.-x v) (.-y v) (.-z v)]))
    (catch :default _ nil)))

(defn find-meshes
  "Every mesh under `root` whose name satisfies `pred`, as a vector. A nil
   root -> []."
  [^js root pred]
  (if (nil? root)
    []
    (let [out #js []]
      (.traverse root
                 (fn [^js o]
                   (when (and (.-isMesh o) (pred (.-name o)))
                     (.push out o))))
      (vec out))))

(defn down-hits
  "Ray straight down from (x, from, z) through `objs` (recursive). All hits
   nearest first, as [[y name] ...] (y not rounded). Never throws."
  [objs x z from]
  (try
    (let [^js T  (three)
          ^js rc (new (.-Raycaster T)
                      (new (.-Vector3 T) x from z)
                      (new (.-Vector3 T) 0 -1 0))]
      (->> (.intersectObjects rc (into-array objs) true)
           (map (fn [^js hit]
                  (let [^js o (.-object hit) ^js m (.-material o)]
                    [(.. hit -point -y)
                     (str (or (not-empty (.-name o)) (.-type o))
                          (when (and m (not-empty (.-name m))) (str "/" (.-name m))))])))
           vec))
    (catch :default _ [])))

(defn- flatten-points [points]
  (js/Float32Array.from (into-array (mapcat identity points))))

(defn polyline
  "A THREE.Line of `points` ([[x y z] ...]), thin and always-on-top."
  [points color]
  (let [^js T    (three)
        ^js Line (.-Line T)
        ^js Geom (.-BufferGeometry T)
        ^js Attr (.-Float32BufferAttribute T)
        ^js Mat  (.-LineBasicMaterial T)
        ^js geom (Geom.)
        ^js mat  (Mat. #js {:color color :depthTest false :transparent true :opacity 0.9})
        ^js line (Line. geom mat)]
    (.setAttribute geom "position" (Attr. (flatten-points points) 3))
    (set! (.-renderOrder line) 999)
    line))

(defn set-polyline!
  "Move `line`'s points to `points`. Rewrites in place when the count is
   unchanged, else swaps in a fresh geometry and disposes the old one."
  [^js line points]
  (let [^js geom (.-geometry line)
        ^js attr (.getAttribute geom "position")
        n        (count points)]
    (if (and attr (= (.-count attr) n))
      (let [^js arr (.-array attr)]
        (doseq [[i [x y z]] (map-indexed vector points)]
          (let [base (* i 3)]
            (aset arr base x)
            (aset arr (+ base 1) y)
            (aset arr (+ base 2) z)))
        (set! (.-needsUpdate attr) true))
      (let [^js T        (three)
            ^js Geom     (.-BufferGeometry T)
            ^js Attr     (.-Float32BufferAttribute T)
            ^js new-geom (Geom.)]
        (.setAttribute new-geom "position" (Attr. (flatten-points points) 3))
        (set! (.-geometry line) new-geom)
        (.dispose geom)))
    line))

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

(defn down-hits-n
  "Like `down-hits`, but each hit is [y name [nx ny nz]]. The normal is the
   hit face's normal transformed into world space, or nil when there is no
   face. Never throws."
  [objs x z from]
  (try
    (let [^js T  (three)
          ^js rc (new (.-Raycaster T)
                      (new (.-Vector3 T) x from z)
                      (new (.-Vector3 T) 0 -1 0))]
      (->> (.intersectObjects rc (into-array objs) true)
           (map (fn [^js hit]
                  (let [^js o (.-object hit) ^js m (.-material o)
                        ^js face (.-face hit)
                        n (when face
                            (let [^js v (.clone (.-normal face))]
                              (.transformDirection v (.-matrixWorld o))
                              [(.-x v) (.-y v) (.-z v)]))]
                    [(.. hit -point -y)
                     (str (or (not-empty (.-name o)) (.-type o))
                          (when (and m (not-empty (.-name m))) (str "/" (.-name m))))
                     n])))
           vec))
    (catch :default _ [])))

(defn object-names
  "A vector of every non-blank .name under root, root included, in
   traverse order."
  [^js root]
  (if (nil? root)
    []
    (let [out #js []]
      (.traverse root
                 (fn [^js o]
                   (when (not-empty (.-name o))
                     (.push out (.-name o)))))
      (vec out))))

(defn find-first
  "The first object under `root` (depth-first) satisfying `pred`, or nil."
  [^js root pred]
  (when root
    (let [found (atom nil)]
      (.traverse root (fn [^js o] (when (and (nil? @found) (pred o)) (reset! found o))))
      @found)))

(defn kit-light-sources
  "The scenery kit's own night lights, found once in `scene` by what
   `kit` (estate.edn's :kit-lights) says they look like: a lamp (a point
   light of `:lamp-color`) and a lantern mesh (named `:lantern-mesh`) whose
   glowing material brightens at night. Either may be nil (no kit)."
  [^js scene {:keys [lamp-color lantern-mesh]}]
  (let [^js T     (three)
        ^js Color (.-Color T)
        hex       (when (string? lamp-color) (.getHex (Color. lamp-color)))]
    {:lamp    (when hex (find-first scene #(and (.-isPointLight ^js %) (= hex (.getHex (.-color ^js %))))))
     :lantern (when (string? lantern-mesh)
                (some-> (find-first scene #(and (.-isMesh ^js %) (= lantern-mesh (.-name ^js %)))) .-material))}))

(defn light-glows!
  "Gives every mesh under `obj` marked as glowing (a built lamp's bulb) a
   point light like the kit's lamp (its colour, range and falloff), once.
   Returns obj."
  [^js obj {:keys [^js lamp]}]
  (.traverse obj
    (fn [^js o]
      (when (and (.-isMesh o) (.. o -userData -glow) (not (.. o -userData -light)))
        (let [^js T  (three)
              ^js PL (.-PointLight T)
              ^js l  (PL. (if lamp (.-color lamp) 0xffd28a) 0
                          (if lamp (.-distance lamp) 10) (if lamp (.-decay lamp) 2))]
          (.add o l)
          ;; its own material: built from saved state it never went through
          ;; ghost! (which clones), and stand-in materials are shared
          (when-not (.. o -userData -ownMaterial)
            (set! (.-material o) (.clone (.-material o)))
            (set! (.. o -userData -ownMaterial) true))
          (set! (.. o -userData -light) l)))))
  obj)

(defn unlight-glows!
  "Reverses `light-glows!` (TASKS T-14: an undone lamp stops shining): every
   descendant of `obj` with a `userData.light` has that light removed from
   its parent, disposed if it can be, and `userData.light` deleted, so a
   later trial can `light-glows!` it again. Returns obj."
  [^js obj]
  (let [lit #js []]
    (.traverse obj (fn [^js o] (when (.. o -userData -light) (.push lit o))))
    (doseq [^js o lit]
      (let [^js l (.. o -userData -light)]
        (.removeFromParent l)
        (when (.-dispose l) (.dispose l))
        (js-delete (.-userData o) "light"))))
  obj)

(defn sync-glows!
  "Every lit glow under `root` follows the kit's own night lights each
   frame: its light as bright as the kit's lamp, its bulb glowing like the
   kit's lantern. So it fades in at dusk exactly when theirs do."
  [^js root {:keys [^js lamp ^js lantern]}]
  (when root
    (.traverse root
      (fn [^js o]
        (when-let [^js l (.. o -userData -light)]
          (when lamp (set! (.-intensity l) (.-intensity lamp)))
          (when lantern
            (let [^js m (.-material o)]
              (.copy (.-emissive m) (.-emissive lantern))
              (set! (.-emissiveIntensity m) (.-emissiveIntensity lantern))))))))
  nil)

(defn by-name
  "root.getObjectByName(name), or nil."
  [^js root name]
  (when root
    (or (.getObjectByName root name) nil)))

(defn update-world!
  "obj.updateMatrixWorld(true); returns obj."
  [^js obj]
  (.updateMatrixWorld obj true)
  obj)

(defn world-pos
  "[x y z] of obj's world position."
  [^js obj]
  (let [^js T (three)
        ^js v (new (.-Vector3 T))]
    (.getWorldPosition obj v)
    [(.-x v) (.-y v) (.-z v)]))

(defn world-scale-y
  "The world-space y scale of obj (TASKS T-14: a sprite's own height, for
   the trial card's anchor), or nil (never throws)."
  [^js obj]
  (try
    (let [^js T (three)
          ^js v (new (.-Vector3 T))]
      (.getWorldScale obj v)
      (when (js/isFinite (.-y v)) (.-y v)))
    (catch :default _ nil)))

(defn world-quat
  "A new world Quaternion (opaque to CLJS)."
  [^js obj]
  (let [^js T (three)
        ^js q (new (.-Quaternion T))]
    (.getWorldQuaternion obj q)
    q))

(defn set-world-quat!
  "Sets bone.quaternion = parentWorld⁻¹ · q, then bone.updateMatrixWorld(true)."
  [^js bone q]
  (let [^js T (three)
        ^js parent (.-parent bone)
        ^js inv (new (.-Quaternion T))]
    (if parent
      (.getWorldQuaternion parent inv)
      (.identity inv))
    (.invert inv)
    (.multiply inv q)
    (.copy (.-quaternion bone) inv)
    (.updateMatrixWorld bone true)
    bone))

(defn- unit-vec3 [[x y z]]
  (let [len (js/Math.sqrt (+ (* x x) (* y y) (* z z)))]
    (when (>= len 1e-9)
      (vec3 [(/ x len) (/ y len) (/ z len)]))))

(defn aim-bone!
  "from/to are [x y z]; a no-op when either is shorter than 1e-9. r =
   setFromUnitVectors(f̂, t̂), the new world quat is r · world(bone), then
   set-world-quat!."
  [^js bone from to]
  (when-let [fhat (unit-vec3 from)]
    (when-let [that (unit-vec3 to)]
      (let [^js T (three)
            ^js Quaternion (.-Quaternion T)
            ^js r (.setFromUnitVectors (Quaternion.) fhat that)
            ^js world (world-quat bone)]
        (.multiply r world)
        (set-world-quat! bone r)))))

(defn set-line-color!
  "(.setHex (.-color (.-material line)) color). Returns nil."
  [^js line color]
  (let [^js mat (.-material line)
        ^js col (.-color mat)]
    (.setHex col color))
  nil)

(defn tilt-bone!
  "The same as aim-bone!, with r = setFromAxisAngle(axis, angle)."
  [^js bone [ax ay az] angle]
  (let [^js T (three)
        ^js Vector3 (.-Vector3 T)
        ^js Quaternion (.-Quaternion T)
        ^js axis (Vector3. ax ay az)
        ^js r (.setFromAxisAngle (Quaternion.) axis angle)
        ^js world (world-quat bone)]
    (.multiply r world)
    (set-world-quat! bone r)))
