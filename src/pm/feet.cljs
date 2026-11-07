(ns pm.feet
  "Pure foot-IK rules: matching ankle bones to ground readings each frame,
   the pelvis drop, and the analytic two-bone IK itself. No three.js, no
   atoms. Vectors are [x y z] in world metres, y up."
  (:require [clojure.string :as str]
            [pm.motion :as m]))

;; -- Small vector helpers (private) -----------------------------------------

(defn- v-sub [[ax ay az] [bx by bz]] [(- ax bx) (- ay by) (- az bz)])
(defn- v-add [[ax ay az] [bx by bz]] [(+ ax bx) (+ ay by) (+ az bz)])
(defn- v-scale [[x y z] s] [(* x s) (* y s) (* z s)])
(defn- v-len [[x y z]] (js/Math.sqrt (+ (* x x) (* y y) (* z z))))
(defn- v-dot [[ax ay az] [bx by bz]] (+ (* ax bx) (* ay by) (* az bz)))
(defn- v-cross [[ax ay az] [bx by bz]]
  [(- (* ay bz) (* az by)) (- (* az bx) (* ax bz)) (- (* ax by) (* ay bx))])

(defn- perp
  "The unit part of v perpendicular to unit u, or nil when too small."
  [v u]
  (let [proj (v-scale u (v-dot v u))
        p    (v-sub v proj)
        len  (v-len p)]
    (when (>= len 1e-6)
      (v-scale p (/ 1 len)))))

(defn- dist2 [[x1 z1] [x2 z2]]
  (js/Math.hypot (- x1 x2) (- z1 z2)))

;; -- Names and config --------------------------------------------------------

(defn sanitize [s]
  (-> s
      (str/replace #"\s" "_")
      (str/replace #"[\[\].:/]" "")))

(defn find-bone [wanted names]
  (when (seq names)
    (let [bare       (str/replace wanted #"^[^:]*:" "")
          san-wanted (sanitize wanted)
          san-bare   (sanitize bare)]
      (or (first (filter #(= % wanted) names))
          (first (filter #(= (sanitize %) san-wanted) names))
          (first (filter #(= (sanitize %) san-bare) names))))))

(defn leg-bones [chain names]
  (let [[hip knee ankle toe] chain
        h (find-bone hip names)
        k (find-bone knee names)
        a (find-bone ankle names)
        t (when toe (find-bone toe names))]
    (when (and h k a)
      {:hip h :knee k :ankle a :toe t})))

(defn- valid-chain? [chain]
  (and (vector? chain)
       (#{3 4} (count chain))
       (every? #(and (string? %) (not (str/blank? %))) chain)))

(defn legs [world]
  (let [raw (get-in world [:motion :legs])]
    (if (map? raw)
      (into {}
            (keep (fn [[rig sides]]
                    (when (and (keyword? rig) (map? sides)
                               (valid-chain? (:left sides)) (valid-chain? (:right sides)))
                      [rig {:left (:left sides) :right (:right sides)}])))
            raw)
      {})))

(def ^:private feet-defaults
  {:drop-cap 0.35 :step-max 0.4 :plant-height 0.04 :plant-speed 0.3
   :tilt-max-deg 25 :tilt-blend-s 0.1 :smooth-s 0.08 :ray-move 0.03})

(defn feet-config [world]
  (let [raw (get-in world [:motion :feet])
        raw (if (map? raw) raw {})]
    (into {}
          (map (fn [[k default]]
                 (let [v (get raw k)]
                   [k (if (and (number? v) (>= v 0)) v default)])))
          feet-defaults)))

(defn ground-hit [hits prefixes]
  (let [qualifying (filter (fn [h] (m/ground-name? (second h) prefixes)) hits)]
    (when (seq qualifying)
      (let [[y _ norm] (apply max-key first qualifying)]
        [y (or norm [0 1 0])]))))

(defn needs-ray? [last-xz xz move]
  (or (nil? last-xz) (>= (dist2 last-xz xz) move)))

;; -- Maths --------------------------------------------------------------------

(defn smooth-by [prev target dt tau]
  (cond
    (nil? prev) target
    (nil? target) prev
    (<= tau 0) target
    :else
    (let [dt' (max 0 dt)]
      (+ prev (* (- target prev) (- 1 (js/Math.exp (- (/ dt' tau)))))))))

(defn pelvis-drop [dl dr cap]
  (let [dl (or dl 0) dr (or dr 0)]
    (min cap (max 0 (- (min dl dr))))))

(defn base-height [grounds prev]
  (let [vals (remove nil? grounds)]
    (if (seq vals) (apply min vals) prev)))

(defn planted? [height speed cfg ds]
  (and (number? height)
       (<= height (* (:plant-height cfg) ds))
       (<= speed (* (:plant-speed cfg) ds))))

(defn two-bone [hip knee ankle target pole]
  (let [l1    (v-len (v-sub knee hip))
        l2    (v-len (v-sub ankle knee))
        D     (v-sub target hip)
        dist  (v-len D)
        u     (if (< dist 1e-9)
                (let [ha (v-sub ankle hip)] (v-scale ha (/ 1 (v-len ha))))
                (v-scale D (/ 1 dist)))
        lo    (max (js/Math.abs (- l1 l2)) 1e-4)
        hi    (+ l1 l2)
        r     (min hi (max lo dist))
        clamped? (or (> dist hi) (< dist lo))
        b     (or (perp (v-sub knee hip) u)
                  (and pole (perp pole u))
                  (perp [0 0 1] u)
                  (perp [1 0 0] u))
        cosA  (max -1 (min 1 (/ (+ (* l1 l1) (* r r) (- (* l2 l2))) (* 2 l1 r))))
        sinA  (js/Math.sqrt (- 1 (* cosA cosA)))
        knee' (v-add hip (v-add (v-scale u (* l1 cosA)) (v-scale b (* l1 sinA))))
        ankle' (v-add hip (v-scale u r))]
    {:knee knee' :ankle ankle' :reach r :clamped? clamped?}))

(defn tilt [n max-deg]
  (if (or (nil? n) (zero? (v-len n)))
    {:axis nil :angle 0}
    (let [len   (v-len n)
          nhat  (v-scale n (/ 1 len))
          c     (v-cross [0 1 0] nhat)
          clen  (v-len c)]
      (if (< clen 1e-6)
        {:axis nil :angle 0}
        (let [axis (v-scale c (/ 1 clen))
              ny   (max -1 (min 1 (nth nhat 1)))
              angle (min (js/Math.acos ny) (* max-deg (/ js/Math.PI 180)))]
          {:axis axis :angle angle})))))

(defn blend-weight [w planted? dt blend-s]
  (if (<= blend-s 0)
    (if planted? 1 0)
    (let [step (/ dt blend-s)
          w'   (if planted? (+ w step) (- w step))]
      (max 0 (min 1 w')))))

;; -- foot-frame ---------------------------------------------------------------

(defn feet-init []
  {:d [0 0] :drop 0 :w [0 0] :prev [nil nil]})

(defn foot-frame [state input cfg ds dt]
  (let [{:keys [d w prev drop]} state
        oy (:obj-y input)
        b  (:base input)
        per-foot
        (mapv
          (fn [i foot]
            (let [[ax ay az] (:ankle foot)
                  g      (:g foot)
                  n      (:n foot)
                  a0     (:a0 foot)
                  loc    (- ay oy)
                  hgt    (- loc a0)
                  prev-i (get prev i)
                  v      (if (or (nil? prev-i) (<= dt 0)) 0 (/ (dist2 prev-i [ax az]) dt))
                  pl     (planted? hgt v cfg ds)
                  d-i    (smooth-by (get d i) (if g (- g b) 0) dt (:smooth-s cfg))
                  w-i    (blend-weight (get w i) pl dt (:tilt-blend-s cfg))
                  t-i    [ax (+ b loc d-i) az]
                  tilt-i (update (tilt n (:tilt-max-deg cfg)) :angle * w-i)]
              {:d d-i :w w-i :prev [ax az] :planted pl :target t-i :tilt tilt-i :g g}))
          (range) (:feet input))
        [l r]  per-foot
        ground (base-height [(when (:planted l) (:g l)) (when (:planted r) (:g r))] nil)
        drop'  (smooth-by drop (pelvis-drop (:d l) (:d r) (:drop-cap cfg)) dt (:smooth-s cfg))]
    {:state {:d [(:d l) (:d r)] :drop drop' :w [(:w l) (:w r)] :prev [(:prev l) (:prev r)]}
     :ground ground
     :obj-y (- b drop')
     :targets [(:target l) (:target r)]
     :tilts [(:tilt l) (:tilt r)]
     :planted [(:planted l) (:planted r)]}))
