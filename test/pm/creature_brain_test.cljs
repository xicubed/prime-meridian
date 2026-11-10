(ns pm.creature-brain-test
  (:require [cljs.test :refer [deftest is]]
            [pm.creature :as c]
            [pm.creature-brain :as cb]
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

;; policy.json, read exactly as the real game sees it under public/.
(def meta-doc
  (cb/parse-meta
    (.readFileSync (js/require "fs") (str (.cwd js/process) "/public/creatures/horse/policy.json") "utf8")))

(def joint-names (mapv (comp name :name) (c/joints horse)))

;; policy_reference.json: 8 samples at age 0 and 8 at age 9499.
(def ref-js
  (js/JSON.parse
    (.readFileSync (js/require "fs") (str (.cwd js/process) "/public/creatures/horse/policy_reference.json") "utf8")))

(defn samples-for [age] (js->clj (aget (.-samples ref-js) (str age)) :keywordize-keys true))

(def all-samples (into (samples-for 0) (samples-for 9499)))

;; B1

(deftest b1-brain-request-horse
  (is (= {:kind :policy :dir "/creatures/horse/" :meta-url "/creatures/horse/policy.json"
          :ages [0 3000 6000 9499] :age 9499 :drop-m [0.4 0.8] :stand {:torso-m 0.65 :up-z 0.9}}
         (cb/brain-request horse "?pony")))
  (is (= {:kind :policy :dir "/creatures/horse/" :meta-url "/creatures/horse/policy.json"
          :ages [0 3000 6000 9499] :age 3000 :drop-m [0.4 0.8] :stand {:torso-m 0.65 :up-z 0.9}}
         (cb/brain-request horse "?pony&age=3000")))
  (is (= 9499 (:age (cb/brain-request horse "?pony&age=123"))))
  (is (= 9499 (:age (cb/brain-request horse "?pony&age=abc"))))
  (is (= 0 (:age (cb/brain-request horse "?pony&age=0"))))
  (is (= {:kind :twitch} (cb/brain-request horse "?pony&brain=twitch")))
  (is (= :policy (:kind (cb/brain-request horse "?pony&brain=other"))))
  (is (= 9499 (:age (cb/brain-request horse nil)))))

;; B2

(deftest b2-brain-request-dog-no-brain
  (is (nil? (cb/brain-request dog "?pony=dog")))
  (is (nil? (cb/brain-request dog "?pony=dog&brain=twitch")))
  (is (nil? (cb/brain-request dog "?pony=dog&age=9499"))))

;; B3

(deftest b3-age-files
  (is (= {:model "/creatures/horse/policy_3000.onnx"
          :data "/creatures/horse/policy_3000.onnx.data"
          :data-path "policy_3000.onnx.data"}
         (cb/age-files {:dir "/creatures/horse/"} 3000)))
  (is (= {:model "/x/policy_0.onnx" :data "/x/policy_0.onnx.data" :data-path "policy_0.onnx.data"}
         (cb/age-files {:dir "/x/"} 0))))

;; B4

(deftest b4-meta-ok
  (is (= [] (cb/meta-problems meta-doc joint-names)))
  (let [p (cb/meta->policy meta-doc joint-names)]
    (is (= 18 (:n p)))
    (is (= 10 (:decim p)))
    (is (= "obs" (:input p)))
    (is (= "actions" (:output p)))
    (is (approx= 0.741765022277832 (nth (:scale p) 1) 1e-9))
    (is (approx= 0.785398006439209 (nth (:offset p) 6) 1e-9))
    (is (approx= -1.7453290224075317 (nth (:lo p) 14) 1e-9))
    (is (approx= 1.3089970350265503 (nth (:hi p) 5) 1e-9))))

;; B5

(deftest b5-meta-problems
  (is (= ["policy.json: joint_order doesn't match the body's joints"]
         (cb/meta-problems (assoc meta-doc :joint_order (vec (reverse joint-names))) joint-names)))
  (is (= ["policy.json: observation sizes must be [18 18 3 3 18], total 60"]
         (cb/meta-problems (assoc-in meta-doc [:observation :total_dim] 61) joint-names)))
  (is (= ["policy.json: observation layout must be joint_pos, joint_vel, gravity_b, ang_vel, last_action"]
         (cb/meta-problems (assoc-in meta-doc [:observation :layout 2 :name] "gravity") joint-names)))
  (is (= ["policy.json: action needs 18 of size, scale, offset, ctrlrange_lo, ctrlrange_hi"]
         (cb/meta-problems (update-in meta-doc [:action :scale] pop) joint-names)))
  (is (= ["policy.json: simulation_timestep must be 0.002"]
         (cb/meta-problems (assoc meta-doc :simulation_timestep 0.001) joint-names)))
  (is (= ["policy.json: control_timestep must be a whole number of physics steps"]
         (cb/meta-problems (assoc meta-doc :control_timestep 0.021) joint-names)))
  (let [e (try (cb/meta->policy (assoc meta-doc :simulation_timestep 0.001) joint-names)
               nil
               (catch :default e e))]
    (is (= "bad brain: policy.json: simulation_timestep must be 0.002" (ex-message e))))
  (is (= [] (cb/meta-problems meta-doc (mapv (comp name :name) (c/joints dog))))))

;; B6

(deftest b6-address-problems
  (is (= [] (cb/address-problems 2 #js [0 7 8] #js [0 6 7] #js [1 0 2 0])))
  (is (= ["actuator 0 drives joint 2 (qpos 8, dof 7); expected joint 1 (qpos 7, dof 6)"
          "actuator 1 drives joint 1 (qpos 7, dof 6); expected joint 2 (qpos 8, dof 7)"]
         (cb/address-problems 2 #js [0 7 8] #js [0 6 7] #js [2 0 1 0]))))

;; B7

(deftest b7-quat->xmat-and-gravity-b
  (is (approx= [1 0 0 0 1 0 0 0 1] (cb/quat->xmat [1 0 0 0]) 1e-9))
  (is (approx= [1 0 0 0 0 -1 0 1 0] (cb/quat->xmat [0.70710678 0.70710678 0 0]) 1e-6))
  (is (approx= [0 -1 0]
               (cb/gravity-b (clj->js (into [9 9 9 9 9 9 9 9 9] [1 0 0 0 0 -1 0 1 0])) 1)
               1e-12))
  (is (= [0 0 -1] (cb/gravity-b (clj->js [1 0 0 0 1 0 0 0 1]) 0))))

;; B8

(deftest b8-quat-integrate
  (is (approx= [0.7071067812 0 0 0.7071067812] (cb/quat-integrate [1 0 0 0] [0 0 js/Math.PI] 0.5) 1e-9))
  (is (approx= [1 0 0 0]
               (cb/quat-integrate [0.7071067812 0 0 0.7071067812] [0 0 js/Math.PI] -0.5)
               1e-9))
  (is (= [1 0 0 0] (cb/quat-integrate [2 0 0 0] [0 0 0] 0.1))))

;; B9

(deftest b9-observation-hand-case
  (let [qpos (clj->js [0 0 0.5 1 0 0 0 0.1 0.2])
        qvel (clj->js [0 0 0 0.3 0.4 0.5 1.1 1.2])
        xmat (clj->js (into [1 0 0 0 1 0 0 0 1] [1 0 0 0 0 -1 0 1 0]))
        obs (cb/observation 2 qpos qvel xmat [0.7 0.8])]
    (is (= 12 (count obs)))
    (is (approx= [0.1 0.2 1.1 1.2 0 -1 0 0.3 0.4 0.5 0.7 0.8] obs 1e-12))))

;; B10

(deftest b10-observation-parity-all-samples
  (doseq [s all-samples]
    (let [q (subvec (:qpos s) 3 7)
          w (subvec (:qvel s) 3 6)
          xmat (clj->js (into (vec (repeat 9 0)) (cb/quat->xmat (cb/quat-integrate q w -0.002))))
          obs (cb/observation 18 (clj->js (:qpos s)) (clj->js (:qvel s)) xmat (:last_action s))]
      (is (approx= (:obs s) obs 1e-5))))
  (let [s (first (filter #(and (= "drop" (:start_type %)) (= 10 (:step %))) (samples-for 9499)))
        q (subvec (:qpos s) 3 7)
        xmat-no-backstep (clj->js (into (vec (repeat 9 0)) (cb/quat->xmat q)))
        obs-no-backstep (cb/observation 18 (clj->js (:qpos s)) (clj->js (:qvel s)) xmat-no-backstep (:last_action s))]
    (is (some? s))
    (is (not (approx= (:obs s) obs-no-backstep 1e-5)))))

;; B11

(deftest b11-action->ctrl
  (let [p (cb/meta->policy meta-doc joint-names)
        zeros18 (vec (repeat 18 0))]
    (is (approx= (nth (:offset p) 1) (nth (cb/action->ctrl p zeros18) 1) 1e-9))
    (is (approx= (nth (:offset p) 5) (nth (cb/action->ctrl p zeros18) 5) 1e-9))
    (is (approx= (nth (:offset p) 6) (nth (cb/action->ctrl p zeros18) 6) 1e-9))
    (is (approx= (nth (:offset p) 14) (nth (cb/action->ctrl p zeros18) 14) 1e-9))
    (is (= 0 (nth (cb/action->ctrl p zeros18) 0)))
    (is (approx= 0.785398 (nth (cb/action->ctrl p (assoc zeros18 1 1)) 1) 1e-7))
    (is (approx= -1.047198 (nth (cb/action->ctrl p (assoc zeros18 5 -1)) 5) 1e-7))
    (is (= (:hi p) (cb/action->ctrl p (vec (repeat 18 100)))))
    (is (= (:lo p) (cb/action->ctrl p (vec (repeat 18 -100)))))
    (doseq [s all-samples]
      (is (approx= (:ctrl s) (cb/action->ctrl p (:onnx_action s)) 1e-6)))))

;; B12

(deftest b12-schedule-synchronous-answers
  (let [st0 (cb/schedule-init 10 2)
        [s1 d1] (cb/add-owed st0 25 25)
        {st1a :st ops1 :ops action1 :action} (cb/plan s1)]
    (is (= 0 d1))
    (is (= [:ask] ops1))
    (is (nil? action1))
    (is (= 25 (:owed st1a)))
    (is (true? (:waiting? st1a)))
    (is (= 0 (:k st1a)))
    (let [{ops1b :ops st1b :st} (cb/plan st1a)]
      (is (= [] ops1b))
      (is (= st1a st1b)))
    (let [st2 (cb/receive st1a 0 [0.5 -0.5])
          {st2a :st ops2 :ops action2 :action} (cb/plan st2)]
      (is (= (into [:apply] (into (vec (repeat 10 :step)) [:ask])) ops2))
      (is (= [0.5 -0.5] action2))
      (is (= 15 (:owed st2a)))
      (is (= 0 (:k st2a)))
      (is (= 1 (:boundaries st2a)))
      (is (true? (:waiting? st2a)))
      (is (= [0.5 -0.5] (:last-action st2a)))
      (let [st3 (cb/receive st2a 0 [1 1])
            {st3a :st ops3 :ops} (cb/plan st3)]
        (is (= ops2 ops3))
        (is (= 5 (:owed st3a)))
        (is (= 2 (:boundaries st3a)))
        (let [st4 (cb/receive st3a 0 [2 2])
              {st4a :st ops4 :ops} (cb/plan st4)]
          (is (= (into [:apply] (vec (repeat 5 :step))) ops4))
          (is (= 0 (:owed st4a)))
          (is (= 5 (:k st4a)))
          (is (false? (:waiting? st4a)))
          (is (nil? (:ready st4a)))
          (is (= 3 (:boundaries st4a)))
          (is (= [2 2] (:last-action st4a)))
          (let [[st5 d5] (cb/add-owed st4a 8 25)
                {st5a :st ops5 :ops} (cb/plan st5)]
            (is (= 0 d5))
            (is (= (into (vec (repeat 5 :step)) [:ask]) ops5))
            (is (= 3 (:owed st5a)))
            (is (= 0 (:k st5a)))
            (is (true? (:waiting? st5a)))))))
    (let [[st' d] (cb/add-owed (assoc st0 :owed 20) 10 25)]
      (is (= 5 d))
      (is (= 25 (:owed st'))))))

;; B13

(deftest b13-schedule-edge-cases
  (let [st0 (cb/schedule-init 3 1)
        [st1 _] (cb/add-owed st0 7 100)
        {st1a :st ops1 :ops} (cb/plan st1)]
    (is (= [:ask] ops1))
    (let [st2 (cb/receive st1a 0 [9])
          {st2a :st ops2 :ops} (cb/plan st2)]
      (is (= [:apply :step :step :step :ask] ops2))
      (is (= 4 (:owed st2a)))
      (is (= 0 (:k st2a)))))
  (let [st0 (cb/schedule-init 1 1)
        [st1 _] (cb/add-owed st0 3 100)
        {st1a :st} (cb/plan st1)
        st2 (cb/receive st1a 0 [5])
        {st2a :st ops2 :ops} (cb/plan st2)]
    (is (= [:apply :step :ask] ops2))
    (is (= 2 (:owed st2a)))
    (is (= 0 (:k st2a)))
    (is (true? (:waiting? st2a))))
  (let [st0 (cb/schedule-init 10 2)
        [s1 _] (cb/add-owed st0 25 25)
        {st1a :st} (cb/plan s1)]
    (is (= st1a (cb/receive st1a 1 [5 5])))
    (let [st2 (cb/receive st1a 0 [1 1])]
      (is (= st2 (cb/receive st2 0 [9 9])))))
  (let [st0 (cb/schedule-init 10 2)
        [s1 _] (cb/add-owed st0 25 25)
        {st1a :st} (cb/plan s1)
        st2 (cb/receive st1a 0 [0.5 -0.5])
        {st2a :st} (cb/plan st2)
        st3 (cb/receive st2a 0 [1 1])
        {st3a :st} (cb/plan st3)
        st4 (cb/receive st3a 0 [2 2])
        {st4a :st} (cb/plan st4)
        restarted (cb/restart st4a)]
    (is (= {:decim 10 :k 0 :owed 0 :ready nil :waiting? false :gen 1
            :last-action [0 0] :boundaries 0}
           restarted))
    (is (= restarted (cb/receive restarted 0 [1 1])))))

;; B14

(deftest b14-drop-seed-and-drop-pose
  (is (= 1000 (cb/drop-seed 1 0)))
  (is (= 1001 (cb/drop-seed 1 1)))
  (is (= 3002 (cb/drop-seed 3 2)))
  (is (approx= [1 2 3.5 -0.11118 0.270672 0.935112 -0.199853 -0.475724 1.503347]
               (cb/drop-pose 7 [[-1 1] [0 2]] [0.5 0.5] [1 2 3])
               1e-6))
  (is (approx= [0 0 0.403137 -0.815781 0.298318 0.331877 -0.367921 0.813115 -0.109015
                -0.756694 -0.678192 0.135262 0.277648 0.342067 -0.039091 0.430789
                1.056588 0.059828 0.181231 -0.007258 0.634142 -1.394989 0.41532
                -0.290985 -1.464534]
               (cb/drop-pose 1001 (c/ctrl-ranges horse) [0.4 0.8] [0 0 0])
               1e-6))
  (is (approx= 0.201568 (nth (cb/drop-pose 1001 (c/ctrl-ranges dog) [0.2 0.4] [0 0 0]) 2) 1e-6))
  (let [horse-pose (cb/drop-pose 1001 (c/ctrl-ranges horse) [0.4 0.8] [0 0 0])
        dog-pose (cb/drop-pose 1001 (c/ctrl-ranges dog) [0.2 0.4] [0 0 0])]
    (is (approx= (subvec horse-pose 3 25) (subvec dog-pose 3 25) 1e-6)))
  (is (approx= [4.5 8 2.193134 0.634347 0.440934 -0.369122 0.516652]
               (subvec (cb/drop-pose 1000 (c/ctrl-ranges horse) [0.4 0.8] [4.5 8 1.79]) 0 7)
               1e-6)))

;; B15

(deftest b15-up?-and-hud-segment
  (let [stand {:torso-m 0.65 :up-z 0.9}]
    (is (true? (cb/up? stand 1.79
                        (clj->js [0 0 0 0 0 2.5])
                        (clj->js [0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.95]))))
    (is (false? (cb/up? stand 1.79
                         (clj->js [0 0 0 0 0 2.4])
                         (clj->js [0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.95]))))
    (is (false? (cb/up? stand 1.79
                         (clj->js [0 0 0 0 0 2.5])
                         (clj->js [0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0 0.85])))))
  (is (= "" (cb/hud-segment nil)))
  (is (= " · brain twitch" (cb/hud-segment {:kind :twitch})))
  (is (= " · brain 3000 loading" (cb/hud-segment {:kind :policy :age 3000 :loading? true})))
  (is (= " · brain 9499 · up" (cb/hud-segment {:kind :policy :age 9499 :up? true})))
  (is (= " · brain 0 · down" (cb/hud-segment {:kind :policy :age 0 :up? false}))))
