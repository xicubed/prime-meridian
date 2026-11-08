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
                (for [i (range 1 12)]
                  (approx= (second (nth masses (dec i))) (aget (.-body_mass model) i)))))
    (is (every? true?
                (for [j (range 9)]
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
        dt (/ 1.0 60)]
    (loop [frame 0 acc 0 ctrl (cm/controller :twitch 9 1) total 0]
      (if (>= frame 120)
        (let [^js qpos (.-qpos data)
              nq (.-nq model)
              all-finite? (every? true? (for [i (range nq)] (not (js/isNaN (aget qpos i)))))
              torso-h (- (aget (.-xpos data) 5) ground-z)
              hinge-moved? (some true? (for [i (range 7 16)] (> (js/Math.abs (aget qpos i)) 0.05)))]
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
