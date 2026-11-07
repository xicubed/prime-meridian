(ns pm.avoid
  "Pure geometry for walkers avoiding circles on the ground: footprints,
   the first blocking circle along a path, tangent detour points, and
   moving a target clear of a static it sits inside. No requires; the
   only JS used is js/Math.")

(def margin 0.15)
(def pad 0.1)
(def free-pad 0.05)

(defn- dist [[x1 z1] [x2 z2]]
  (js/Math.hypot (- x1 x2) (- z1 z2)))

(defn rotate [[dx dz] yaw-deg]
  (let [theta (* yaw-deg (/ js/Math.PI 180))
        c (js/Math.cos theta) s (js/Math.sin theta)]
    [(+ (* dx c) (* dz s)) (+ (* (- dx) s) (* dz c))]))

(defn- valid-vec3? [v]
  (and (vector? v) (= 3 (count v)) (every? number? v)))

(defn footprint [item]
  (let [at (:at item)
        fp (:footprint item)]
    (if (and (valid-vec3? at) (sequential? fp))
      (let [[x _ z] at
            s (:scale item) s (if (and (number? s) (pos? s)) s 1)
            theta (:yaw item) theta (if (number? theta) theta 0)]
        (into []
              (keep (fn [entry]
                      (when (valid-vec3? entry)
                        (let [[dx dz r] entry]
                          (when (pos? r)
                            (let [[rx rz] (rotate [dx dz] theta)]
                              {:at [(+ x (* s rx)) (+ z (* s rz))] :r (* s r)}))))))
              fp))
      [])))

(defn- valid-obstacle [o]
  (let [at (:at o) r (:r o)]
    (when (and (vector? at) (= 2 (count at)) (every? number? at) (number? r) (pos? r))
      {:at at :r r})))

(defn statics [world]
  (let [ids     (sort (keys (:mirrors world)))
        mirrors (into []
                       (mapcat (fn [id] (mapcat footprint (:placed (get-in world [:mirrors id])))))
                       ids)
        obstacles (into [] (keep valid-obstacle) (get-in world [:motion :obstacles]))]
    (into mirrors obstacles)))

(defn inside? [pt r statics]
  (boolean (some (fn [c] (< (dist pt (:at c)) (+ r (:r c) margin))) statics)))

(defn first-block [p v t-max r circles]
  (reduce
    (fn [best c]
      (let [[cvx cvz] (or (:vel c) [0 0])
            [cax caz] (:at c)
            [px pz]   p
            [vx vz]   v
            d0x (- cax px) d0z (- caz pz)
            vrx (- cvx vx) vrz (- cvz vz)
            vr2 (+ (* vrx vrx) (* vrz vrz))
            t*  (if (zero? vr2)
                  0
                  (let [raw (/ (- (+ (* d0x vrx) (* d0z vrz))) vr2)]
                    (max 0 (min t-max raw))))
            dx  (+ d0x (* vrx t*))
            dz  (+ d0z (* vrz t*))
            dd  (js/Math.hypot dx dz)
            clear (+ r (:r c) margin)]
        (if (and (> t* 0) (< dd clear))
          (let [cand {:t t* :at [(+ cax (* cvx t*)) (+ caz (* cvz t*))] :clear clear :dist dd}]
            (if (or (nil? best) (< t* (:t best))) cand best))
          best)))
    nil circles))

(defn path-block [p q speed horizon-s r circles]
  (let [[px pz] p [qx qz] q
        dx (- qx px) dz (- qz pz)
        d  (js/Math.hypot dx dz)]
    (when (pos? d)
      (let [u [(/ dx d) (/ dz d)]
            L (min d (* speed horizon-s))
            v [(* (first u) speed) (* (second u) speed)]]
        (first-block p v (/ L speed) r circles)))))

(defn tangent-aim [p c clear side]
  (let [[px pz] p [cx cz] c
        dcx (- cx px) dcz (- cz pz)
        d   (js/Math.hypot dcx dcz)
        ux  (if (zero? d) 1 (/ dcx d))
        uz  (if (zero? d) 0 (/ dcz d))
        s   (if (= side :right) 1 -1)
        rux (- uz) ruz ux
        beyond? (> d clear)
        alpha (if beyond? (js/Math.asin (/ clear d)) (/ js/Math.PI 2))
        len   (if beyond? (+ (js/Math.sqrt (- (* d d) (* clear clear))) clear) clear)
        cosA (js/Math.cos alpha) sinA (js/Math.sin alpha)
        dirx (+ (* ux cosA) (* s rux sinA))
        dirz (+ (* uz cosA) (* s ruz sinA))]
    [(+ px (* dirx len)) (+ pz (* dirz len))]))

(defn- free-goal-pass [g u r statics]
  (reduce
    (fn [[g moved?] c]
      (let [[cax caz] (:at c)
            d (js/Math.hypot (- (first g) cax) (- (second g) caz))
            C (+ r (:r c) margin)]
        (if (< d C)
          (let [wx (- (first g) cax) wz (- (second g) caz)
                [ux uz] u
                wu (+ (* wx ux) (* wz uz))
                wmag2 (+ (* wx wx) (* wz wz))
                cp (+ C free-pad)
                k  (+ wu (js/Math.sqrt (+ (- (* wu wu) wmag2) (* cp cp))))]
            [[(- (first g) (* ux k)) (- (second g) (* uz k))] true])
          [g moved?])))
    [g false] statics))

(defn free-goal [p g r statics]
  (let [[px pz] p [gx gz] g
        dmag (js/Math.hypot (- gx px) (- gz pz))]
    (if (zero? dmag)
      g
      (let [u [(/ (- gx px) dmag) (/ (- gz pz) dmag)]
            max-passes (inc (count statics))
            behind? (fn [g] (< (+ (* (- (first g) px) (first u)) (* (- (second g) pz) (second u))) 0))]
        (loop [g g pass 0]
          (let [[g' moved?] (free-goal-pass g u r statics)]
            (if (or (not moved?) (>= (inc pass) max-passes))
              (if (behind? g') p g')
              (recur g' (inc pass)))))))))

(defn circle-points [[x z] r n]
  (let [pt (fn [k]
              (let [theta (* 2 js/Math.PI (/ k n))]
                [(+ x (* r (js/Math.cos theta))) (+ z (* r (js/Math.sin theta)))]))
        pts (mapv pt (range n))]
    (conj pts (first pts))))
