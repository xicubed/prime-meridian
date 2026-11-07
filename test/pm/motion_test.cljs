(ns pm.motion-test
  (:require [cljs.test :refer [deftest is]]
            [pm.motion :as m]
            [pm.world :as w]))

(def W (w/load-estate w/estate-edn))

(defn near? [a b] (< (js/Math.abs (- a b)) 1e-6))
(defn vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))

(def F (m/walker {:id :f :name "F" :route [[0 0] [3 4] [6 0]] :pause-s 2}))
(def Fm (assoc F :pause 0 :to 1 :leg 1))

(defn run [w f n]
  (loop [w w i 0 picks []]
    (if (>= i n)
      picks
      (let [w' (f w)
            picks' (if (not= (:leg w) (:leg w')) (conj picks (:to w')) picks)]
        (recur w' (inc i) picks')))))

;; Config and walkers

(deftest t01-config-estate
  (is (= 1.2 (:speed (m/config W))))
  (is (= ["SM_Hiroshi_Terrain" "SM_Hiroshi_Gravel"] (:ground (m/config W))))
  (is (= :soma (:rig (:bricks (m/config W)))))
  (is (= "/vendor/three-motionbricks-0.2.0/index.mjs" (:lib (:bricks (m/config W))))))

(deftest t02-config-defaults
  (is (= {:bricks nil :ground [] :speed 1.2} (m/config {})))
  (is (= {:bricks nil :ground ["A"] :speed 1.2}
         (m/config {:motion {:speed -1 :ground ["A" 3]}}))))

(deftest t03-walkers-estate-ids-names
  (is (= [:un-automate-monde :le-testeur] (mapv :id (m/walkers W))))
  (is (= ["Un Automate" "Le Testeur"] (mapv :name (m/walkers W)))))

(deftest t04-walkers-estate-first
  (let [w1 (first (m/walkers W))]
    (is (vnear? [-6.5 -6.5] (:pos w1)))
    (is (= 0 (:to w1)))
    (is (= 1 (:dir w1)))
    (is (= 3 (:pause w1)))
    (is (= 0 (:leg w1)))
    (is (nil? (:ground w1)))
    (is (nil? (:y w1)))
    (is (near? 1.4288993 (:heading w1)))))

(deftest t05-walkers-estate-second
  (let [w2 (second (m/walkers W))]
    (is (vnear? [-5.0 -14.0] (:pos w2)))
    (is (= 5 (:pause w2)))
    (is (= 0 (:to w2)))
    (is (near? -1.8157750 (:heading w2)))))

(deftest t06-walker-fixture-f
  (is (vnear? [0 0] (:pos F)))
  (is (near? -0.9272952 (:heading F)))
  (is (= 2 (:pause F)))
  (is (= 2 (:pause-s F)))
  (is (= "F" (:name F))))

(deftest t07-walkers-invalid-entries
  (let [ws (m/walkers {:walkers [{:id "x" :route [[0 0]]}
                                  {:id :a :route []}
                                  {:id :b :route [[1] [2 3] ["a" 1] [4 5]] :pause-s -2}
                                  {:id :c :route [[0 0]] :name ""}]})
        by-id (into {} (map (juxt :id identity)) ws)]
    (is (= 2 (count ws)))
    (is (= [:b :c] (mapv :id ws)))
    (let [b (:b by-id)]
      (is (= [[2 3] [4 5]] (:route b)))
      (is (= 0 (:pause-s b)))
      (is (= 0 (:pause b)))
      (is (= "b" (:name b)))
      (is (vnear? [2 3] (:pos b)))
      (is (near? -0.7853982 (:heading b))))
    (let [c (:c by-id)]
      (is (= [[0 0]] (:route c)))
      (is (= "c" (:name c)))
      (is (= 0 (:heading c))))))

(deftest t08-walkers-empty
  (is (= [] (m/walkers {}))))

;; Ping-pong

(deftest t09-next-index-n3
  (is (= [1 1] (m/next-index 3 0 1)))
  (is (= [2 1] (m/next-index 3 1 1)))
  (is (= [1 -1] (m/next-index 3 2 1)))
  (is (= [0 -1] (m/next-index 3 1 -1)))
  (is (= [1 1] (m/next-index 3 0 -1))))

(deftest t10-next-index-edge
  (is (= [1 1] (m/next-index 2 0 1)))
  (is (= [0 -1] (m/next-index 2 1 1)))
  (is (= [1 1] (m/next-index 2 0 -1)))
  (is (= [0 1] (m/next-index 1 0 1)))
  (is (= [0 1] (m/next-index 0 0 -1))))

(deftest t11-target
  (is (vnear? [0 0] (m/target F)))
  (is (vnear? [3 4] (m/target Fm))))

(deftest t12-new-target
  (is (true? (m/new-target? F Fm)))
  (is (false? (m/new-target? F (assoc F :pause 1)))))

;; Pauses (step)

(deftest t13-step-pause-decrement
  (let [w' (m/step F 1 0.5)]
    (is (near? 1.5 (:pause w')))
    (is (vnear? [0 0] (:pos w')))
    (is (= 0 (:to w')))
    (is (= 0 (:leg w')))))

(deftest t14-step-pause-ends
  (let [w' (m/step F 1 2)]
    (is (= 0 (:pause w')))
    (is (= 1 (:to w')))
    (is (= 1 (:dir w')))
    (is (= 1 (:leg w')))
    (is (vnear? [0 0] (:pos w')))))

(deftest t15-step-pause-leftover-not-moved
  (let [w' (m/step F 1 3)]
    (is (vnear? [0 0] (:pos w')))
    (is (= 1 (:leg w')))))

(deftest t16-step-end-of-route
  (let [w' (m/step (assoc F :to 2 :dir 1 :pause 0.5 :pos [6 0] :leg 4) 1 1)]
    (is (= 1 (:to w')))
    (is (= -1 (:dir w')))
    (is (= 5 (:leg w')))
    (is (= 0 (:pause w')))))

(deftest t17-step-zero-or-negative-dt
  (is (near? 2 (:pause (m/step F 1 0))))
  (is (near? 2 (:pause (m/step F 1 -1)))))

;; Kinematic movement, arrival and heading

(deftest t18-step-move
  (let [w' (m/step Fm 1 1)]
    (is (vnear? [0.6 0.8] (:pos w')))
    (is (near? -0.9272952 (:heading w')))
    (is (= 0 (:pause w')))
    (is (= 1 (:leg w')))))

(def M (assoc Fm :pos [0.6 0.8]))

(deftest t19-step-move-more
  (let [w' (m/step M 1 3.6)]
    (is (vnear? [2.76 3.68] (:pos w')))
    (is (= 0 (:pause w')))))

(deftest t20-step-arrive
  (let [w' (m/step M 1 3.8)]
    (is (vnear? [2.88 3.84] (:pos w')))
    (is (near? 2 (:pause w')))
    (is (= 1 (:to w')))))

(deftest t21-step-overshoot-capped
  (let [w' (m/step M 10 1)]
    (is (vnear? [3 4] (:pos w')))
    (is (near? 2 (:pause w')))))

(deftest t22-step-already-inside
  (let [w' (m/step (assoc Fm :pos [2.9 4.0] :heading 0.5) 1 0.1)]
    (is (near? 2 (:pause w')))
    (is (vnear? [2.9 4.0] (:pos w')))
    (is (near? 0.5 (:heading w')))))

(deftest t23-step-zero-pause-goes-on
  (let [B (:b (into {} (map (juxt :id identity))
                     (m/walkers {:walkers [{:id :b :route [[1] [2 3] ["a" 1] [4 5]] :pause-s -2}]})))
        w' (m/step B 1 0.1)]
    (is (= 1 (:to w')))
    (is (= 1 (:dir w')))
    (is (= 1 (:leg w')))
    (is (= 0 (:pause w')))
    (is (vnear? [2 3] (:pos w')))))

(deftest t24-step-single-point-identical
  (let [C (:c (into {} (map (juxt :id identity))
                     (m/walkers {:walkers [{:id :c :route [[0 0]] :name ""}]})))]
    (is (identical? C (m/step C 1 5)))))

(deftest t25-yaw
  (is (near? 0 (m/yaw 1 0)))
  (is (near? 1.5707963 (m/yaw 0 -1)))
  (is (near? -1.5707963 (m/yaw 0 1)))
  (is (near? -0.9272952 (m/yaw 3 4)))
  (is (near? 3.1415927 (js/Math.abs (m/yaw -1 0)))))

(deftest t26-step-facing-neg-z
  (let [w0 (m/walker {:id :g :route [[0 0] [0 -2]] :pause-s 0})
        w1 (m/step w0 1 0.1)
        w2 (m/step w1 1 0.5)]
    (is (vnear? [0 -0.5] (:pos w2)))
    (is (near? 1.5707963 (:heading w2)))))

;; Real data, both walkers

(def W1 (first (m/walkers W)))
(def W2 (second (m/walkers W)))

(deftest t27-step-real-first-walker
  (let [after1 (m/step W1 1.2 3)]
    (is (= 1 (:leg after1)))
    (is (= 1 (:to after1)))
    (is (= 0 (:pause after1)))
    (let [after2 (m/step after1 1.2 1)]
      (is (vnear? [-6.3302944 -7.6879394] (:pos after2)))
      (is (near? 1.4288993 (:heading after2))))))

(deftest t28-step-real-second-walker
  (let [after2 (m/step (m/step W2 1.2 5) 1.2 1)]
    (is (vnear? [-5.2910428 -12.8358290] (:pos after2)))
    (is (near? -1.8157750 (:heading after2)))
    (is (= 1 (:to after2)))))

(deftest t29-step-every-walker-pauses-off
  (doseq [x (m/walkers W)]
    (let [x' (m/step x 1.2 (:pause-s x))]
      (is (= 1 (:leg x')))
      (is (= 1 (:to x')))
      (is (= 0 (:pause x'))))))

(deftest t30-order-fixture-f
  (let [picks (run F #(m/step % 1 0.1) 400)]
    (is (= [1 2 1 0 1 2] picks))
    (is (= 6 (:leg (nth (iterate #(m/step % 1 0.1) F) 400))))))

(deftest t31-order-second-walker
  (let [picks (run W2 #(m/step % 1.2 0.1) 320)]
    (is (= [1 2 1 0] picks))
    (is (= 4 (:leg (nth (iterate #(m/step % 1.2 0.1) W2) 320))))))

;; follow

(deftest t32-follow-move
  (let [w' (m/follow Fm [1 1] false 0.1)]
    (is (vnear? [1 1] (:pos w')))
    (is (= 0 (:pause w')))
    (is (= 1 (:to w')))
    (is (near? -0.9272952 (:heading w')))))

(deftest t33-follow-arrive-close
  (let [w' (m/follow Fm [2.9 3.9] false 0.1)]
    (is (near? 2 (:pause w')))))

(deftest t34-follow-idle-arrival
  (let [w' (m/follow Fm [1 1] true 0.1)]
    (is (near? 2 (:pause w')))
    (is (vnear? [1 1] (:pos w')))))

(deftest t35-follow-pausing-ignores-idle
  (let [w' (m/follow F [0.1 0] false 0.5)]
    (is (near? 1.5 (:pause w')))
    (is (vnear? [0.1 0] (:pos w'))))
  (let [w' (m/follow F [0 0] true 0.5)]
    (is (near? 1.5 (:pause w')))))

(deftest t36-follow-pause-ends-new-target
  (let [w' (m/follow F [0.1 0] false 2)]
    (is (= 0 (:pause w')))
    (is (= 1 (:to w')))
    (is (= 1 (:leg w')))
    (is (vnear? [0.1 0] (:pos w')))
    (is (true? (m/new-target? F w')))))

(deftest t37-follow-nil-pos-keeps-pos
  (let [w' (m/follow Fm nil false 0.1)]
    (is (vnear? [0 0] (:pos w')))
    (is (= 0 (:pause w')))))

(deftest t38-follow-second-walker
  (let [w' (m/follow (assoc W2 :pause 0 :to 1 :leg 1) [-6.1 -10.1] false 0.1)]
    (is (near? 5 (:pause w')))))

;; Ground

(def P ["SM_Hiroshi_Terrain" "SM_Hiroshi_Gravel"])

(deftest t39-ground-height-max-qualifying
  (is (near? 3.1 (m/ground-height [[3.1 "SM_Hiroshi_Terrain/M_Terrain"]
                                    [2.4 "SM_Hiroshi_Gravel"]] P))))

(deftest t40-ground-height-skips-non-ground
  (is (near? 2.65 (m/ground-height [[5.0 "pm-walkers/le-testeur"]
                                     [4.2 "SM_Tree_01/Bark"]
                                     [2.65 "SM_Hiroshi_Terrain"]] P))))

(deftest t41-ground-height-nil-cases
  (is (nil? (m/ground-height [[2.0 "Mesh"]] P)))
  (is (nil? (m/ground-height [] P)))
  (is (nil? (m/ground-height nil P)))
  (is (nil? (m/ground-height [[2.0 "X_SM_Hiroshi_Terrain"]] P)))
  (is (nil? (m/ground-height [[2.0 "SM_Hiroshi_Terrain"]] []))))

(deftest t42-ground-name
  (is (true? (m/ground-name? "SM_Hiroshi_Gravel_02" P)))
  (is (false? (m/ground-name? nil P))))

(deftest t43-sense-ground
  (is (near? 2.1 (:ground (m/sense-ground F [[2.1 "SM_Hiroshi_Terrain"]] P))))
  (is (near? 2.1 (:ground (m/sense-ground (assoc F :ground 2.1) [[9 "Rock"]] P)))))

;; Smoothing

(deftest t44-smooth-nil-cases
  (is (near? 2.1 (m/smooth nil 2.1 0.016)))
  (is (near? 2.0 (m/smooth 2.0 nil 0.1)))
  (is (nil? (m/smooth nil nil 0.1))))

(deftest t45-smooth-values
  (is (near? 2.6321206 (m/smooth 2.0 3.0 0.15)))
  (is (near? 2.0 (m/smooth 2.0 3.0 0)))
  (is (near? 3.0 (m/smooth 2.0 3.0 10))))

(deftest t46-settle
  (is (near? 2.65 (:y (m/settle (assoc F :ground 2.65) 0))))
  (is (near? 2.6321206 (:y (m/settle (assoc F :y 2.0 :ground 3.0) 0.15))))
  (is (near? 2.0 (:y (m/settle (assoc F :y 2.0) 0.1)))))

;; Flag, progress, reasons

(deftest t47-bricks-flag
  (is (true? (m/bricks-flag? "?bricks")))
  (is (true? (m/bricks-flag? "?probe=1,2&bricks")))
  (is (true? (m/bricks-flag? "?bricks=0")))
  (is (false? (m/bricks-flag? "")))
  (is (false? (m/bricks-flag? nil)))
  (is (false? (m/bricks-flag? "?bricksx")))
  (is (false? (m/bricks-flag? "?fresh"))))

(deftest t48-load-fraction
  (is (near? 0.37 (m/load-fraction "model" 37 100)))
  (is (= 0 (m/load-fraction "model" 5 0)))
  (is (= 0 (m/load-fraction "runtime" 0 1)))
  (is (= 1 (m/load-fraction "warmup" 0 1)))
  (is (nil? (m/load-fraction "other" 1 2))))

(deftest t49-short-reason
  (is (= "Failed to fetch" (m/short-reason "Failed to fetch")))
  (is (= "unknown" (m/short-reason nil)))
  (is (= "unknown" (m/short-reason "   ")))
  (is (= "line one" (m/short-reason "line one\nline two")))
  (is (= "spaced" (m/short-reason "  spaced  "))))

(deftest t50-short-reason-truncation
  (is (= (str (apply str (repeat 39 "a")) "…") (m/short-reason (apply str (repeat 50 "a")))))
  (is (= (apply str (repeat 40 "a")) (m/short-reason (apply str (repeat 40 "a"))))))

;; HUD

(deftest t51-hud-text-kinematic
  (is (= "motion: kinematic" (m/hud-text {:mode :kinematic}))))

(deftest t52-hud-text-loading
  (is (= "motion: loading model 37%" (m/hud-text {:mode :loading :progress 0.37})))
  (is (= "motion: loading model 100%" (m/hud-text {:mode :loading :progress 0.999})))
  (is (= "motion: loading model 0%" (m/hud-text {:mode :loading :progress nil})))
  (is (= "motion: loading model 100%" (m/hud-text {:mode :loading :progress 1.5})))
  (is (= "motion: loading model 0%" (m/hud-text {:mode :loading :progress -1}))))

(deftest t53-hud-text-bricks-plural
  (is (= "motion: bricks webgpu · 2 walkers · gen 420 ms"
         (m/hud-text {:mode :bricks :backend "webgpu" :walkers 2 :gen-ms 420.4}))))

(deftest t54-hud-text-bricks-singular
  (is (= "motion: bricks wasm · 1 walker · gen 420 ms"
         (m/hud-text {:mode :bricks :backend "wasm" :walkers 1 :gen-ms 419.5}))))

(deftest t55-hud-text-bricks-blank-backend-no-gen
  (is (= "motion: bricks · 2 walkers"
         (m/hud-text {:mode :bricks :backend "" :walkers 2 :gen-ms nil}))))

(deftest t56-hud-text-failed
  (is (= "motion: kinematic (bricks failed: no network)"
         (m/hud-text {:mode :failed :reason "no network"}))))

(deftest t57-hud-text-nil-and-unknown
  (is (nil? (m/hud-text nil)))
  (is (nil? (m/hud-text {:mode :off}))))

;; Figure data

(deftest t58-figure-data
  (is (= 3 (count (:parts m/figure))))
  (is (every? #(contains? #{:box :sphere :cylinder :cone :capsule} (:shape %)) (:parts m/figure)))
  (is (= #{:crew :crew-head :eye} (set (map :color (:parts m/figure)))))
  (is (= 1.95 (:label-y m/figure))))
