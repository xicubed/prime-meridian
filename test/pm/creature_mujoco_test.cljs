(ns pm.creature-mujoco-test
  (:require [cljs.test :refer [deftest is async]]
            [pm.creature :as c]
            [pm.creature-motion :as cm]
            [pm.species :as species]))

(defn approx=
  ([a b] (approx= a b 1e-6))
  ([a b eps]
   (cond
     (and (number? a) (number? b)) (< (js/Math.abs (- a b)) eps)
     (and (sequential? a) (sequential? b))
     (and (= (count a) (count b)) (every? true? (map #(approx= %1 %2 eps) a b)))
     :else (= a b))))

(def P {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})
(def ground-z 1.79)

(def horse (c/parse (get species/texts :horse)))
(def dog (c/parse (get species/texts :dog)))

(defn no-effort [sp]
  (update sp :reflexes (fn [rx] (mapv #(dissoc % :effort) rx))))

(defn import-mj []
  ;; require(), not import(): shadow's :node-test runs in a vm context with no dynamic-import
  ;; callback (ERR_VM_DYNAMIC_IMPORT_CALLBACK_MISSING); Node 22.12+ can require() this ESM
  ;; package (Hal, manager fix). .default() returns a promise of the MuJoCo API.
  (let [^js mod (js/require (str (.cwd js/process) "/node_modules/@mujoco/mujoco/mujoco.js"))]
    ((.-default mod))))

(defn aidx
  ([^js arr n] (aidx arr 0 n))
  ([^js arr start n] (vec (for [i (range start (+ start n))] (aget arr i)))))

(defn check-counts-masses-ranges [^js mj sp]
  (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp P))
        ^js data (new (.-MjData mj) model)
        counts (c/counts sp)
        masses (c/body-masses sp)
        ranges (c/ctrl-ranges sp)]
    (is (= (:nbody counts) (.-nbody model)))
    (is (= (:njnt counts) (.-njnt model)))
    (is (= (:nu counts) (.-nu model)))
    (is (= (:nq counts) (.-nq model)))
    (is (= (:nv counts) (.-nv model)))
    (is (= (:ngeom counts) (.-ngeom model)))
    (is (approx= 0 (aget (.-body_mass model) 0)))
    (is (every? true?
                (for [i (range 1 (inc (count masses)))]
                  (approx= (second (nth masses (dec i))) (aget (.-body_mass model) i)))))
    (is (every? true?
                (for [j (range (count ranges))]
                  (let [[lo hi] (nth ranges j)]
                    (and (approx= lo (aget (.-jnt_range model) (+ (* 2 j) 2)))
                         (approx= hi (aget (.-jnt_range model) (+ (* 2 j) 3))))))))
    [model data]))

;; R1

(deftest r1-horse-mjcf-compiles
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (let [[^js model ^js data] (check-counts-masses-ranges mj horse)]
                   (is (approx= [4.5 8 3.03] (aidx (.-qpos data) 0 3)))
                   (is (approx= [1 0 0 0] (aidx (.-qpos data) 3 4)))
                   (.delete data) (.delete model))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R2

(deftest r2-dog-mjcf-compiles
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (let [[^js model ^js data] (check-counts-masses-ranges mj dog)]
                   (is (approx= [4.5 8 2.63] (aidx (.-qpos data) 0 3)))
                   (.delete data) (.delete model))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R3

(defn check-rest [^js mj sp lo hi r]
  (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp P))
        ^js data (new (.-MjData mj) model)
        ok? (loop [i 0 ok true]
              (if (>= i 1000)
                ok
                (do (.mj_step mj model data)
                    (let [z (aget (.-xpos data) 5)]
                      (recur (inc i) (and ok (not (js/isNaN z)) (>= (- z ground-z) r)))))))]
    (is ok?)
    (let [^js qpos (.-qpos data)
          nq (.-nq model)]
      (is (every? true? (for [i (range nq)] (not (js/isNaN (aget qpos i)))))))
    (let [h (- (aget (.-xpos data) 5) ground-z)]
      (is (<= lo h hi)))
    (.delete data) (.delete model)))

(deftest r3-rest-with-zero-controls
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (check-rest mj horse 0.80 0.86 0.15)
                 (check-rest mj dog 0.40 0.46 0.10)
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R4

(deftest r4-yaw
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf dog {:at [0 0 0] :yaw 90 :drop-m 0.4}))
                       ^js data (new (.-MjData mj) model)]
                   (is (approx= [0 0 0.84 0.707107 0 0 0.707107] (aidx (.-qpos data) 7)))
                   (.delete data) (.delete model))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R5

(defn run-twitch [^js mj sp]
  (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp P))
        ^js data (new (.-MjData mj) model)
        ranges (c/ctrl-ranges sp)
        nu (count ranges)
        dt (/ 1.0 60)]
    (loop [frame 0 acc 0 ctrl (cm/controller :twitch nu 1) total 0]
      (if (>= frame 120)
        (let [^js qpos (.-qpos data)
              nq (.-nq model)
              all-finite? (every? true? (for [i (range nq)] (not (js/isNaN (aget qpos i)))))
              torso-h (- (aget (.-xpos data) 5) ground-z)
              hinge-moved? (some true? (for [i (range 7 (+ 7 nu))] (> (js/Math.abs (aget qpos i)) 0.05)))]
          (is (<= 999 total 1001))
          (is all-finite?)
          (is (> torso-h 0.05))
          (is hinge-moved?)
          (.delete data) (.delete model))
        (let [{:keys [steps acc]} (cm/fixed-steps acc dt)
              ctrl' (loop [c ctrl i 0]
                      (if (>= i steps)
                        c
                        (let [[c2 a] (cm/act c nil c/timestep)
                              ctrl-vals (cm/actions->ctrl a ranges)]
                          (dotimes [k (count ctrl-vals)]
                            (aset (.-ctrl data) k (nth ctrl-vals k)))
                          (.mj_step mj model data)
                          (recur c2 (inc i)))))]
          (recur (inc frame) acc ctrl' (+ total steps)))))))

(deftest r5-twitch-drives-horse-and-dog
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (run-twitch mj horse)
                 (run-twitch mj dog)
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R6

(defn run-reflex-on [^js mj sp roll]
  (let [place (assoc P :roll roll)
        ^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp place))
        ^js data (new (.-MjData mj) model)
        _ (.mj_forward mj model data)
        rx (c/reflexes sp)
        r (first rx)
        nv (.-nv model)
        ^js jp (new (.-DoubleBuffer mj) (* 3 nv))
        ^js jr (new (.-DoubleBuffer mj) (* 3 nv))
        ranges (c/ctrl-ranges sp)
        nu (count ranges)
        acts (set (:acts r))]
    (loop [i 0 ctrl (cm/reflex-controller (cm/controller :still nu 1) rx)]
      (if (>= i 1500)
        (let [s (cm/sense r (.-xpos data) (.-xmat data) (.GetView jp) (.GetView jr) ground-z)
              ^js qpos (.-qpos data)
              nq (.-nq model)
              finite? (every? true? (for [j (range nq)] (not (js/isNaN (aget qpos j)))))]
          (.delete jp) (.delete jr) (.delete data) (.delete model)
          {:sense s :finite? finite?})
        (do
          (.mj_jacBody mj model data jp jr (:body-id r))
          (let [obs {:nv nv :qpos (.-qpos data)
                     :senses [(cm/sense r (.-xpos data) (.-xmat data) (.GetView jp) (.GetView jr) ground-z)]}
                [ctrl' a] (cm/act ctrl obs c/timestep)
                vals (cm/actions->ctrl a ranges)]
            (dotimes [k nu]
              (aset (.-ctrl data) k (if (contains? acts k) (nth vals k) 0)))
            (.mj_step mj model data)
            (recur (inc i) ctrl')))))))

(deftest r6-reflex-on-head-lifts-and-levels
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (doseq [[sp need-clear] [[(no-effort horse) 0.175] [(no-effort dog) 0.105]]
                         roll [90 -90]]
                   (let [{:keys [sense finite?]} (run-reflex-on mj sp roll)]
                     (is finite?)
                     (is (>= (:clearance sense) need-clear))
                     (is (<= (cm/tilt-deg (:up sense)) 20))))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R7

(defn run-reflex-off [^js mj sp roll]
  (let [place (assoc P :roll roll)
        ^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp place))
        ^js data (new (.-MjData mj) model)
        _ (.mj_forward mj model data)
        r (first (c/reflexes sp))]
    (dotimes [k (.-nu model)] (aset (.-ctrl data) k 0))
    (dotimes [_ 1500] (.mj_step mj model data))
    (let [s (cm/sense r (.-xpos data) (.-xmat data) #js [] #js [] ground-z)]
      (.delete data) (.delete model)
      s)))

(deftest r7-reflex-off-head-does-not-level
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (doseq [[sp need-clear] [[(no-effort horse) 0.175] [(no-effort dog) 0.105]]]
                   (let [s (run-reflex-off mj sp 90)]
                     (is (> (cm/tilt-deg (:up s)) 20))
                     (is (< (:clearance s) need-clear))))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R8

(deftest r8-binding-and-jacobian-by-hand
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (let [obj-body (.. mj -mjtObj -mjOBJ_BODY -value)
                       obj-joint (.. mj -mjtObj -mjOBJ_JOINT -value)
                       col (fn [^js v nv d] [(aget v d) (aget v (+ nv d)) (aget v (+ (* 2 nv) d))])
                       check-species
                       (fn [sp expect3 expect6 expect7]
                         (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp P))
                               ^js data (new (.-MjData mj) model)
                               _ (.mj_forward mj model data)
                               nv (.-nv model)
                               body-id (.mj_name2id mj model obj-body "head")
                               ^js jp (new (.-DoubleBuffer mj) (* 3 nv))
                               ^js jr (new (.-DoubleBuffer mj) (* 3 nv))]
                           (.mj_jacBody mj model data jp jr body-id)
                           (let [jpv (.GetView jp)
                                 jrv (.GetView jr)]
                             (is (= 24 nv))
                             (is (= 3 body-id))
                             (is (= 3 (:body-id (first (c/reflexes sp)))))
                             (is (approx= [1 0 0] (col jpv nv 0)))
                             (is (approx= [0 0 0] (col jrv nv 0)))
                             (is (approx= expect3 (col jpv nv 3)))
                             (is (approx= [1 0 0] (col jrv nv 3)))
                             (is (approx= expect6 (col jpv nv 6)))
                             (is (approx= [0 0 1] (col jrv nv 6)))
                             (is (approx= expect7 (col jpv nv 7)))
                             (is (approx= [0 1 0] (col jrv nv 7)))
                             (is (approx= [0 0 0] (col jpv nv 8)))
                             (is (approx= [0 1 0] (col jrv nv 8)))
                             (is (approx= [0 0 0] (col jpv nv 9)))
                             (is (approx= [1 0 0] (col jrv nv 9)))
                             (is (approx= [0 0 0] (col jpv nv 10)))
                             (is (approx= [0 0 0] (col jrv nv 10)))
                             (let [joint-ids (mapv #(.mj_name2id mj model obj-joint %)
                                                    ["neck-bend" "neck-pitch" "head-pitch" "head-roll"])
                                   dofadrs (mapv #(aget (.-jnt_dofadr model) %) joint-ids)]
                               (is (= [6 7 8 9] dofadrs))
                               (is (= [6 7 8 9] (:dofs (first (c/reflexes sp)))))))
                           (.delete jp) (.delete jr)
                           [model data]))]
                   (let [[^js model1 ^js data1] (check-species horse [0 -0.32 0] [0 0.18 0] [0.24 0 -0.18])]
                     (.delete data1) (.delete model1))
                   (let [[^js model2 ^js data2] (check-species dog [0 -0.16 0] [0 0.09 0] [0.12 0 -0.09])]
                     ;; Pin the trap: plain Float64Arrays stay all zero.
                     (let [nv (.-nv model2)
                           ^js jp2 (js/Float64Array. (* 3 nv))
                           ^js jr2 (js/Float64Array. (* 3 nv))
                           body-id (.mj_name2id mj model2 obj-body "head")]
                       (.mj_jacBody mj model2 data2 jp2 jr2 body-id)
                       (is (every? true? (for [i (range (* 3 nv))] (= 0 (aget jp2 i)))))
                       (is (every? true? (for [i (range (* 3 nv))] (= 0 (aget jr2 i))))))
                     (.delete data2) (.delete model2)))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R9

(deftest r9-force-limits-compiled
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (doseq [[sp f] [[horse 35] [dog 9]]]
                   (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp P))
                         ^js fr (.-actuator_forcerange model)
                         ^js fl (.-actuator_forcelimited model)]
                     (dotimes [k 4]
                       (is (approx= (- f) (aget fr (* 2 k))))
                       (is (approx= f (aget fr (+ (* 2 k) 1))))
                       (is (= 1 (aget fl k))))
                     (doseq [k (range 4 18)]
                       (is (= 0 (aget fl k))))
                     (.delete model)))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R10, R11

(defn run-session [^js mj sp lift-m]
  (let [place (assoc P :roll 90)
        ^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp place))
        ^js data (new (.-MjData mj) model)
        _ (.mj_forward mj model data)
        rx (c/reflexes sp)
        r (first rx)
        nv (.-nv model)
        ^js jp (new (.-DoubleBuffer mj) (* 3 nv))
        ^js jr (new (.-DoubleBuffer mj) (* 3 nv))
        ranges (c/ctrl-ranges sp)
        nu (count ranges)
        acts (:acts r)
        acts-set (set acts)
        torso-cap (:capsule (first (c/bodies sp)))
        hind-cap (:capsule (nth (c/bodies sp) 7))]
    (loop [i 0
           ctrl (cm/reflex-controller (cm/controller :still nu 1) rx 1)
           armed true
           clears 0
           ever-resting? false
           active-after-resting? false
           max-force 0.0
           all-finite? true
           held-s 0.0
           torso-ok? true
           hind-ok? true]
      (if (>= i 15000)
        (let [final-energy (:energy (first (:efforts ctrl)))]
          (.delete jp) (.delete jr) (.delete data) (.delete model)
          {:clears clears :ever-resting? ever-resting? :active-after-resting? active-after-resting?
           :max-force max-force :all-finite? all-finite? :held-s held-s :final-energy final-energy
           :torso-ok? torso-ok? :hind-ok? hind-ok?})
        (do
          (.mj_jacBody mj model data jp jr (:body-id r))
          (let [s (cm/sense r (.-xpos data) (.-xmat data) (.GetView jp) (.GetView jr) ground-z)
                obs {:nv nv :qpos (.-qpos data) :senses [s] :forces (.-actuator_force data)}
                [ctrl' a] (cm/act ctrl obs c/timestep)
                vals (cm/actions->ctrl a ranges)
                mode (:mode (first (:efforts ctrl')))
                t (* (inc i) 0.002)
                step-force (reduce max 0.0 (map #(js/Math.abs (aget (.-actuator_force data) %)) acts))
                clearance (:clearance s)
                tilt (cm/tilt-deg (:up s))]
            (dotimes [k nu]
              (aset (.-ctrl data) k (if (contains? acts-set k) (nth vals k) 0)))
            (.mj_step mj model data)
            (let [^js qpos (.-qpos data)
                  nq (.-nq model)
                  finite-now? (every? true? (for [j (range nq)] (not (js/isNaN (aget qpos j)))))
                  gated? (>= t 1.0)
                  armed' (if gated?
                           (cond
                             (and armed (>= clearance lift-m)) false
                             (and (not armed) (< clearance (* 0.5 lift-m))) true
                             :else armed)
                           armed)
                  clears' (if (and gated? armed (>= clearance lift-m)) (inc clears) clears)
                  resting-now? (= mode :resting)
                  held?' (and (>= clearance (* 0.7 lift-m)) (<= tilt 20))
                  torso-low (cm/capsule-clearance (.-xpos data) (.-xmat data) 1 torso-cap ground-z)
                  torso-ok?' (if (>= t 3.0) (and torso-ok? (<= torso-low 0.02)) torso-ok?)
                  hind-low (cm/capsule-clearance (.-xpos data) (.-xmat data) 8 hind-cap ground-z)
                  hind-ok?' (if (>= t 3.0) (and hind-ok? (<= hind-low 0.02)) hind-ok?)]
              (recur (inc i) ctrl' armed' clears'
                     (or ever-resting? resting-now?)
                     (or active-after-resting? (and ever-resting? (= mode :active)))
                     (max max-force step-force)
                     (and all-finite? finite-now?)
                     (if held?' (+ held-s 0.002) held-s)
                     torso-ok?'
                     hind-ok?'))))))))

(deftest r10-horse-tries-rests-tries-again
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (let [{:keys [clears ever-resting? active-after-resting? max-force all-finite? torso-ok? hind-ok?]}
                       (run-session mj horse 0.25)]
                   (is (>= clears 2))
                   (is ever-resting?)
                   (is active-after-resting?)
                   (is all-finite?)
                   (is (<= max-force (+ 35 1e-6)))
                   (is (>= max-force 34.9))
                   (is torso-ok?)
                   (is hind-ok?))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

(deftest r11-dog-strong-and-steady
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (let [{:keys [held-s ever-resting? final-energy max-force all-finite? torso-ok? hind-ok?]}
                       (run-session mj dog 0.15)]
                   (is (>= held-s 20))
                   (is (not ever-resting?))
                   (is (> final-energy 0.8))
                   (is (<= max-force (+ 9 1e-6)))
                   (is all-finite?)
                   (is torso-ok?)
                   (is hind-ok?))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R12

(defn run-drop [^js mj sp]
  (let [place (assoc P :roll 0)
        ^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp place))
        ^js data (new (.-MjData mj) model)
        _ (.mj_forward mj model data)
        rx (c/reflexes sp)
        r (first rx)
        nv (.-nv model)
        ^js jp (new (.-DoubleBuffer mj) (* 3 nv))
        ^js jr (new (.-DoubleBuffer mj) (* 3 nv))
        ranges (c/ctrl-ranges sp)
        nu (count ranges)
        sigma (c/rest-fraction sp)
        kick (c/kick-above sp)
        torso-cap (:capsule (first (c/bodies sp)))
        hind-cap (:capsule (nth (c/bodies sp) 7))]
    (loop [i 0
           ctrl (cm/reflex-controller (cm/controller :twitch nu 1) rx 1)
           torso-ok? true
           hind-ok? true
           all-finite? true]
      (if (>= i 15000)
        (do
          (.delete jp) (.delete jr) (.delete data) (.delete model)
          {:torso-ok? torso-ok? :hind-ok? hind-ok? :all-finite? all-finite?})
        (do
          (.mj_jacBody mj model data jp jr (:body-id r))
          (let [s (cm/sense r (.-xpos data) (.-xmat data) (.GetView jp) (.GetView jr) ground-z)
                obs {:nv nv :qpos (.-qpos data) :senses [s] :forces (.-actuator_force data)}
                [ctrl' a] (cm/act ctrl obs c/timestep)
                tg (cm/actions->ctrl a ranges)
                vals (cm/limp tg (.-qpos data) (cm/stiffness ctrl' sigma kick))]
            (dotimes [k nu] (aset (.-ctrl data) k (nth vals k)))
            (.mj_step mj model data)
            (let [t (* (inc i) 0.002)
                  ^js qpos (.-qpos data)
                  nq (.-nq model)
                  finite-now? (every? true? (for [j (range nq)] (not (js/isNaN (aget qpos j)))))
                  torso-low (cm/capsule-clearance (.-xpos data) (.-xmat data) 1 torso-cap ground-z)
                  hind-low (cm/capsule-clearance (.-xpos data) (.-xmat data) 8 hind-cap ground-z)
                  torso-ok-now? (if (>= t 5.0) (<= torso-low 0.02) true)
                  hind-ok-now? (if (>= t 5.0) (<= hind-low 0.02) true)]
              (recur (inc i) ctrl'
                     (and torso-ok? torso-ok-now?)
                     (and hind-ok? hind-ok-now?)
                     (and all-finite? finite-now?)))))))))

(deftest r12-game-drop-lies-down
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (doseq [sp [horse dog]]
                   (let [{:keys [torso-ok? hind-ok? all-finite?]} (run-drop mj sp)]
                     (is torso-ok?)
                     (is hind-ok?)
                     (is all-finite?)))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R13

(defn run-table [^js mj sp sigma]
  (let [place (assoc P :roll 0)
        ^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp place))
        ^js data (new (.-MjData mj) model)
        _ (.mj_forward mj model data)
        nu (count (c/ctrl-ranges sp))
        torso-cap (:capsule (first (c/bodies sp)))
        hind-cap (:capsule (nth (c/bodies sp) 7))
        sig-vec (vec (repeat nu sigma))
        zeros (vec (repeat nu 0))]
    (dotimes [_ 15000]
      (let [vals (cm/limp zeros (.-qpos data) sig-vec)]
        (dotimes [k nu] (aset (.-ctrl data) k (nth vals k)))
        (.mj_step mj model data)))
    (let [torso-low (cm/capsule-clearance (.-xpos data) (.-xmat data) 1 torso-cap ground-z)
          hind-low (cm/capsule-clearance (.-xpos data) (.-xmat data) 8 hind-cap ground-z)]
      (.delete data) (.delete model)
      {:torso torso-low :hind hind-low})))

(deftest r13-table-contrast-dog-only
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (let [stiff (run-table mj dog 1)
                       limp (run-table mj dog 0.1)]
                   (is (> (:torso stiff) 0.3))
                   (is (> (:hind stiff) 0.3))
                   (is (<= (:torso limp) 0.02))
                   (is (<= (:hind limp) 0.02)))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R14

(deftest r14-v2-bodies-joints-and-actuators
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (doseq [[sp hind-mass] [[horse 28.274334] [dog 8.901179]]]
                   (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp P))
                         obj-body (.. mj -mjtObj -mjOBJ_BODY -value)
                         jl (c/joints sp)
                         ^js axis (.-jnt_axis model)
                         ^js trnid (.-actuator_trnid model)
                         ^js body-mass (.-body_mass model)]
                     (is (= 18 (.-nu model)))
                     (is (= 19 (.-njnt model)))
                     (doseq [j (range 1 19)]
                       (is (approx= (:axis (nth jl (dec j)))
                                    [(aget axis (* 3 j)) (aget axis (inc (* 3 j))) (aget axis (+ 2 (* 3 j)))])))
                     (is (approx= [-1 0 0] (:axis (nth jl 7))))
                     (is (approx= [0 0 1] (:axis (nth jl 10))))
                     (doseq [k (range 18)]
                       (is (= (inc k) (aget trnid (* 2 k)))))
                     (is (= 8 (.mj_name2id mj model obj-body "hind")))
                     (is (approx= hind-mass (aget body-mass 8)))
                     (.delete model)))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R15

(defn splay-probe [^js mj sp roll qpos-idx theta-deg lower-body-id lower-len]
  (let [place (assoc P :roll roll)
        ^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp place))
        ^js data (new (.-MjData mj) model)]
    (aset (.-qpos data) qpos-idx (* theta-deg (/ js/Math.PI 180)))
    (.mj_kinematics mj model data)
    (let [^js xpos (.-xpos data)
          ^js xmat (.-xmat data)
          tip-z (+ (aget xpos (+ (* 3 lower-body-id) 2))
                   (* (aget xmat (+ (* 9 lower-body-id) 8)) (- lower-len)))
          torso-z (aget xpos 5)]
      (.delete data) (.delete model)
      {:delta (- tip-z torso-z) :tip-z tip-z})))

(deftest r15-splay-lying-on-side
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 ;; roll 90: right legs down
                 (is (approx= -0.100000 (:delta (splay-probe mj horse 90 14 0 7 0.37))))
                 (is (approx= -0.416964 (:delta (splay-probe mj horse 90 14 25 7 0.37))))
                 (is (approx= 0.216964 (:delta (splay-probe mj horse 90 14 -25 7 0.37))))
                 (is (approx= -0.416964 (:delta (splay-probe mj horse 90 22 25 12 0.37))))
                 (is (approx= 0.416964 (:delta (splay-probe mj horse 90 11 25 5 0.37))))
                 (is (approx= -0.070000 (:delta (splay-probe mj dog 90 14 0 7 0.18))))
                 (is (approx= -0.230595 (:delta (splay-probe mj dog 90 14 25 7 0.18))))
                 (is (approx= -0.230595 (:delta (splay-probe mj dog 90 22 25 12 0.18))))
                 ;; down-side moves down, up-side moves up
                 (is (< (:delta (splay-probe mj horse 90 14 25 7 0.37))
                        (:delta (splay-probe mj horse 90 14 0 7 0.37))))
                 (is (> (:delta (splay-probe mj horse 90 11 25 5 0.37))
                        (:delta (splay-probe mj horse 90 11 0 5 0.37))))
                 ;; roll -90: left legs down
                 (is (approx= -0.416964 (:delta (splay-probe mj horse -90 11 25 5 0.37))))
                 (is (approx= -0.416964 (:delta (splay-probe mj horse -90 19 25 10 0.37))))
                 (is (approx= 0.416964 (:delta (splay-probe mj horse -90 14 25 7 0.37))))
                 (is (approx= -0.230595 (:delta (splay-probe mj dog -90 11 25 5 0.18))))
                 (is (approx= -0.230595 (:delta (splay-probe mj dog -90 19 25 10 0.18))))
                 (is (approx= 0.230595 (:delta (splay-probe mj dog -90 14 25 7 0.18))))
                 ;; absolute check: right front hoof height above ground
                 (let [h0 (- (:tip-z (splay-probe mj horse 90 14 0 7 0.37)) 0.04 ground-z)
                       h25 (- (:tip-z (splay-probe mj horse 90 14 25 7 0.37)) 0.04 ground-z)]
                   (is (approx= 0.410000 h0))
                   (is (approx= 0.093036 h25)))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R16

(defn swing-probe [^js mj sp qpos-idx theta-deg lower-body-id]
  (let [place {:at [0 0 0] :yaw 0 :drop-m 0}
        ^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp place))
        ^js data (new (.-MjData mj) model)]
    (aset (.-qpos data) qpos-idx (* theta-deg (/ js/Math.PI 180)))
    (.mj_kinematics mj model data)
    (let [^js xpos (.-xpos data)
          knee [(aget xpos (* 3 lower-body-id)) (aget xpos (inc (* 3 lower-body-id))) (aget xpos (+ 2 (* 3 lower-body-id)))]
          torso [(aget xpos 3) (aget xpos 4) (aget xpos 5)]]
      (.delete data) (.delete model)
      (mapv - knee torso))))

(deftest r16-fold-sign-standing-pose
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (is (approx= [-0.087052 0.1 -0.148351] (swing-probe mj horse 12 75 5)))
                 (is (approx= [0.609090 0.1 -0.24] (swing-probe mj horse 12 -60 5)))
                 (is (approx= [0.049090 0.1 -0.24] (swing-probe mj horse 20 -60 10)))
                 (is (approx= [-0.647052 0.1 -0.148351] (swing-probe mj horse 20 75 10)))
                 (is (approx= [-0.087052 -0.1 -0.148351] (swing-probe mj horse 15 75 7)))
                 (is (approx= [0.049090 -0.1 -0.24] (swing-probe mj horse 23 -60 12)))
                 (is (approx= [0.006815 0.07 -0.081764] (swing-probe mj dog 12 75 5)))
                 (is (approx= [0.373205 0.07 -0.13] (swing-probe mj dog 12 -60 5)))
                 (is (approx= [-0.026795 0.07 -0.13] (swing-probe mj dog 20 -60 10)))
                 (is (approx= [-0.393185 0.07 -0.081764] (swing-probe mj dog 20 75 10)))
                 (is (< (js/Math.abs (first (swing-probe mj horse 12 75 5))) 0.28))
                 (is (> (js/Math.abs (first (swing-probe mj horse 12 -60 5))) 0.28))
                 (is (< (js/Math.abs (first (swing-probe mj horse 20 -60 10))) 0.28))
                 (is (> (js/Math.abs (first (swing-probe mj horse 20 75 10))) 0.28))
                 (is (< (js/Math.abs (first (swing-probe mj dog 12 75 5))) 0.2))
                 (is (> (js/Math.abs (first (swing-probe mj dog 12 -60 5))) 0.2))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R17

(defn kinematic-state [^js mj sp place qpos-idx theta-deg]
  (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp place))
        ^js data (new (.-MjData mj) model)]
    (aset (.-qpos data) qpos-idx (* theta-deg (/ js/Math.PI 180)))
    (.mj_kinematics mj model data)
    (let [xpos (vec (.-xpos data))
          xmat (vec (.-xmat data))]
      (.delete data) (.delete model)
      {:xpos xpos :xmat xmat})))

(defn body-origin [{:keys [xpos]} body-id]
  [(nth xpos (* 3 body-id)) (nth xpos (inc (* 3 body-id))) (nth xpos (+ 2 (* 3 body-id)))])

(defn offset-point [{:keys [xpos xmat]} body-id [lx ly lz]]
  (let [mx (* 9 body-id)]
    [(+ (nth xpos (* 3 body-id))
        (* (nth xmat mx) lx) (* (nth xmat (+ mx 1)) ly) (* (nth xmat (+ mx 2)) lz))
     (+ (nth xpos (inc (* 3 body-id)))
        (* (nth xmat (+ mx 3)) lx) (* (nth xmat (+ mx 4)) ly) (* (nth xmat (+ mx 5)) lz))
     (+ (nth xpos (+ (* 3 body-id) 2))
        (* (nth xmat (+ mx 6)) lx) (* (nth xmat (+ mx 7)) ly) (* (nth xmat (+ mx 8)) lz))]))

(deftest r17-spine-signs-standing-pose
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (let [place {:at [0 0 0] :yaw 0 :drop-m 0}
                       st-bend-horse (kinematic-state mj horse place 17 25)
                       st-bend-dog (kinematic-state mj dog place 17 25)
                       st-twist-horse (kinematic-state mj horse place 18 20)
                       st-twist-dog (kinematic-state mj dog place 18 20)]
                   (is (approx= [-0.271892 -0.126785 0]
                                (mapv - (offset-point st-bend-horse 8 [-0.3 0 0]) (body-origin st-bend-horse 1))))
                   (is (approx= [-0.199388 -0.092976 0]
                                (mapv - (offset-point st-bend-dog 8 [-0.22 0 0]) (body-origin st-bend-dog 1))))
                   (is (approx= [-0.28 0.111070 -0.012783]
                                (mapv - (body-origin st-twist-horse 9) (body-origin st-twist-horse 1))))
                   (is (approx= [-0.28 -0.076868 -0.081187]
                                (mapv - (body-origin st-twist-horse 11) (body-origin st-twist-horse 1))))
                   (is (approx= [-0.2 0.076039 -0.004249]
                                (mapv - (body-origin st-twist-dog 9) (body-origin st-twist-dog 1))))
                   (is (approx= [-0.2 -0.055518 -0.052132]
                                (mapv - (body-origin st-twist-dog 11) (body-origin st-twist-dog 1)))))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; S1

(defn stand-hold [^js mj sp]
  (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp {:at [0 0 0] :yaw 0 :drop-m 0}))
        ^js data (new (.-MjData mj) model)
        nu (.-nu model)
        _ (.mj_forward mj model data)]
    (dotimes [_ 2500]
      (dotimes [k nu] (aset (.-ctrl data) k 0))
      (.mj_step mj model data))
    (let [h (aget (.-xpos data) 5)
          tilt (cm/tilt-deg (cm/head-up (.-xmat data) 1 [0 0 1]))
          ^js qpos (.-qpos data)
          nq (.-nq model)
          finite? (every? true? (for [i (range nq)] (not (js/isNaN (aget qpos i)))))]
      (.delete data) (.delete model)
      {:h h :tilt tilt :finite? finite?})))

(deftest s1-stand-hold-quest-criterion
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (let [{:keys [h tilt finite?]} (stand-hold mj horse)]
                   (js/console.log (str "stand-hold horse kp " (:kp horse) ": torso " (.toFixed h 3) " m, tilt " (.toFixed tilt 1) "° after 5 s"))
                   (is finite?)
                   (is (<= 0.70 h 0.84))
                   (is (<= tilt 20)))
                 (let [{:keys [h tilt finite?]} (stand-hold mj dog)]
                   (js/console.log (str "stand-hold dog kp " (:kp dog) ": torso " (.toFixed h 3) " m, tilt " (.toFixed tilt 1) "° after 5 s"))
                   (is finite?)
                   (is (<= 0.42 h 0.445))
                   (is (<= tilt 1)))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; S2

(defn stand-run-ok? [^js mj sp kp push-idx steps]
  (let [sp' (assoc sp :kp kp)
        ^js model (.from_xml_string (.-MjModel mj) (c/mjcf sp' {:at [0 0 0] :yaw 0 :drop-m 0}))
        ^js data (new (.-MjData mj) model)
        nu (.-nu model)
        _ (.mj_forward mj model data)
        h-min (* (/ 5.0 6.0) (- (c/rest-bottom sp)))]
    (when push-idx (aset (.-qvel data) push-idx 0.1))
    (let [ok? (loop [i 0]
                (cond
                  (>= i steps) true
                  :else
                  (do
                    (dotimes [k nu] (aset (.-ctrl data) k 0))
                    (.mj_step mj model data)
                    (let [h (aget (.-xpos data) 5)
                          tilt (cm/tilt-deg (cm/head-up (.-xmat data) 1 [0 0 1]))]
                      (if (and (>= h h-min) (<= tilt 20))
                        (recur (inc i))
                        false)))))]
      (.delete data) (.delete model)
      ok?)))

(defn holds? [^js mj sp kp]
  (and (stand-run-ok? mj sp kp nil 15000)
       (stand-run-ok? mj sp kp 0 5000)
       (stand-run-ok? mj sp kp 1 5000)))

(deftest s2-smallest-holding-kp
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (let [kps [150 175 200 225]
                       v (mapv #(holds? mj horse %) kps)]
                   (js/console.log (str "stand-hold finding: horse holds from :kp " (some (fn [[k ok]] (when ok k)) (map vector kps v))
                                        " (has " (:kp horse) "); dog holds at 40: " (holds? mj dog 40)))
                   (is (= [false false false true] v)))
                 (is (true? (holds? mj dog 40)))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))
