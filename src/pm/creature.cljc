(ns pm.creature
  "Pure species rules: validation, MuJoCo MJCF generation, mass/geometry math,
   the z-up (MuJoCo) <-> y-up (three.js) frame conversion, and look parts for
   drawing. Runs in bb (scripts/creature_mjcf.clj) and cljs (the game). No
   atoms, no I/O, no species names."
  (:require [clojure.string :as str]
            #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])))

;; -- Constants ----------------------------------------------------------

(def timestep 0.002)                             ; s, MuJoCo <option timestep>
(def ground-half 2)                              ; m, plane half-size: a 4 m square
(def origin {:at [0 0 0] :yaw 0 :drop-m 0.4})    ; the trainer's placement (bb task)

;; -- Math, through reader conditionals -----------------------------------

(def ^:private pi #?(:clj Math/PI :cljs js/Math.PI))
(defn- sqrt [x]   #?(:clj (Math/sqrt x)             :cljs (js/Math.sqrt x)))
(defn- asin [x]   #?(:clj (Math/asin x)             :cljs (js/Math.asin x)))
(defn- atan2 [y x] #?(:clj (Math/atan2 y x)          :cljs (js/Math.atan2 y x)))
(defn- cos [x]    #?(:clj (Math/cos x)              :cljs (js/Math.cos x)))
(defn- sin [x]    #?(:clj (Math/sin x)              :cljs (js/Math.sin x)))
(defn- abs* [x]   #?(:clj (Math/abs (double x))     :cljs (js/Math.abs x)))

(defn- ex-message* [e]
  #?(:clj (.getMessage ^Throwable e) :cljs (or (.-message e) (str e))))

(defn- clamp [x lo hi] (max lo (min hi x)))

;; -- Validation -----------------------------------------------------------

(defn- valid-vec3? [v]
  (and (vector? v) (= 3 (count v)) (every? number? v)))

(defn- valid-range? [r]
  (and (vector? r) (= 2 (count r)) (every? number? r)
       (let [[lo hi] r] (and (<= -180 lo) (< lo hi) (<= hi 180)))))

(defn- valid-joint? [j]
  (and (map? j)
       (valid-vec3? (:axis j))
       (not (every? zero? (:axis j)))
       (valid-range? (:range j))))

(defn- valid-capsule? [c]
  (and (map? c)
       (valid-vec3? (:from c))
       (valid-vec3? (:to c))
       (not= (:from c) (:to c))
       (number? (:r c))
       (pos? (:r c))))

(defn- valid-joints-entry? [j]
  (and (map? j) (keyword? (:name j)) (valid-vec3? (:axis j))
       (not (every? zero? (:axis j))) (valid-range? (:range j))))

(defn- joint-problems
  "P14/P16/P17/P18 for a non-root body: 0 or more problem strings."
  [nm b]
  (let [has-joint?  (some? (:joint b))
        has-joints? (contains? b :joints)]
    (cond
      (and has-joint? has-joints?)
      [(str "body " (pr-str nm) ": use :joint or :joints, not both")]

      has-joints?
      (let [js (:joints b)]
        (if-not (and (vector? js) (seq js))
          [(str "body " (pr-str nm) ": :joints must be a non-empty vector")]
          (into []
                (keep-indexed
                  (fn [j entry]
                    (when-not (valid-joints-entry? entry)
                      (str "body " (pr-str nm) ": joint " j
                           ": needs a keyword :name, a non-zero :axis [x y z] and :range [lo hi] with -180 <= lo < hi <= 180"))))
                js)))

      has-joint?
      (if (valid-joint? (:joint b))
        []
        [(str "body " (pr-str nm)
              ": :joint needs a non-zero :axis [x y z] and :range [lo hi] with -180 <= lo < hi <= 180")])

      :else [])))

(defn body-joints
  "[{:name :axis :range [lo-deg hi-deg]} ...] for a body's hinges, in MuJoCo
   order: a :joints vector as listed, or a single :joint named after the
   body, or [] when welded. When both are present (invalid), :joints wins."
  [body]
  (cond
    (seq (:joints body)) (vec (:joints body))
    (:joint body)        [(assoc (:joint body) :name (:name body))]
    :else                []))

(defn- has-any-joint? [b]
  (or (some? (:joint b)) (and (vector? (:joints b)) (seq (:joints b)))))

(defn- joint-name-pass
  "[p19-problems known-names] over non-root bodies, :bodies vector order
   then listed order."
  [bs]
  (reduce
    (fn [[probs seen] [i b]]
      (if (zero? i)
        [probs seen]
        (reduce
          (fn [[probs seen] j]
            (let [jn (:name j)]
              (if (keyword? jn)
                (if (contains? seen jn)
                  [(conj probs (str "joint " (pr-str jn) ": duplicate name")) seen]
                  [probs (conj seen jn)])
                [probs seen])))
          [probs seen]
          (body-joints b))))
    [[] #{}]
    (map-indexed vector bs)))

(defn- valid-up? [up]
  (or (nil? up) (and (valid-vec3? up) (not (every? zero? up)))))

(defn- reflex-entry-problems [i r body-names known-joint-names]
  (let [tag (str "reflex " i)]
    (cond-> []
      (not= :head-level (:kind r))
      (conj (str tag ": unknown :kind " (pr-str (:kind r))))

      (not (contains? body-names (:body r)))
      (conj (str tag ": unknown body " (pr-str (:body r))))

      true
      (into (let [js (:joints r)]
              (if-not (and (vector? js) (seq js))
                [(str tag ": :joints must be a non-empty vector of joint names")]
                (keep (fn [j] (when-not (contains? known-joint-names j)
                                (str tag ": unknown joint " (pr-str j))))
                      js))))

      (not (valid-up? (:up r)))
      (conj (str tag ": :up must be a non-zero [x y z]"))

      (not (and (number? (:lift-m r)) (>= (:lift-m r) 0)))
      (conj (str tag ": :lift-m must be a number >= 0"))

      (not (and (number? (:gain r)) (pos? (:gain r))))
      (conj (str tag ": :gain must be a positive number"))

      (not (and (number? (:w-lift r)) (>= (:w-lift r) 0)))
      (conj (str tag ": :w-lift must be a number >= 0"))

      (not (and (number? (:onset-s r)) (>= (:onset-s r) 0)))
      (conj (str tag ": :onset-s must be a number >= 0")))))

(defn- reflex-problems [sp known-joint-names]
  (let [rx (:reflexes sp)]
    (cond
      (nil? rx) []
      (not (and (vector? rx) (every? map? rx))) [":reflexes must be a vector of maps"]
      :else
      (let [body-names (set (map :name (:bodies sp)))]
        (into [] (mapcat (fn [i r] (reflex-entry-problems i r body-names known-joint-names))
                          (range) rx))))))

(defn problems
  "A vector of problem strings, [] when `sp` is valid. Never throws."
  [sp]
  (let [sp          (if (map? sp) sp {})
        kind-ok?    (keyword? (:kind sp))
        density     (:density sp)
        density-ok? (and (number? density) (pos? density))
        kp          (:kp sp)
        kp-ok?      (and (number? kp) (pos? kp))
        damping     (:damping sp)
        damping-ok? (and (number? damping) (>= damping 0))
        colors      (:colors sp)
        colors-ok?  (and (map? colors)
                          (every? keyword? (keys colors))
                          (every? (fn [v] (and (integer? v) (<= 0 v 0xffffff))) (vals colors)))
        bodies      (:bodies sp)
        bodies-ok?  (and (vector? bodies) (seq bodies) (every? map? bodies))
        top (cond-> []
              (not kind-ok?)    (conj ":kind must be a keyword")
              (not density-ok?) (conj ":density must be a positive number")
              (not kp-ok?)      (conj ":kp must be a positive number")
              (not damping-ok?) (conj ":damping must be a number >= 0")
              (not colors-ok?)  (conj ":colors must map keywords to 0xRRGGBB ints"))]
    (if-not bodies-ok?
      (conj top ":bodies must be a non-empty vector")
      (let [[body-probs _]
            (reduce
              (fn [[probs seen] [i b]]
                (let [nm    (:name b)
                      root? (zero? i)
                      probs (if (and root? (or (:parent b) (:at b) (:joint b) (contains? b :joints)))
                              (conj probs (str "root " (pr-str nm) " must have no :parent, :at or :joint"))
                              probs)]
                  (if-not (keyword? nm)
                    [(conj probs (str "body " i ": :name must be a keyword")) seen]
                    (let [probs (if (contains? seen nm)
                                  (conj probs (str "body " (pr-str nm) ": duplicate name"))
                                  probs)
                          probs (if (or root? (contains? seen (:parent b)))
                                  probs
                                  (conj probs (str "body " (pr-str nm) ": unknown parent " (pr-str (:parent b)))))
                          probs (if (or root? (valid-vec3? (:at b)))
                                  probs
                                  (conj probs (str "body " (pr-str nm) ": :at must be [x y z]")))
                          probs (if (valid-capsule? (:capsule b))
                                  probs
                                  (conj probs (str "body " (pr-str nm)
                                                    ": :capsule needs :from, :to ([x y z], different) and :r > 0")))
                          probs (if (and colors-ok? (not (contains? colors (:color b))))
                                  (conj probs (str "body " (pr-str nm) ": :color " (pr-str (:color b)) " is not in :colors"))
                                  probs)
                          probs (if root? probs (into probs (joint-problems nm b)))]
                      [probs (conj seen nm)]))))
              [[] #{}]
              (map-indexed vector bodies))
            [p19-probs known-names] (joint-name-pass bodies)
            all (-> top (into body-probs) (into p19-probs))
            all (if (some has-any-joint? bodies) all (conj all "needs at least one :joint"))]
        (into all (reflex-problems sp known-names))))))

(defn check!
  "`sp` if valid, else throws the ex-info `parse` throws."
  [sp]
  (let [probs (problems sp)]
    (if (empty? probs)
      sp
      (throw (ex-info (str "bad species" (when (keyword? (:kind sp)) (str " " (:kind sp))) ": " (first probs))
                       {:problems probs})))))

(defn parse
  "The species map (as read) when `text` is valid EDN for a valid species."
  [text]
  (let [sp (try (edn/read-string text)
                (catch #?(:clj Exception :cljs :default) e
                  (throw (ex-info (str "bad species: not EDN (" (ex-message* e) ")") {:problems []}))))]
    (if (map? sp)
      (check! sp)
      (throw (ex-info "bad species: not a map" {:problems []})))))

;; -- Structure --------------------------------------------------------------

(defn- subtree [bs name]
  (let [node (first (filter #(= name (:name %)) bs))
        kids (filterv #(= name (:parent %)) bs)]
    (into [node] (mapcat #(subtree bs (:name %)) kids))))

(defn bodies
  "The bodies in depth-first order (MuJoCo's body order: id = index + 1)."
  [sp]
  (let [bs (:bodies sp)]
    (subtree bs (:name (first bs)))))

(defn joints
  "[{:name :body :axis :range [lo-rad hi-rad]} ...] in `bodies` (MuJoCo hinge)
   order, each body's `body-joints` in order."
  [sp]
  (into []
        (mapcat (fn [b]
                  (mapv (fn [j] {:name  (:name j)
                                 :body  (:name b)
                                 :axis  (:axis j)
                                 :range (mapv #(* % (/ pi 180)) (:range j))})
                        (body-joints b))))
        (bodies sp)))

(defn ctrl-ranges [sp] (mapv :range (joints sp)))

(defn counts [sp]
  (let [nb (count (bodies sp))
        nj (count (joints sp))]
    {:nbody (inc nb) :njnt (inc nj) :nu nj :nq (+ 7 nj) :nv (+ 6 nj) :ngeom (inc nb)}))

(defn reflexes
  "The compiled :reflexes vector ([] when there is no :reflexes key), with
   indices resolved from `joints`. Assumes `sp` is valid."
  [sp]
  (let [bs      (bodies sp)
        idx-of  (into {} (map-indexed (fn [i b] [(:name b) i])) bs)
        by-name (into {} (map (fn [b] [(:name b) b])) bs)
        jnts    (joints sp)
        k-of    (into {} (map-indexed (fn [k j] [(:name j) k])) jnts)
        ranges  (mapv :range jnts)]
    (mapv
      (fn [r]
        (let [acts (mapv k-of (:joints r))]
          {:kind    (:kind r)
           :body    (:body r)
           :body-id (inc (get idx-of (:body r)))
           :capsule (:capsule (get by-name (:body r)))
           :up      (or (:up r) [0 0 1])
           :joints  (vec (:joints r))
           :acts    acts
           :qpos    (mapv #(+ 7 %) acts)
           :dofs    (mapv #(+ 6 %) acts)
           :ranges  (mapv ranges acts)
           :lift-m  (:lift-m r)
           :gain    (:gain r)
           :w-lift  (:w-lift r)
           :onset-s (:onset-s r)}))
      (:reflexes sp))))

(defn- vec3-sub [[ax ay az] [bx by bz]] [(- ax bx) (- ay by) (- az bz)])
(defn- vec3-len [[x y z]] (sqrt (+ (* x x) (* y y) (* z z))))

(defn capsule-mass [sp body]
  (let [{:keys [from to r]} (:capsule body)
        l (vec3-len (vec3-sub to from))]
    (* (:density sp) pi r r (+ l (/ (* 4 r) 3)))))

(defn body-masses [sp]
  (mapv (fn [b] [(:name b) (capsule-mass sp b)]) (bodies sp)))

(defn- origin-at [by-name name]
  (let [b (get by-name name)
        p (:parent b)]
    (if (nil? p)
      [0 0 0]
      (let [[ox oy oz] (origin-at by-name p)
            [ax ay az] (:at b)]
        [(+ ox ax) (+ oy ay) (+ oz az)]))))

(defn rest-bottom
  "The lowest point (m, torso origin = 0, rest pose) over all bodies and
   capsule ends, rotated by `roll-deg` about the body's own x (0 by default)."
  ([sp] (rest-bottom sp 0))
  ([sp roll-deg]
   (let [bs      (bodies sp)
         by-name (into {} (map (fn [b] [(:name b) b])) bs)
         phi     (* roll-deg (/ pi 180))
         sinphi  (sin phi)
         cosphi  (cos phi)]
     (apply min
            (for [b bs
                  :let [[_ oy oz] (origin-at by-name (:name b))
                        {:keys [from to r]} (:capsule b)]
                  p [from to]]
              (- (+ (* (+ oy (nth p 1)) sinphi) (* (+ oz (nth p 2)) cosphi)) r))))))

;; -- Frames: z-up (MuJoCo) <-> y-up (three.js) -----------------------------

(defn mj->three [[x y z]] [x z (- y)])
(defn three->mj [[x y z]] [x (- z) y])
(defn mj-quat->three [[w x y z]] [x z (- y) w])

;; -- Placement --------------------------------------------------------------

(defn spawn
  "{:ground [x y z] :torso [x y z] :quat [w x y z]}, all in the MuJoCo frame.
   :roll on `place` (degrees, default 0) rotates about the body's own x
   (forward), applied before :yaw."
  [sp place]
  (let [[gx gy gz] (three->mj (:at place))
        roll (or (:roll place) 0)
        tz   (+ gz (:drop-m place) (- (rest-bottom sp roll)))
        psi  (* (:yaw place) (/ pi 180))
        phi  (* roll (/ pi 180))
        cy   (cos (/ psi 2)) sy (sin (/ psi 2))
        cr   (cos (/ phi 2)) sr (sin (/ phi 2))]
    {:ground [gx gy gz]
     :torso  [gx gy tz]
     :quat   [(* cy cr) (* cy sr) (* sy sr) (* cr sy)]}))

;; -- MJCF ---------------------------------------------------------------

(defn fmt
  "A number as XML text: 6 decimals, trailing zeros and dot stripped, -0 -> 0."
  [x]
  (let [s #?(:clj (String/format java.util.Locale/ROOT "%.6f" (object-array [(double x)]))
             :cljs (.toFixed x 6))
        s (str/replace s #"0+$" "")
        s (str/replace s #"\.$" "")]
    (if (= s "-0") "0" s)))

(defn- v [coll] (str/join " " (map fmt coll)))
(defn- ind [n] (apply str (repeat n "  ")))

(defn mjcf
  "The MJCF XML string for `sp` placed at `place`. Throws (via `check!`) on a
   bad species."
  [sp place]
  (let [sp       (check! sp)
        density  (:density sp)
        damping  (:damping sp)
        kp       (:kp sp)
        bs       (:bodies sp)
        children (fn [pname] (filterv #(= pname (:parent %)) bs))
        sw       (spawn sp place)
        geom     (fn [n b]
                   (let [{:keys [from to r]} (:capsule b)]
                     (str (ind n) "<geom name=\"" (name (:name b)) "\" type=\"capsule\" fromto=\""
                          (v from) " " (v to) "\" size=\"" (fmt r) "\" density=\"" (fmt density)
                          "\" contype=\"0\" conaffinity=\"1\"/>")))
        joint-el (fn [n j]
                   (let [[lo hi] (:range j)
                         lo-r    (* lo (/ pi 180))
                         hi-r    (* hi (/ pi 180))]
                     (str (ind n) "<joint name=\"" (name (:name j)) "\" type=\"hinge\" axis=\""
                          (v (:axis j)) "\" limited=\"true\" range=\"" (fmt lo-r) " " (fmt hi-r)
                          "\" damping=\"" (fmt damping) "\"/>")))
        body-xml (fn body-xml [b depth]
                   (let [lvl       (+ 2 depth)
                         inner     (inc lvl)
                         nm        (name (:name b))
                         root?     (zero? depth)
                         open      (if root?
                                     (str (ind lvl) "<body name=\"" nm "\" pos=\"" (v (:torso sw))
                                          "\" quat=\"" (v (:quat sw)) "\">")
                                     (str (ind lvl) "<body name=\"" nm "\" pos=\"" (v (:at b)) "\">"))
                         joint-l   (if root?
                                     [(str (ind inner) "<freejoint name=\"root\"/>")]
                                     (mapv #(joint-el inner %) (body-joints b)))
                         kid-lines (mapcat #(body-xml % (inc depth)) (children (:name b)))
                         close     (str (ind lvl) "</body>")]
                     (into [open] (concat joint-l [(geom inner b)] kid-lines [close]))))
        jnts     (joints sp)
        actuator-lines (mapv (fn [j]
                                (str (ind 2) "<position name=\"" (name (:name j)) "\" joint=\"" (name (:name j))
                                     "\" kp=\"" (fmt kp) "\" ctrllimited=\"true\" ctrlrange=\""
                                     (fmt (first (:range j))) " " (fmt (second (:range j))) "\"/>"))
                              jnts)
        lines (concat
                [(str "<mujoco model=\"" (name (:kind sp)) "\">")
                 (str (ind 1) "<compiler angle=\"radian\" autolimits=\"true\"/>")
                 (str (ind 1) "<option timestep=\"" (fmt timestep) "\" integrator=\"implicitfast\"/>")
                 (str (ind 1) "<worldbody>")
                 (str (ind 2) "<geom name=\"ground\" type=\"plane\" pos=\"" (v (:ground sw))
                      "\" size=\"" (fmt ground-half) " " (fmt ground-half) " 0.1\" contype=\"1\" conaffinity=\"0\"/>")]
                (body-xml (first bs) 0)
                [(str (ind 1) "</worldbody>")
                 (str (ind 1) "<actuator>")]
                actuator-lines
                [(str (ind 1) "</actuator>")
                 "</mujoco>"])]
    (str (str/join "\n" lines) "\n")))

;; -- Drawing parts (pm.plan look-part style, three.js frame) -----------------

(defn body-part
  "One part for `body`, in the body's own frame converted to three.js, in
   pm.plan's part shape with :color resolved to the species' hex int."
  [sp body]
  (let [{:keys [from to r]} (:capsule body)
        l       (vec3-len (vec3-sub to from))
        mid     (mapv (fn [a b] (/ (+ a b) 2)) from to)
        [dx dy dz] (mapv #(/ % l) (mj->three (vec3-sub to from)))
        vertical? (and (< (abs* dx) 1e-9) (< (abs* dz) 1e-9))
        c-deg   (* (asin (clamp dy -1 1)) (/ 180 pi))
        b-deg   (if vertical? 0 (* (atan2 (- dz) dx) (/ 180 pi)))]
    {:shape :capsule
     :size  [(+ l (* 2 r)) (* 2 r) (* 2 r)]
     :at    (mj->three mid)
     :rot   [0 b-deg c-deg]
     :color (get (:colors sp) (:color body))}))

(defn body-parts [sp]
  (vec (map-indexed (fn [i b] {:name (:name b) :id (inc i) :part (body-part sp b)}) (bodies sp))))
