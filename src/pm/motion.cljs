(ns pm.motion
  "Pure rules for walkers pacing a route: kinematic stepping, MotionBricks
   follow-mode, ground sensing/smoothing, avoidance planning, and HUD text.
   No three.js, no atoms, no js/Date and no walker names — the only JS used
   is js/Math and js/URLSearchParams, both available under Node for testing."
  (:require [clojure.string :as str]
            [pm.avoid :as a]))

;; -- Constants ---------------------------------------------------------

(def default-speed 1.2)
(def arrive-m 0.3)
(def smooth-tau 0.15)
(def plan-every-s 0.25)
(def wait-s 2)
(def resend-m 0.5)
(def default-radius 0.3)
(def default-rig :mixamo)
(def label-gap 0.35)
(def default-senses {:sight-m 8 :sight-deg 110 :hear-m 1.2 :react-s 0.4 :forget-s 1.5})
(def walk-pace 1.2)
(def face-min-speed 0.05)
(def ^:private senses-keys [:sight-m :sight-deg :hear-m :react-s :forget-s])

(def figure
  "The kinematic figure, in pm.plan's look-part format (+x forward, degrees)."
  {:parts [{:shape :capsule :size [1.1 0.42 0.42] :at [0 0.6 0]     :rot [0 0 90] :color :crew}
           {:shape :sphere  :size [0.3 0.32 0.3]  :at [0 1.38 0]                  :color :crew-head}
           {:shape :box     :size [0.06 0.08 0.18] :at [0.14 1.4 0]               :color :eye}]
   :label-y 1.95})

;; -- Heading -------------------------------------------------------------

(defn yaw [dx dz]
  (js/Math.atan2 (- dz) dx))

(defn- pt-dist [[x1 z1] [x2 z2]]
  (js/Math.hypot (- x1 x2) (- z1 z2)))

;; -- Config / walker state -------------------------------------------------

(defn config [world]
  (let [b (get-in world [:motion :bricks])
        b (when (map? b) b)
        g (get-in world [:motion :ground])
        g (if (sequential? g) (filterv string? g) [])
        s (get-in world [:motion :speed])
        s (if (and (number? s) (pos? s)) s default-speed)]
    {:bricks b :ground (vec g) :speed s}))

(defn avoid-config [world]
  (let [r (get-in world [:motion :radius])
        r (if (and (number? r) (pos? r)) r default-radius)
        h (get-in world [:motion :horizon-s])
        h (if (and (number? h) (pos? h)) h 2)]
    {:radius r :horizon-s h}))

(defn senses [base override]
  (if (map? override)
    (into {}
          (map (fn [k]
                 (let [v (get override k)]
                   [k (if (and (number? v) (not (js/isNaN v)) (>= v 0))
                        (if (= k :sight-deg) (min 360 v) v)
                        (get base k))])))
          senses-keys)
    base))

(defn senses-config [world]
  (senses default-senses (get-in world [:motion :senses])))

(defn rig-key [rig]
  (when (keyword? rig)
    (let [[first-part & rest-parts] (str/split (name rig) #"-")]
      (apply str first-part
             (map (fn [p] (str (str/upper-case (subs p 0 1)) (subs p 1))) rest-parts)))))

(defn character [c]
  (when (map? c)
    (let [f (:file c)]
      (when (and (string? f) (not (str/blank? f))
                 (or (str/starts-with? f "/") (str/starts-with? f "https://")))
        (let [r (:rig c)]
          (cond
            (nil? r)     {:file f :rig default-rig}
            (keyword? r) {:file f :rig r}
            :else        nil))))))

(defn display-scale [world]
  (let [ds (get-in world [:motion :display-scale])]
    (if (and (number? ds) (pos? ds)) ds 1.0)))

(defn label-height [top ds]
  (if (and (number? top) (js/isFinite top) (pos? top))
    (+ (* top ds) label-gap)
    (* (:label-y figure) ds)))

(defn- valid-point? [p]
  (and (vector? p) (= 2 (count p)) (every? number? p)))

(defn walker [entry]
  (let [id    (:id entry)
        route (filterv valid-point? (:route entry))]
    (when (and (keyword? id) (seq route))
      (let [nm      (:name entry)
            nm      (if (and (string? nm) (not (str/blank? nm))) nm (name id))
            pause-s (:pause-s entry)
            pause-s (if (and (number? pause-s) (>= pause-s 0)) pause-s 0)
            radius  (:radius entry)
            radius  (if (and (number? radius) (pos? radius)) radius default-radius)
            [x0 z0] (first route)
            heading (if (>= (count route) 2)
                      (let [[x1 z1] (second route)]
                        (yaw (- x1 x0) (- z1 z0)))
                      0)
            speed   (:speed entry)
            speed   (when (and (number? speed) (pos? speed)) speed)
            agent   (:agent entry)
            agent   (if (keyword? agent) agent id)
            tint    (:tint entry)
            tint    (when (and (integer? tint) (<= 0 tint 0xffffff)) tint)
            loop?   (true? (:loop entry))
            face    (:face entry)
            face    (when (valid-point? face) face)]
        {:id id :name nm :route route :pause-s pause-s
         :pos [x0 z0] :heading heading :to 0 :dir 1 :pause pause-s :leg 0
         :ground nil :y nil
         :radius radius :vel [0 0] :aim nil :goal nil :side nil :wait 0 :waited false
         :character (character (:character entry))
         :senses (senses default-senses (:senses entry))
         :speed speed :facing nil :aware {} :glance false :agent agent
         :tint tint :loop loop? :face face}))))

(defn walkers [world]
  (let [cfg  (avoid-config world)
        ds   (display-scale world)
        scfg (senses-config world)]
    (into []
          (keep (fn [entry]
                  (let [entry (if (and (number? (:radius entry)) (pos? (:radius entry)))
                                entry
                                (assoc entry :radius (:radius cfg)))
                        entry (assoc entry :senses (senses scfg (:senses entry)))
                        w (walker entry)]
                    (when w (update w :radius * ds)))))
          (:walkers world))))

;; -- Ping-pong -------------------------------------------------------------

(defn next-index
  ([n i dir] (next-index n i dir false))
  ([n i dir loop?]
   (cond
     (< n 2) [0 1]
     loop?   [(mod (inc i) n) 1]
     :else
     (let [j (+ i dir)]
       (if (and (>= j 0) (< j n))
         [j dir]
         [(- i dir) (- dir)])))))

(defn target [w]
  (get (:route w) (:to w)))

(defn new-target? [before after]
  (not= (:leg before) (:leg after)))

(defn goal-point [w]
  (or (:goal w) (target w)))

(defn steer-point [w]
  (or (:aim w) (goal-point w)))

;; -- step / follow ---------------------------------------------------------

(defn- pick [w]
  (let [[i' dir'] (next-index (count (:route w)) (:to w) (:dir w) (:loop w))]
    (assoc w :to i' :dir dir' :pause 0 :leg (inc (:leg w))
             :aim nil :goal nil :side nil :wait 0 :waited false)))

(defn- arrive [w]
  (if (> (:pause-s w) 0)
    (assoc w :pause (:pause-s w) :aim nil :goal nil :side nil :wait 0 :waited false)
    (pick w)))

(defn step [w speed dt]
  (let [dt'   (max 0 dt)
        route (:route w)]
    (cond
      (< (count route) 2)
      w

      (> (:pause w) 0)
      (let [p (- (:pause w) dt')]
        (if (> p 0)
          (let [w' (assoc w :pause p :vel [0 0])]
            (if-let [[fx fz] (:face w)]
              (let [[x z] (:pos w)
                    dx (- fx x) dz (- fz z)]
                (if (zero? (js/Math.hypot dx dz))
                  w'
                  (let [h (yaw dx dz)]
                    (assoc w' :heading h :facing h))))
              w'))
          (assoc (pick w) :vel [0 0])))

      (> (:wait w) 0)
      (let [p (- (:wait w) dt')]
        (if (> p 0)
          (assoc w :wait p :vel [0 0])
          (assoc w :wait 0 :waited true :vel [0 0])))

      :else
      (let [gp     (goal-point w)
            [x z]  (:pos w)
            dgx (- (first gp) x) dgz (- (second gp) z)
            dg  (js/Math.hypot dgx dgz)]
        (if (<= dg arrive-m)
          (assoc (arrive w) :vel [0 0])
          (let [sp  (steer-point w)
                dsx (- (first sp) x) dsz (- (second sp) z)
                ds  (js/Math.hypot dsx dsz)
                s   (min (* speed dt') ds)
                ux  (if (zero? ds) 0 (/ dsx ds))
                uz  (if (zero? ds) 0 (/ dsz ds))
                nx  (+ x (* ux s))
                nz  (+ z (* uz s))
                vel (if (zero? dt') [0 0] [(/ (* ux s) dt') (/ (* uz s) dt')])
                w'  (assoc w :pos [nx nz] :heading (yaw dsx dsz) :vel vel)]
            (if (:aim w)
              (let [dgx' (- (first gp) nx) dgz' (- (second gp) nz)
                    dg'  (js/Math.hypot dgx' dgz')]
                (cond
                  (<= dg' arrive-m)      (arrive w')
                  (<= (- ds s) arrive-m) (assoc w' :aim nil)
                  :else                  w'))
              (if (<= (- dg s) arrive-m)
                (arrive w')
                w'))))))))

(defn follow [w pos idle? dt]
  (let [dt'     (max 0 dt)
        old-pos (:pos w)
        w       (cond
                  (nil? pos) w
                  (zero? dt') (assoc w :pos pos)
                  :else (assoc w :pos pos
                                 :vel [(/ (- (first pos) (first old-pos)) dt')
                                       (/ (- (second pos) (second old-pos)) dt')]))
        route   (:route w)]
    (cond
      (< (count route) 2)
      w

      (> (:pause w) 0)
      (let [p (- (:pause w) dt')]
        (if (> p 0)
          (let [w' (assoc w :pause p)]
            (if-let [[fx fz] (:face w)]
              (let [[x z] (:pos w)
                    dx (- fx x) dz (- fz z)]
                (if (zero? (js/Math.hypot dx dz))
                  w'
                  (let [h (yaw dx dz)]
                    (assoc w' :heading h :facing h))))
              w'))
          (pick w)))

      (> (:wait w) 0)
      (let [p (- (:wait w) dt')]
        (if (> p 0)
          (assoc w :wait p :vel [0 0])
          (assoc w :wait 0 :waited true :vel [0 0])))

      :else
      (let [gp     (goal-point w)
            dg     (pt-dist gp (:pos w))]
        (if (or (<= dg arrive-m) (and idle? (nil? (:aim w))))
          (arrive w)
          (if (and (:aim w) (<= (pt-dist (:aim w) (:pos w)) arrive-m))
            (assoc w :aim nil)
            w))))))

;; -- Senses -------------------------------------------------------------

(defn forward [heading]
  [(js/Math.cos heading) (- (js/Math.sin heading))])

(defn facing [w]
  (or (:facing w) (:heading w)))

(defn sees? [w o]
  (let [s (or (:senses w) default-senses)
        [wx wz] (:pos w) [ox oz] (:pos o)
        dx (- ox wx) dz (- oz wz)
        d  (js/Math.hypot dx dz)]
    (cond
      (> d (:sight-m s)) false
      (zero? d) true
      :else
      (let [[fx fz] (forward (facing w))
            dot  (/ (+ (* fx dx) (* fz dz)) d)
            half (/ (* (:sight-deg s) (/ js/Math.PI 180)) 2)]
        (>= dot (- (js/Math.cos half) 1e-9))))))

(defn hear-range [w o]
  (let [s (or (:senses w) default-senses)
        [vx vz] (or (:vel o) [0 0])
        speed (js/Math.hypot vx vz)]
    (* (:hear-m s) (max 1 (/ speed walk-pace)))))

(defn hears? [w o]
  (let [[wx wz] (:pos w) [ox oz] (:pos o)
        d (js/Math.hypot (- ox wx) (- oz wz))]
    (<= d (hear-range w o))))

(defn perceive [w others dt]
  (let [dt'    (max 0 dt)
        [vx vz] (:vel w)
        speed  (js/Math.hypot vx vz)
        w      (if (>= speed face-min-speed) (assoc w :facing (yaw vx vz)) w)
        s      (or (:senses w) default-senses)
        react-s  (:react-s s)
        forget-s (:forget-s s)
        aware  (:aware w)
        by-id  (into {} (map (fn [o] [(:id o) o])) (remove #(= (:id %) (:id w)) others))
        ids    (into (set (keys by-id)) (keys aware))
        aware' (reduce
                 (fn [acc id]
                   (let [o     (get by-id id)
                         e     (get aware id)
                         seen  (and o (sees? w o))
                         heard (and o (hears? w o))]
                     (cond
                       (or seen heard)
                       (let [on' (+ (or (:on e) 0) dt')]
                         (assoc acc id {:on on' :off 0
                                        :noticed (or (true? (:noticed e))
                                                     (>= on' (- react-s 1e-9)))
                                        :how (if seen :sight :hear)}))

                       (true? (:noticed e))
                       (let [off' (+ (:off e) dt')]
                         (if (>= off' (- forget-s 1e-9))
                           acc
                           (assoc acc id {:on 0 :off off' :noticed true :how nil})))

                       :else
                       acc)))
                 {} ids)]
    (assoc w :aware aware'
             :glance (boolean (some #(and (:noticed %) (= :hear (:how %))) (vals aware'))))))

(defn perceive-all [ws dt]
  (let [ws (vec ws) n (count ws)]
    (vec (map-indexed
           (fn [i w]
             (perceive w (into (subvec ws 0 i) (subvec ws (inc i) n)) dt))
           ws))))

(defn noticed [w others]
  (filterv #(true? (get-in (:aware w) [(:id %) :noticed])) others))

(defn cone-points [[x z] heading sight-m sight-deg n]
  (let [n'   (max 1 n)
        half (/ (* sight-deg (/ js/Math.PI 180)) 2)
        step (/ (* 2 half) n')
        rim  (mapv (fn [k]
                      (let [a (+ (- heading half) (* k step))]
                        [(+ x (* sight-m (js/Math.cos a))) (- z (* sight-m (js/Math.sin a)))]))
                    (range (inc n')))]
    (into (into [[x z]] rim) [[x z]])))

;; -- Avoidance planning ------------------------------------------------------

(defn tint-hex [base tint]
  (if (and (integer? base) (<= 0 base 0xffffff)
           (integer? tint) (<= 0 tint 0xffffff))
    (let [br  (bit-and (bit-shift-right base 16) 255)
          bg  (bit-and (bit-shift-right base 8) 255)
          bb  (bit-and base 255)
          tr  (bit-and (bit-shift-right tint 16) 255)
          tg  (bit-and (bit-shift-right tint 8) 255)
          tb  (bit-and tint 255)
          mul (fn [c t] (js/Math.round (/ (* c t) 255)))]
      (bit-or (bit-shift-left (mul br tr) 16)
              (bit-shift-left (mul bg tg) 8)
              (mul bb tb)))
    base))

(defn figure-hexes [palette tint]
  (mapv (fn [part]
          (let [c    (:color part)
                base (get palette c)]
            (if (= c :eye) base (tint-hex base tint))))
        (:parts figure)))

;; -- Generation gate ---------------------------------------------------------

(def empty-gate {:queue [] :in-flight nil :last-start nil :next-id 0})

(defn gate-enqueue [gate]
  (let [id (:next-id gate)]
    [(-> gate (update :queue conj id) (update :next-id inc)) id]))

(defn gate-start [gate frame]
  (if (and (nil? (:in-flight gate)) (seq (:queue gate)) (not= (:last-start gate) frame))
    (let [id (first (:queue gate))]
      [(assoc gate :queue (vec (rest (:queue gate))) :in-flight id :last-start frame) id])
    [gate nil]))

(defn gate-done [gate id]
  (if (= id (:in-flight gate))
    (assoc gate :in-flight nil)
    gate))

(defn gate-queued [gate]
  (count (:queue gate)))

(defn walkable? [g base step-max]
  (and (number? g) (<= (js/Math.abs (- g base)) step-max)))

(defn plan [w others statics cfg]
  (let [route (:route w)]
    (if (or (< (count route) 2) (> (:pause w) 0))
      (assoc w :aim nil)
      (let [G (target w)
            P (:pos w)
            r (:radius w)
            g (a/free-goal P G r statics)
            w1 (assoc w :goal (when (not= g G) g))]
        (if (<= (pt-dist P g) arrive-m)
          (assoc w1 :aim nil)
          (let [speed      (or (:speed w) (:speed cfg))
                horizon-s  (:horizon-s cfg)
                other-circles (mapv (fn [o] {:at (:pos o) :r (:radius o) :vel (:vel o)}) others)
                circles    (into statics other-circles)
                b          (a/path-block P g speed horizon-s r circles)]
            (if (nil? b)
              (assoc w1 :aim nil :side nil :wait 0 :waited false)
              (let [clear  (+ (:clear b) a/pad)
                    sides  (if (= (:side w) :left) [:left :right] [:right :left])
                    taken  (reduce
                             (fn [acc side]
                               (or acc
                                   (let [D (a/tangent-aim P (:at b) clear side)
                                         [Dx Dz] D
                                         ground-ok (or (not (fn? (:ground-at cfg)))
                                                       (not (number? (:y w)))
                                                       (walkable? ((:ground-at cfg) Dx Dz) (:y w)
                                                                  (or (:step-max cfg) 0.4)))]
                                     (when (and ground-ok
                                                (not (a/inside? D r statics))
                                                (nil? (a/path-block P D speed horizon-s r circles)))
                                       [D side]))))
                             nil sides)]
                (if taken
                  (let [[D side] taken]
                    (assoc w1 :aim D :side side :wait 0 :waited false))
                  (cond
                    (:waited w)      (arrive w1)
                    (> (:wait w) 0)  (assoc w1 :aim nil)
                    :else            (assoc w1 :aim nil :side nil :wait wait-s)))))))))))

(defn plan-all [ws statics cfg]
  (let [ws (vec ws) n (count ws)]
    (vec (map-indexed
           (fn [i w]
             (let [others (into (subvec ws 0 i) (subvec ws (inc i) n))]
               (plan w (noticed w others) statics cfg)))
           ws))))

(defn replan [before after statics cfg dt acc]
  (let [after (perceive-all after dt)]
    (if (or (>= (+ acc dt) (- plan-every-s 1e-9))
            (some (fn [[b a]] (not= (:leg b) (:leg a))) (map vector before after)))
      [(plan-all after statics cfg) 0]
      [after (+ acc dt)])))

(defn advance [ws statics cfg dt acc]
  (replan ws (mapv #(step % (or (:speed %) (:speed cfg)) dt) ws) statics cfg dt acc))

(defn detours [ws]
  (count (filter #(some? (:aim %)) ws)))

(defn steer-command [sent w]
  (cond
    (or (< (count (:route w)) 2) (> (:pause w) 0))
    nil

    (> (:wait w) 0)
    (if (= sent [:stop]) nil [:stop])

    :else
    (let [p (steer-point w)
          q (when (and (vector? sent) (= (first sent) :move)) (second sent))]
      (if (and q (<= (pt-dist q p) resend-m))
        nil
        [:move p]))))

;; -- Ground sensing / smoothing ---------------------------------------------

(defn ground-name? [s prefixes]
  (boolean (and (string? s) (some #(str/starts-with? s %) prefixes))))

(defn ground-height [hits prefixes]
  (when (seq hits)
    (let [ys (keep (fn [[y n]] (when (ground-name? n prefixes) y)) hits)]
      (when (seq ys) (apply max ys)))))

(defn sense-ground [w hits prefixes]
  (if-let [h (ground-height hits prefixes)]
    (assoc w :ground h)
    w))

(defn smooth [prev target dt]
  (cond
    (nil? prev) target
    (nil? target) prev
    :else
    (let [dt' (max 0 dt)]
      (+ prev (* (- target prev) (- 1 (js/Math.exp (- (/ dt' smooth-tau)))))))))

(defn settle [w dt]
  (assoc w :y (smooth (:y w) (:ground w) dt)))

;; -- Flag / progress / reasons ----------------------------------------------

(defn url-flag? [search name]
  (.has (js/URLSearchParams. (or search "")) name))

(defn bricks-flag?
  "MotionBricks is the default (Michael, Nov 7: the walkers are the real look
   now); `?kinematic` asks for the plain stand-in figures. `?bricks` is still
   accepted (it's the default anyway)."
  [search]
  (not (.has (js/URLSearchParams. (or search "")) "kinematic")))

(defn paths-flag? [search]
  (.has (js/URLSearchParams. (or search "")) "paths"))

(defn load-fraction [stage loaded total]
  (case stage
    "runtime" 0
    "model"   (if (and (number? total) (pos? total)) (/ loaded total) 0)
    "warmup"  1
    nil))

(defn short-reason [msg]
  (let [first-line (first (str/split (str msg) #"\n" 2))
        trimmed    (str/trim (or first-line ""))]
    (cond
      (str/blank? trimmed)     "unknown"
      (> (count trimmed) 40)  (str (subs trimmed 0 39) "…")
      :else                    trimmed)))

;; -- HUD --------------------------------------------------------------------

(defn char-errors-text [errs]
  (when (seq errs)
    (str "mannequin for "
         (str/join ", " (map (fn [e] (str (:name e) " (" (:reason e) ")")) errs)))))

(defn- base-hud-text [status]
  (case (:mode status)
    :kinematic "motion: kinematic"

    :loading
    (let [f (:progress status)
          f (if (number? f) f 0)
          f (max 0 (min 1 f))
          n (js/Math.round (* 100 f))]
      (str "motion: loading model " n "%"))

    :bricks
    (let [backend (:backend status)
          n       (:walkers status)
          gen-ms  (:gen-ms status)
          q       (:queue status)
          ce      (char-errors-text (:char-errors status))]
      (str "motion: bricks"
           (when (and (string? backend) (not (str/blank? backend))) (str " " backend))
           " · " n " walker" (when (not= n 1) "s")
           (when (number? gen-ms) (str " · gen " (js/Math.round gen-ms) " ms"))
           (when (and (number? q) (> q 0)) (str " · queue " q))
           (when ce (str " · " ce))))

    :failed
    (str "motion: kinematic (bricks failed: " (:reason status) ")")

    nil))

(defn hud-text [status]
  (when (map? status)
    (when-let [base (base-hud-text status)]
      (let [n (:detours status)]
        (if (and (number? n) (>= n 1))
          (str base " · " n " detour" (when (not= n 1) "s"))
          base)))))
