(ns pm.creature-brain
  "Pure rules for the learned brain: the policy request from species data and
   the URL, policy.json checks, the 60-number observation, action -> ctrl,
   the control schedule (a pure state machine), the seeded drop pose, the
   \"up?\" test and the HUD segment. No atoms, no DOM, no three.js, no
   species names."
  (:require [pm.creature :as c]
            [pm.creature-motion :as cm]))

;; -- Request and files (§3.1) -------------------------------------------------

(defn brain-request [sp search]
  (when-let [b (c/brain sp)]
    (let [params (js/URLSearchParams. (or search ""))]
      (if (= "twitch" (.get params "brain"))
        {:kind :twitch}
        (let [ages        (:ages b)
              default-age (:default-age b)
              age-param   (.get params "age")
              parsed      (when (some? age-param) (js/parseInt age-param 10))
              age         (if (and (some? parsed) (js/isFinite parsed)
                                    (contains? (set ages) parsed))
                            parsed
                            default-age)]
          {:kind     :policy
           :dir      (:dir b)
           :meta-url (str (:dir b) (:meta b))
           :ages     ages
           :age      age
           :drop-m   (:drop-m b)
           :stand    (:stand b)})))))

(defn age-files [br age]
  (let [dir (:dir br)]
    {:model     (str dir "policy_" age ".onnx")
     :data      (str dir "policy_" age ".onnx.data")
     :data-path (str "policy_" age ".onnx.data")}))

;; -- policy.json (§3.2) --------------------------------------------------------

(defn parse-meta [text]
  (js->clj (js/JSON.parse text) :keywordize-keys true))

(defn- n-numbers? [v n]
  (and (vector? v) (= n (count v)) (every? number? v)))

(defn meta-problems [m joint-names]
  (let [n       (count joint-names)
        obs     (:observation m)
        layout  (:layout obs)
        names   (mapv :name layout)
        sizes   (mapv :size layout)
        action  (:action m)
        total   (+ (* 3 n) 6)
        sim-dt  (:simulation_timestep m)
        ctrl-dt (:control_timestep m)
        r       (when (and (number? sim-dt) (not (zero? sim-dt))) (/ ctrl-dt sim-dt))
        rr      (when (some? r) (js/Math.round r))]
    (cond-> []
      (not= (:joint_order m) joint-names)
      (conj "policy.json: joint_order doesn't match the body's joints")

      (not= names ["joint_pos" "joint_vel" "gravity_b" "ang_vel" "last_action"])
      (conj "policy.json: observation layout must be joint_pos, joint_vel, gravity_b, ang_vel, last_action")

      (or (not= sizes [n n 3 3 n]) (not= (:total_dim obs) total))
      (conj (str "policy.json: observation sizes must be [" n " " n " 3 3 " n "], total " total))

      (or (not= (:size action) n)
          (not (n-numbers? (:scale action) n))
          (not (n-numbers? (:offset action) n))
          (not (n-numbers? (:ctrlrange_lo action) n))
          (not (n-numbers? (:ctrlrange_hi action) n)))
      (conj (str "policy.json: action needs " n " of size, scale, offset, ctrlrange_lo, ctrlrange_hi"))

      (not (and (number? sim-dt) (<= (js/Math.abs (- sim-dt c/timestep)) 1e-9)))
      (conj "policy.json: simulation_timestep must be 0.002")

      (or (nil? r) (> (js/Math.abs (- r rr)) 1e-6) (< rr 1))
      (conj "policy.json: control_timestep must be a whole number of physics steps"))))

(defn meta->policy [m joint-names]
  (let [probs (meta-problems m joint-names)]
    (if (seq probs)
      (throw (ex-info (str "bad brain: " (first probs)) {:problems probs}))
      (let [n      (count joint-names)
            obs    (:observation m)
            action (:action m)
            r      (/ (:control_timestep m) (:simulation_timestep m))]
        {:n      n
         :decim  (js/Math.round r)
         :input  (:input_name obs)
         :output (:output_name action)
         :scale  (:scale action)
         :offset (:offset action)
         :lo     (:ctrlrange_lo action)
         :hi     (:ctrlrange_hi action)}))))

(defn address-problems [n jnt-qposadr jnt-dofadr actuator-trnid]
  (into []
        (keep (fn [k]
                (let [j  (aget actuator-trnid (* 2 k))
                      qa (aget jnt-qposadr j)
                      da (aget jnt-dofadr j)]
                  (when-not (and (= j (inc k)) (= qa (+ 7 k)) (= da (+ 6 k)))
                    (str "actuator " k " drives joint " j " (qpos " qa ", dof " da
                         "); expected joint " (inc k) " (qpos " (+ 7 k) ", dof " (+ 6 k) ")")))))
        (range n)))

;; -- Rotation helpers (§3.3) ---------------------------------------------------

(defn quat->xmat [[w x y z]]
  [(- 1 (* 2 (+ (* y y) (* z z)))) (* 2 (- (* x y) (* w z)))       (* 2 (+ (* x z) (* w y)))
   (* 2 (+ (* x y) (* w z)))       (- 1 (* 2 (+ (* x x) (* z z)))) (* 2 (- (* y z) (* w x)))
   (* 2 (- (* x z) (* w y)))       (* 2 (+ (* y z) (* w x)))       (- 1 (* 2 (+ (* x x) (* y y))))])

(defn- quat-mul [[w1 x1 y1 z1] [w2 x2 y2 z2]]
  [(- (* w1 w2) (* x1 x2) (* y1 y2) (* z1 z2))
   (+ (* w1 x2) (* x1 w2) (* y1 z2) (- (* z1 y2)))
   (+ (* w1 y2) (- (* x1 z2)) (* y1 w2) (* z1 x2))
   (+ (* w1 z2) (* x1 y2) (- (* y1 x2)) (* z1 w2))])

(defn- quat-norm [[w x y z]]
  (let [n (js/Math.sqrt (+ (* w w) (* x x) (* y y) (* z z)))]
    (if (zero? n) [1 0 0 0] [(/ w n) (/ x n) (/ y n) (/ z n)])))

(defn quat-integrate [q w dt]
  (let [[wx wy wz] w
        wmag (js/Math.sqrt (+ (* wx wx) (* wy wy) (* wz wz)))]
    (if (zero? wmag)
      (quat-norm q)
      (let [a    (* wmag dt)
            half (/ a 2)
            s    (js/Math.sin half)
            c    (js/Math.cos half)
            u    [(/ wx wmag) (/ wy wmag) (/ wz wmag)]
            dq   [c (* (nth u 0) s) (* (nth u 1) s) (* (nth u 2) s)]]
        (quat-norm (quat-mul q dq))))))

(defn gravity-b [xmat id]
  [(- (aget xmat (+ (* 9 id) 6))) (- (aget xmat (+ (* 9 id) 7))) (- (aget xmat (+ (* 9 id) 8)))])

;; -- Observation and action (§3.4) --------------------------------------------

(defn observation [n qpos qvel xmat last-action]
  (let [qpos-part (mapv #(aget qpos (+ 7 %)) (range n))
        qvel-part (mapv #(aget qvel (+ 6 %)) (range n))
        grav      (gravity-b xmat 1)
        ang       [(aget qvel 3) (aget qvel 4) (aget qvel 5)]]
    (vec (concat qpos-part qvel-part grav ang (vec last-action)))))

(defn action->ctrl [policy action]
  (mapv (fn [a sc off lo hi] (max lo (min hi (+ (* a sc) off))))
        action (:scale policy) (:offset policy) (:lo policy) (:hi policy)))

;; -- The control schedule (§3.5) -----------------------------------------------

(defn schedule-init [decim n]
  {:decim decim :k 0 :owed 0 :ready nil :waiting? false :gen 0
   :last-action (vec (repeat n 0)) :boundaries 0})

(defn add-owed [st steps cap]
  (let [total (+ (:owed st) steps)]
    [(assoc st :owed (min total cap)) (max 0 (- total cap))]))

(defn plan [st]
  (loop [st st ops [] applied nil]
    (cond
      (<= (:owed st) 0)
      {:st st :ops ops :action applied}

      (pos? (:k st))
      (recur (-> st (update :k #(mod (inc %) (:decim st))) (update :owed dec))
             (conj ops :step)
             applied)

      (:ready st)
      (let [a (:ready st)]
        (recur (-> st
                   (assoc :last-action a :ready nil)
                   (update :boundaries inc)
                   (assoc :k (mod 1 (:decim st)))
                   (update :owed dec))
               (-> ops (conj :apply) (conj :step))
               a))

      (:waiting? st)
      {:st st :ops ops :action applied}

      :else
      {:st (assoc st :waiting? true) :ops (conj ops :ask) :action applied})))

(defn receive [st gen action]
  (if (and (= gen (:gen st)) (:waiting? st))
    (assoc st :ready action :waiting? false)
    st))

(defn restart [st]
  (assoc (schedule-init (:decim st) (count (:last-action st))) :gen (inc (:gen st))))

;; -- Drops, "up", HUD (§3.6) ---------------------------------------------------

(defn drop-seed [seed attempt] (+ (* 1000 seed) attempt))

(defn- draws [x0 k]
  (loop [x x0 i 0 acc []]
    (if (= i k)
      acc
      (let [[x' u] (cm/rng-next x)]
        (recur x' (inc i) (conj acc u))))))

(defn drop-pose [seed ranges [lo-m hi-m] [gx gy gz]]
  (let [n      (count ranges)
        x0     (cm/rng-seed seed)
        us     (draws x0 (+ 4 n))
        u1     (nth us 0) u2 (nth us 1) u3 (nth us 2) u4 (nth us 3)
        h      (+ lo-m (* u1 (- hi-m lo-m)))
        a      u2 b u3 c u4
        sqa    (js/Math.sqrt a)
        sq1a   (js/Math.sqrt (- 1 a))
        two-pi (* 2 js/Math.PI)
        qw     (* sqa (js/Math.cos (* two-pi c)))
        qx     (* sq1a (js/Math.sin (* two-pi b)))
        qy     (* sq1a (js/Math.cos (* two-pi b)))
        qz     (* sqa (js/Math.sin (* two-pi c)))
        joints (mapv (fn [k [lo hi]] (+ lo (* (nth us (+ 4 k)) (- hi lo)))) (range n) ranges)]
    (vec (concat [gx gy (+ gz h) qw qx qy qz] joints))))

(defn up? [stand ground-z xpos xmat]
  (and (> (- (aget xpos 5) ground-z) (:torso-m stand))
       (> (aget xmat 17) (:up-z stand))))

(defn hud-segment [b]
  (cond
    (nil? b) ""
    (= :twitch (:kind b)) " · brain twitch"
    (:loading? b) (str " · brain " (:age b) " loading")
    (= :policy (:kind b)) (str " · brain " (:age b) " · " (if (:up? b) "up" "down"))
    :else ""))
