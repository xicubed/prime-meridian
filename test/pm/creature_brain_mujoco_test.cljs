(ns pm.creature-brain-mujoco-test
  (:require [cljs.test :refer [deftest is async]]
            [pm.creature :as c]
            [pm.creature-brain :as cb]
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

(def horse (c/parse (get species/texts :horse)))
(def dog (c/parse (get species/texts :dog)))

(def horse-dir (str (.cwd js/process) "/public/creatures/horse/"))
(def east-bank {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})
(def bank-ground-z 1.79)

(defn import-mj []
  ;; require(), not import(): same reason as creature_mujoco_test.cljs.
  (let [^js mod (js/require (str (.cwd js/process) "/node_modules/@mujoco/mujoco/mujoco.js"))]
    ((.-default mod))))

(defn import-ort []
  (let [^js ort (js/require (str (.cwd js/process) "/node_modules/onnxruntime-web"))]
    (set! (.. ort -env -wasm -numThreads) 1)
    ort))

(defn read-bytes [path] (.readFileSync (js/require "fs") path))
(defn read-text [path] (.readFileSync (js/require "fs") path "utf8"))

(def meta-doc (cb/parse-meta (read-text (str horse-dir "policy.json"))))
(def joint-names (mapv (comp name :name) (c/joints horse)))
(def pol (cb/meta->policy meta-doc joint-names))

(def ref-js (js/JSON.parse (read-text (str horse-dir "policy_reference.json"))))
(defn samples-for [age] (js->clj (aget (.-samples ref-js) (str age)) :keywordize-keys true))
(def cpu-trace (js->clj (.-fixed_chest_rollout_3s_cpu ref-js) :keywordize-keys true))

(defn load-session [^js ort age]
  (let [model-bytes (read-bytes (str horse-dir "policy_" age ".onnx"))
        data-bytes (read-bytes (str horse-dir "policy_" age ".onnx.data"))
        data-path (str "policy_" age ".onnx.data")]
    (.create (.-InferenceSession ort) model-bytes
             #js{:executionProviders #js ["wasm"]
                 :externalData #js [#js {:path data-path :data data-bytes}]})))

(defn load-mj+session [ort age]
  (js/Promise.all #js [(import-mj) (load-session ort age)]))

(defn infer [^js ort ^js session obs]
  (let [^js tensor (new (.-Tensor ort) "float32" (js/Float32Array.from (clj->js obs)) #js [1 (count obs)])]
    (-> (.run session #js {"obs" tensor})
        (.then (fn [^js out]
                 (let [^js t (aget out "actions")]
                   {:data (vec (.-data t)) :dims (vec (.-dims t))}))))))

(defn run-seq
  "Runs promise-returning thunks in order, collecting their results."
  [fns]
  (reduce (fn [p f] (.then p (fn [acc] (.then (f) (fn [v] (conj acc v))))))
          (js/Promise.resolve [])
          fns))

;; R1

(defn check-age [ort age]
  (-> (load-session ort age)
      (.then (fn [^js s]
               (is (= ["obs"] (vec (.-inputNames s))))
               (is (= ["actions"] (vec (.-outputNames s))))
               (-> (infer ort s (vec (repeat 60 0)))
                   (.then (fn [{:keys [dims]}]
                            (is (= [1 18] dims))
                            (.release s))))))))

(deftest r1-ages-load
  (async done
    (let [ort (import-ort)]
      (-> (run-seq (map (fn [age] #(check-age ort age)) [0 3000 6000 9499]))
          (.then (fn [_] (done)))
          (.catch (fn [e] (is false (str e)) (done)))))))

;; R2, R3

(defn check-parity [ort age eps]
  (-> (load-session ort age)
      (.then (fn [^js s]
               (let [samples (samples-for age)]
                 (-> (run-seq (map (fn [sample] #(infer ort s (:obs sample))) samples))
                     (.then (fn [results]
                              (doseq [[r sample] (map vector results samples)]
                                (is (approx= (:onnx_action sample) (:data r) eps)))
                              (.release s)))))))))

(deftest r2-onnx-parity-age-0
  (async done
    (let [ort (import-ort)]
      (-> (check-parity ort 0 1e-4)
          (.then (fn [_] (done)))
          (.catch (fn [e] (is false (str e)) (done)))))))

(deftest r3-onnx-parity-age-9499
  (async done
    (let [ort (import-ort)]
      (-> (check-parity ort 9499 1e-4)
          (.then (fn [_] (done)))
          (.catch (fn [e] (is false (str e)) (done)))))))

;; R4

(deftest r4-addresses
  (async done
    (-> (import-mj)
        (.then (fn [^js mj]
                 (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf horse c/origin))]
                   (is (= [] (cb/address-problems 18 (.-jnt_qposadr model) (.-jnt_dofadr model) (.-actuator_trnid model))))
                   (is (= 25 (.-nq model)))
                   (is (= 24 (.-nv model)))
                   (is (= 18 (.-nu model)))
                   (.delete model))
                 (done)))
        (.catch (fn [e] (is false (str e)) (done))))))

;; R5-R9 share this rollout

(defn init-chest! [^js mj ^js model ^js data]
  (let [iq (:initial_qpos cpu-trace) iv (:initial_qvel cpu-trace)]
    (dotimes [i (count iq)] (aset (.-qpos data) i (nth iq i)))
    (dotimes [i (count iv)] (aset (.-qvel data) i (nth iv i)))
    (.mj_forward mj model data)))

(defn run-rollout
  "150 control steps of: obs -> infer -> ctrl -> 10 x mj_step. :real reads
   xmat straight off data after mj_step (the game, §8.1); :fresh rebuilds it
   from the current qpos quaternion (sim2sim_cpu.py's formula, R6)."
  [^js mj ^js ort ^js session xmat-mode]
  (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf horse c/origin))
        ^js data (new (.-MjData mj) model)
        n (:n pol)]
    (init-chest! mj model data)
    (letfn [(xmat-arg []
              (case xmat-mode
                :real (.-xmat data)
                :fresh (clj->js
                         (into (vec (repeat 9 0))
                               (cb/quat->xmat [(aget (.-qpos data) 3) (aget (.-qpos data) 4)
                                               (aget (.-qpos data) 5) (aget (.-qpos data) 6)])))))
            (go [i la records obs-list]
              (let [^js qpos (.-qpos data)
                    h (aget qpos 2) qx (aget qpos 4) qy (aget qpos 5)
                    up (- 1 (* 2 (+ (* qx qx) (* qy qy))))]
                (if (>= i 150)
                  (do (.delete data) (.delete model)
                      (js/Promise.resolve {:records records :obs obs-list :final {:h h :up up}}))
                  (let [obs (cb/observation n (.-qpos data) (.-qvel data) (xmat-arg) la)]
                    (-> (infer ort session obs)
                        (.then (fn [result]
                                 (let [action (:data result)
                                       ctrl (cb/action->ctrl pol action)]
                                   (dotimes [k n] (aset (.-ctrl data) k (nth ctrl k)))
                                   (dotimes [_ 10] (.mj_step mj model data))
                                   (go (inc i) action (conj records {:h h :up up}) (conj obs-list obs))))))))))]
      (go 0 (vec (repeat n 0)) [] []))))

;; R5

(deftest r5-game-rollout-vs-cpu-trace
  (async done
    (let [ort (import-ort)
          trace (:trace cpu-trace)]
      (-> (load-mj+session ort 9499)
          (.then (fn [arr]
                   (let [mj (aget arr 0) ^js session (aget arr 1)]
                     (-> (run-rollout mj ort session :real)
                         (.then (fn [{:keys [records]}]
                                  (let [worst (apply max (for [i (range 50)]
                                                            (js/Math.abs (- (:h (nth records i))
                                                                             (:height (nth trace i))))))]
                                    (is (<= worst 0.01)))
                                  (.release session)
                                  (done)))))))
          (.catch (fn [e] (is false (str e)) (done)))))))

;; R6

(deftest r6-engine-identity-current-quaternion
  (async done
    (let [ort (import-ort)
          trace (:trace cpu-trace)]
      (-> (load-mj+session ort 9499)
          (.then (fn [arr]
                   (let [mj (aget arr 0) ^js session (aget arr 1)]
                     (-> (run-rollout mj ort session :fresh)
                         (.then (fn [{:keys [records]}]
                                  (let [worst (apply max (for [i (range 50)]
                                                            (js/Math.abs (- (:h (nth records i))
                                                                             (:height (nth trace i))))))]
                                    (is (<= worst 0.001)))
                                  (.release session)
                                  (done)))))))
          (.catch (fn [e] (is false (str e)) (done)))))))

;; R7

(deftest r7-ends-standing-vs-warp-trace
  (async done
    (let [ort (import-ort)]
      (-> (load-mj+session ort 9499)
          (.then (fn [arr]
                   (let [mj (aget arr 0) ^js session (aget arr 1)]
                     (-> (run-rollout mj ort session :real)
                         (.then (fn [{:keys [records final]}]
                                  (doseq [i (range 100 150)]
                                    (let [{:keys [h up]} (nth records i)]
                                      (is (> h 0.65))
                                      (is (> up 0.9))))
                                  (is (> (:h (nth records 149)) 0.65))
                                  (is (> (:up (nth records 149)) 0.9))
                                  (is (> (:h final) 0.65))
                                  (is (> (:up final) 0.9))
                                  (.release session)
                                  (done)))))))
          (.catch (fn [e] (is false (str e)) (done)))))))

;; R8

(deftest r8-age-0-does-not-stand
  (async done
    (let [ort (import-ort)]
      (-> (load-mj+session ort 0)
          (.then (fn [arr]
                   (let [mj (aget arr 0) ^js session (aget arr 1)]
                     (-> (run-rollout mj ort session :real)
                         (.then (fn [{:keys [records final]}]
                                  (is (not (some (fn [{:keys [h up]}] (and (> h 0.65) (> up 0.9))) records)))
                                  (is (< (:h final) 0.4))
                                  (is (< (:up final) 0.5))
                                  (.release session)
                                  (done)))))))
          (.catch (fn [e] (is false (str e)) (done)))))))

;; R9

(defn run-scheduled [^js mj ^js ort ^js session pattern]
  (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf horse c/origin))
        ^js data (new (.-MjData mj) model)
        n (:n pol)]
    (init-chest! mj model data)
    (letfn [(exec! [action ops]
              (doseq [op ops]
                (case op
                  :apply (let [ctrl (cb/action->ctrl pol action)]
                           (dotimes [k n] (aset (.-ctrl data) k (nth ctrl k))))
                  :step (.mj_step mj model data)
                  :ask nil)))
            (process [sched obs-list dropped pat]
              (if (>= (count obs-list) 150)
                (do (.delete data) (.delete model)
                    (js/Promise.resolve {:obs obs-list :dropped dropped}))
                (let [{:keys [st ops action]} (cb/plan sched)]
                  (exec! action ops)
                  (if (some #{:ask} ops)
                    (let [obs (cb/observation n (.-qpos data) (.-qvel data) (.-xmat data) (:last-action st))
                          gen (:gen st)]
                      (-> (infer ort session obs)
                          (.then (fn [result]
                                   (let [st2 (cb/receive st gen (:data result))]
                                     (process st2 (conj obs-list obs) dropped pat))))))
                    (let [steps (first pat)
                          [st2 d] (cb/add-owed st steps cm/max-steps)]
                      (process st2 obs-list (+ dropped d) (rest pat)))))))]
      (process (cb/schedule-init (:decim pol) n) [] 0 (cycle pattern)))))

(deftest r9-schedule-invariance
  (async done
    (let [ort (import-ort)]
      (-> (load-mj+session ort 9499)
          (.then (fn [arr]
                   (let [mj (aget arr 0) ^js session (aget arr 1)]
                     (-> (js/Promise.all
                           #js [(run-scheduled mj ort session [25])
                                (run-scheduled mj ort session [8 9 7 25 1 0 3])
                                (run-scheduled mj ort session [1])
                                (run-rollout mj ort session :real)])
                         (.then (fn [results]
                                  (let [r25 (aget results 0) rpat (aget results 1)
                                        r1 (aget results 2) plain (aget results 3)]
                                    (is (= 0 (:dropped r25)))
                                    (is (= 0 (:dropped rpat)))
                                    (is (= 0 (:dropped r1)))
                                    (is (= (:obs r25) (:obs rpat)))
                                    (is (= (:obs r25) (:obs r1)))
                                    (is (= (:obs r25) (:obs plain))))
                                  (.release session)
                                  (done)))))))
          (.catch (fn [e] (is false (str e)) (done)))))))

;; R10

(defn run-drop-trial [^js mj ^js ort ^js session t]
  (let [^js model (.from_xml_string (.-MjModel mj) (c/mjcf horse east-bank))
        ^js data (new (.-MjData mj) model)
        n (:n pol)
        ranges (c/ctrl-ranges horse)
        ground (:ground (c/spawn horse east-bank))
        qpos (cb/drop-pose (cb/drop-seed 1 t) ranges [0.4 0.8] ground)]
    (dotimes [i (count qpos)] (aset (.-qpos data) i (nth qpos i)))
    (.mj_forward mj model data)
    (letfn [(go [i la]
              (if (>= i 300)
                (let [up? (cb/up? {:torso-m 0.65 :up-z 0.9} bank-ground-z (.-xpos data) (.-xmat data))]
                  (.delete data) (.delete model)
                  (js/Promise.resolve up?))
                (let [obs (cb/observation n (.-qpos data) (.-qvel data) (.-xmat data) la)]
                  (-> (infer ort session obs)
                      (.then (fn [result]
                               (let [action (:data result)
                                     ctrl (cb/action->ctrl pol action)]
                                 (dotimes [k n] (aset (.-ctrl data) k (nth ctrl k)))
                                 (dotimes [_ 10] (.mj_step mj model data))
                                 (go (inc i) action))))))))]
      (go 0 (vec (repeat n 0))))))

(deftest r10-random-drops-both-ends-of-training
  (async done
    (let [ort (import-ort)]
      (-> (load-mj+session ort 9499)
          (.then (fn [arr]
                   (let [mj (aget arr 0) ^js session9499 (aget arr 1)]
                     (-> (run-seq (map (fn [t] #(run-drop-trial mj ort session9499 t)) (range 1 11)))
                         (.then (fn [results]
                                  (is (every? true? results))
                                  (.release session9499)
                                  mj))))))
          (.then (fn [mj]
                   (-> (load-session ort 0)
                       (.then (fn [^js session0]
                                (-> (run-seq (map (fn [t] #(run-drop-trial mj ort session0 t)) (range 1 4)))
                                    (.then (fn [results0]
                                             (is (every? false? results0))
                                             (.release session0)))))))))
          (.then (fn [_] (done)))
          (.catch (fn [e] (is false (str e)) (done)))))))

;; R11

(deftest r11-species-without-brain-untouched
  (is (= [] (c/problems dog)))
  (is (nil? (cb/brain-request dog "?pony=dog"))))
