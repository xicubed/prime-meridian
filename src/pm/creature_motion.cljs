(ns pm.creature-motion
  "Pure rules for the creature spike: config/URL, the twitch controller (a
   swappable driver, mirroring pm.motion's walker step/follow split), the
   fixed-step accumulator, stats, HUD text and pose from MuJoCo arrays. Only
   js/Math and js/URLSearchParams; no atoms, no three.js, no species names."
  (:require [clojure.string :as str]
            [pm.creature :as c]
            [pm.motion :as m]))

;; -- Spike config and URL (§5.1) --------------------------------------------

(defn spike-config [world]
  (let [cfg (:creature-spike world)]
    (when (and (map? cfg)
               (string? (:flag cfg)) (not (str/blank? (:flag cfg)))
               (keyword? (:kind cfg))
               (let [at (:at cfg)] (and (vector? at) (= 3 (count at)) (every? number? at))))
      (let [seed   (:seed cfg)
            seed   (if (integer? seed) seed 1)
            yaw    (:yaw cfg)
            yaw    (if (number? yaw) yaw 0)
            drop-m (:drop-m cfg)
            drop-m (if (and (number? drop-m) (>= drop-m 0)) drop-m 0.4)]
        {:flag (:flag cfg) :kind (:kind cfg) :seed seed :at (:at cfg) :yaw yaw :drop-m drop-m}))))

(defn spike-request [search cfg]
  (when cfg
    (let [params (js/URLSearchParams. (or search ""))]
      (when (.has params (:flag cfg))
        (let [v          (.get params (:flag cfg))
              k          (if (and (string? v) (not (str/blank? v))) (keyword v) (:kind cfg))
              seed-param (.get params "seed")
              parsed     (when (some? seed-param) (js/parseInt seed-param 10))
              s          (if (and (some? parsed) (js/isFinite parsed)) parsed (:seed cfg))]
          (assoc cfg :kind k :seed s))))))

;; -- The twitch noise (§5.3) -------------------------------------------------

(defn rng-seed [s]
  (inc (mod (js/Math.floor (js/Math.abs s)) 2147483646)))

(defn rng-next [x]
  (let [x' (mod (* 16807 x) 2147483647)]
    [x' (/ x' 2147483647)]))

(defn- chan-init [seed i]
  (let [x0        (rng-seed (+ (* seed 100) i))
        [x1 _]    (rng-next x0)
        [x2 _]    (rng-next x1)   ; warm-up: discard 2 draws
        [x3 u1]   (rng-next x2)
        [x4 u2]   (rng-next x3)]
    {:v 0 :from 0 :to (- (* 2 u1) 1) :t 0 :dur (+ 0.3 (* 0.5 u2)) :rng x4}))

(defn twitch-init [n seed]
  {:kind :twitch :n n :chans (vec (map #(chan-init seed %) (range n)))})

(defn- smoothstep [u] (* u u (- 3 (* 2 u))))

(defn- roll-channel [ch dt]
  (loop [from (:from ch) to (:to ch) t (+ (:t ch) dt) dur (:dur ch) rng (:rng ch)]
    (if (>= t dur)
      (let [from'    to
            t'       (- t dur)
            [r1 u1]  (rng-next rng)
            to'      (- (* 2 u1) 1)
            [r2 u2]  (rng-next r1)
            dur'     (+ 0.3 (* 0.5 u2))]
        (recur from' to' t' dur' r2))
      {:from from :to to :t t :dur dur :rng rng})))

(defn twitch-step [ctrl dt]
  (if-not (and (number? dt) (js/isFinite dt) (pos? dt))
    ctrl
    (update ctrl :chans
            (fn [chans]
              (mapv (fn [ch]
                      (let [{:keys [from to t dur rng]} (roll-channel ch dt)
                            v (+ from (* (- to from) (smoothstep (/ t dur))))]
                        {:v v :from from :to to :t t :dur dur :rng rng}))
                    chans)))))

(defn twitch-actions [ctrl] (mapv :v (:chans ctrl)))

(defn- clamp [x lo hi] (max lo (min hi x)))

;; -- Sensing (§5.1, pure) -----------------------------------------------------

(defn head-up
  "The body's local `up` axis in world frame: wi = Σj xmat[9·id + 3i + j] · up[j]."
  [xmat id up]
  (mapv (fn [i]
          (reduce + (map (fn [j] (* (aget xmat (+ (* 9 id) (* 3 i) j)) (nth up j))) (range 3))))
        (range 3)))

(defn tilt-deg [u]
  (* (js/Math.acos (clamp (nth u 2) -1 1)) (/ 180 js/Math.PI)))

(defn capsule-clearance
  "The lowest point of the body's capsule above `ground-z`."
  [xpos xmat id capsule ground-z]
  (let [{:keys [from to r]} capsule
        z-at (fn [p]
               (+ (aget xpos (+ (* 3 id) 2))
                  (reduce + (map (fn [j] (* (aget xmat (+ (* 9 id) 6 j)) (nth p j))) (range 3)))))]
    (- (min (z-at from) (z-at to)) r ground-z)))

(defn sense [r xpos xmat jacp jacr ground-z]
  {:up        (head-up xmat (:body-id r) (:up r))
   :clearance (capsule-clearance xpos xmat (:body-id r) (:capsule r) ground-z)
   :jacp      jacp
   :jacr      jacr})

;; -- The reflex math (§5.2, pure) ----------------------------------------------

(defn reflex-strength [t onset-s]
  (if (<= onset-s 0) 1 (clamp (/ t onset-s) 0 1)))

(defn reflex-targets
  "One joint target (radians) per (:joints r), by Jacobian-transpose control."
  [r sense q strength nv]
  (let [[ux uy uz] (:up sense)
        e-rot  [uy (- ux) 0]
        e-lift (max 0 (- (:lift-m r) (:clearance sense)))
        jacp   (:jacp sense)
        jacr   (:jacr sense)]
    (mapv (fn [d [lo hi] qi]
            (let [g (+ (reduce + (map (fn [row] (* (aget jacr (+ (* row nv) d)) (nth e-rot row))) (range 3)))
                       (* (:w-lift r) (aget jacp (+ (* 2 nv) d)) e-lift))]
              (clamp (+ qi (* strength (:gain r) g)) lo hi)))
          (:dofs r) (:ranges r) q)))

(defn target->action [target [lo hi]]
  (let [mid (/ (+ lo hi) 2) half (/ (- hi lo) 2)]
    (/ (- target mid) half)))

;; -- Effort: energy with hysteresis and tremor (§5.1, §5.2, pure) -------------

(defn effort-init [] {:energy 1 :mode :active :level 1})

(defn effort-amount
  "clamp(Σ|forces[k]| / (count(acts)·fmax), 0, 1). 0 when forces is nil, acts
   is empty or fmax <= 0."
  [forces acts fmax]
  (if (or (nil? forces) (empty? acts) (not (pos? fmax)))
    0
    (clamp (/ (reduce + (map #(js/Math.abs (aget forces %)) acts)) (* (count acts) fmax)) 0 1)))

(defn effort-step [st e amount dt]
  (if-not (and (number? dt) (js/isFinite dt) (pos? dt))
    st
    (let [a       (clamp amount 0 1)
          energy  (:energy st)
          mode    (:mode st)
          energy' (if (= mode :active)
                    (max 0 (- energy (/ (* dt a) (:energy-s e))))
                    (min 1 (+ energy (/ dt (:recover-s e)))))
          eps     1e-6                                  ; threshold tolerance: a boundary this
                                                          ; close is floating-point noise, not a crossing
          mode'   (cond
                    (and (= mode :active) (< energy' (- (:low e) eps))) :resting
                    (and (= mode :resting) (> energy' (+ (:high e) eps))) :active
                    :else mode)
          target  (if (= mode' :active) 1 0)
          delta   (/ dt (:ramp-s e))
          level   (:level st)
          level'  (clamp (cond
                            (< level target) (min target (+ level delta))
                            (> level target) (max target (- level delta))
                            :else level)
                          0 1)]
      {:energy energy' :mode mode' :level level'})))

(defn effort-label [st r sense]
  (cond
    (= (:mode st) :resting) "resting"
    (and (>= (:clearance sense) (:lift-m r)) (<= (tilt-deg (:up sense)) 20)) "holding"
    :else "trying"))

(defn tremor-deg-at [e t]
  (* (:tremor-deg e) (js/Math.exp (/ (- (max 0 t)) (:steady-s e)))))

;; -- The controller interface (§5.3) -----------------------------------------

(defn controller [kind n seed]
  (if (= kind :twitch) (twitch-init n seed) {:kind kind :n n}))

(defn reflex-controller
  ([inner reflexes] (reflex-controller inner reflexes 1))
  ([inner reflexes seed]
   (let [base {:kind :reflex :n (:n inner) :inner inner :reflexes reflexes :t 0}]
     (if (some :effort reflexes)
       (assoc base :efforts
              (mapv (fn [r]
                      (when-let [e (:effort r)]
                        (assoc (effort-init) :tremor
                               (twitch-init (count (:joints r)) (+ seed (:seed-salt e))))))
                    reflexes))
       base))))

(defn- reflex-actions
  "[a' efforts'] for one reflex `r` at index `i`, folding into `a`/`efforts`."
  [a efforts i r se obs t' dt']
  (let [q  (mapv #(aget (:qpos obs) %) (:qpos r))
        nv (:nv obs)]
    (if-let [e (:effort r)]
      (let [st      (nth efforts i)
            fmax    (* (:strength e) (:torque-nm e))
            amount  (effort-amount (:forces obs) (:acts r) fmax)
            st1     (effort-step st e amount dt')
            tremor' (twitch-step (:tremor st) (* dt' (:tremor-speed e)))
            s       (* (reflex-strength t' (:onset-s r)) (:strength e))
            targets (reflex-targets r se q s nv)
            amp     (* (/ js/Math.PI 180) (tremor-deg-at e t') (:level st1))
            v       (twitch-actions tremor')
            level   (:level st1)
            sigma   (:rest-frac e)]
        [(reduce (fn [a j]
                   (let [[lo hi] (nth (:ranges r) j)
                         qj      (nth q j)
                         tj      (clamp (+ (nth targets j) (* amp (nth v j))) lo hi)
                         final   (+ (* level tj) (* (- 1 level) (+ qj (* sigma (- 0 qj)))))]
                     (assoc a (nth (:acts r) j) (target->action final (nth (:ranges r) j)))))
                 a (range (count (:acts r))))
         (assoc efforts i (assoc st1 :tremor tremor'))])
      (let [s       (reflex-strength t' (:onset-s r))
            targets (reflex-targets r se q s nv)]
        [(reduce (fn [a j]
                   (assoc a (nth (:acts r) j)
                          (target->action (nth targets j) (nth (:ranges r) j))))
                 a (range (count (:acts r))))
         efforts]))))

(defn act [ctrl obs dt]
  (case (:kind ctrl)
    :twitch (let [c' (twitch-step ctrl dt)] [c' (twitch-actions c')])

    :reflex
    (let [[inner' a] (act (:inner ctrl) obs dt)
          dt'   (if (and (number? dt) (js/isFinite dt) (pos? dt)) dt 0)
          t'    (+ (:t ctrl) dt')
          ctrl' (assoc ctrl :inner inner' :t t')]
      (if (nil? obs)
        [ctrl' a]
        (let [[a' efforts']
              (reduce
                (fn [[a efforts] [i r]]
                  (reflex-actions a efforts i r (nth (:senses obs) i) obs t' dt'))
                [a (:efforts ctrl)]
                (map-indexed vector (:reflexes ctrl)))]
          [(if (contains? ctrl :efforts) (assoc ctrl' :efforts efforts') ctrl') a'])))

    [ctrl (vec (repeat (:n ctrl) 0))]))

(defn actions->ctrl [actions ranges]
  (mapv (fn [a [lo hi]] (+ (/ (+ lo hi) 2) (* (clamp a -1 1) (/ (- hi lo) 2)))) actions ranges))

;; -- Limp when not trying (§5.4, pure, output shaping between act and MuJoCo) -

(defn limp
  "ctrl[k] = q + sigma·(target − q), a servo of stiffness sigma·kp toward the
   same target (the MuJoCo model's kp is untouched). sigma 1 passes target."
  [targets qpos sigmas]
  (mapv (fn [k t sigma]
          (let [q (aget qpos (+ 7 k))]
            (+ q (* sigma (- t q)))))
        (range) targets sigmas))

(defn stiffness
  "One sigma per actuator: 1 for a twitch kick (|:to| >= kick-above) or a
   reflex's own acts (it already blends its own rest), else rest-frac."
  [ctrl rest-frac kick-above]
  (case (:kind ctrl)
    :twitch (mapv (fn [ch] (if (>= (js/Math.abs (:to ch)) kick-above) 1 rest-frac)) (:chans ctrl))
    :reflex (let [base (stiffness (:inner ctrl) rest-frac kick-above)
                  acts (into #{} (mapcat :acts) (:reflexes ctrl))]
              (mapv (fn [k sigma] (if (contains? acts k) 1 sigma)) (range) base))
    (vec (repeat (:n ctrl) rest-frac))))

;; -- Fixed physics step (§5.6) ------------------------------------------------

(def step-s c/timestep)
(def max-steps 25)

(defn fixed-steps
  ([acc dt] (fixed-steps acc dt step-s max-steps))
  ([acc dt step cap]
   (let [acc   (if (and (number? acc) (js/isFinite acc) (pos? acc)) acc 0)
         dt    (if (and (number? dt) (js/isFinite dt) (pos? dt)) dt 0)
         total (+ acc dt)
         n     (js/Math.floor (+ (/ total step) 1e-9))
         s     (min n cap)
         d     (- n s)
         a     (max 0 (- total (* n step)))]
     {:steps s :dropped d :acc a})))

;; -- Stats and HUD (§5.5) -----------------------------------------------------

(def empty-stats {:since nil :sum 0 :n 0 :ms nil :steps 0 :dropped 0})

(defn stats-tick [stats now-ms frame-ms steps dropped]
  (let [since    (or (:since stats) now-ms)
        sum      (+ (:sum stats) frame-ms)
        n        (inc (:n stats))
        dropped' (+ (:dropped stats) dropped)]
    (if (>= (- now-ms since) 1000)
      {:since now-ms :sum 0 :n 0 :ms (/ sum n) :steps steps :dropped dropped'}
      {:since since :sum sum :n n :ms (:ms stats) :steps steps :dropped dropped'})))

(defn hud-text [status]
  (when (map? status)
    (case (:mode status)
      :loading (str (:flag status) ": " (name (:kind status)) " loading")

      :running
      (let [stats  (:stats status)
            ms     (:ms stats)
            torso  (:torso status)
            head   (:head status)
            effort (:effort status)]
        (str (:flag status) ": " (name (:kind status)) " · "
             (if (number? ms) (.toFixed ms 1) "-") " ms/frame · "
             (:steps stats) " steps · " (:dropped stats) " dropped · torso "
             (if (number? torso) (.toFixed torso 2) "-") " m"
             (when (map? head)
               (str " · head " (.toFixed (:clearance head) 2) " m " (js/Math.round (:tilt head)) "°"))
             (when (map? effort)
               (str " · energy " (.toFixed (:energy effort) 2) " · " (:label effort)))))

      :failed
      (str (:flag status) ": " (name (:kind status)) " failed (" (m/short-reason (:reason status)) ")")

      nil)))

;; -- Pose from MuJoCo arrays (§5.6) -------------------------------------------

(defn body-pose [xpos xquat id]
  {:pos  (c/mj->three [(aget xpos (* 3 id)) (aget xpos (+ (* 3 id) 1)) (aget xpos (+ (* 3 id) 2))])
   :quat (c/mj-quat->three [(aget xquat (* 4 id)) (aget xquat (+ (* 4 id) 1))
                             (aget xquat (+ (* 4 id) 2)) (aget xquat (+ (* 4 id) 3))])})
