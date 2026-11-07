(ns pm.characters-test
  (:require [cljs.test :refer [deftest is]]
            [pm.motion :as m]
            [pm.avoid :as a]
            [pm.world :as w]))

(def W (w/load-estate w/estate-edn))

(defn near? [a b] (< (js/Math.abs (- a b)) 1e-6))
(defn vnear? [a b] (and (= (count a) (count b)) (every? true? (map near? a b))))
(defn dist [[x1 z1] [x2 z2]] (js/Math.hypot (- x1 x2) (- z1 z2)))

(defn sim [ws statics n]
  (loop [walkers ws acc 0 i 0 min-d js/Infinity min-static js/Infinity
         picks (vec (repeat (count ws) []))]
    (if (>= i n)
      [walkers min-d min-static picks]
      (let [[walkers' acc'] (m/advance walkers statics {:speed 1.2 :horizon-s 2} 0.05 acc)
            d (if (>= (count walkers') 2)
                (dist (:pos (nth walkers' 0)) (:pos (nth walkers' 1)))
                js/Infinity)
            gaps (for [wk walkers' c statics] (- (dist (:pos wk) (:at c)) (:r c) (:radius wk)))
            sg (if (seq gaps) (reduce min js/Infinity gaps) js/Infinity)
            picks' (vec (map-indexed (fn [idx p]
                                        (if (not= (:leg (nth walkers idx)) (:leg (nth walkers' idx)))
                                          (conj p (:to (nth walkers' idx)))
                                          p))
                                      picks))]
        (recur walkers' acc' (inc i) (min min-d d) (min min-static sg) picks')))))

;; Rig names

(deftest t01-rig-key-simple
  (is (= "mixamo" (m/rig-key :mixamo))))

(deftest t02-rig-key-two-parts
  (is (= "mixamoPlusZ" (m/rig-key :mixamo-plus-z))))

(deftest t03-rig-key-camel
  (is (= "robotExpressive" (m/rig-key :robot-expressive)))
  (is (= "unitreeH1" (m/rig-key :unitree-h1))))

(deftest t04-rig-key-non-keyword
  (is (nil? (m/rig-key "mixamo")))
  (is (nil? (m/rig-key nil))))

;; Character entries

(deftest t05-character-explicit
  (is (= {:file "/assets/characters/polybot.glb" :rig :mixamo}
         (m/character {:file "/assets/characters/polybot.glb" :rig :mixamo}))))

(deftest t06-character-default-rig
  (is (= {:file "/a.glb" :rig :mixamo} (m/character {:file "/a.glb"}))))

(deftest t07-character-https
  (is (= {:file "https://x.test/a.glb" :rig :mixamo-plus-z}
         (m/character {:file "https://x.test/a.glb" :rig :mixamo-plus-z}))))

(deftest t08-character-drops-extra-keys
  (is (= {:file "/a.glb" :rig :mixamo} (m/character {:file "/a.glb" :rig :mixamo :scale 3}))))

(deftest t09-character-nil-cases
  (doseq [c [{:file "assets/a.glb"}
             {:file ""}
             {:file "   "}
             {:file "http://x.test/a.glb"}
             {:rig :mixamo}
             {:file 7}
             {:file "/a.glb" :rig "mixamo"}
             "polybot"
             nil]]
    (is (nil? (m/character c)))))

(deftest t10-walkers-estate-characters
  (is (= [{:file "/assets/characters/polybot.glb" :rig :mixamo} nil]
         (mapv :character (m/walkers W)))))

(deftest t11-walker-character-nil-cases
  (is (nil? (:character (m/walker {:id :f :route [[0 0] [1 0]]}))))
  (let [w' (m/walker {:id :f :route [[0 0]] :character {:file "x"}})]
    (is (nil? (:character w')))
    (is (= :f (:id w')))))

;; Display scale and radius

(deftest t12-display-scale
  (is (= 1.0 (m/display-scale W)))
  (is (= 1.0 (m/display-scale {})))
  (is (= 1.4 (m/display-scale {:motion {:display-scale 1.4}}))))

(deftest t13-display-scale-defaults
  (is (= 1.0 (m/display-scale {:motion {:display-scale 0}})))
  (is (= 1.0 (m/display-scale {:motion {:display-scale -1}})))
  (is (= 1.0 (m/display-scale {:motion {:display-scale "2"}})))
  (is (= 1.0 (m/display-scale {:motion {:display-scale nil}}))))

(deftest t14-walkers-radius-scaled
  (is (= [0.6 0.8]
         (mapv :radius (m/walkers {:motion {:display-scale 2 :radius 0.3}
                                    :walkers [{:id :a :route [[0 0]]}
                                              {:id :b :route [[0 0]] :radius 0.4}]})))))

(deftest t15-walkers-radius-estate-scaled
  (let [W14 (assoc-in W [:motion :display-scale] 1.4)]
    (is (every? true? (map near? [0.42 0.42] (mapv :radius (m/walkers W14)))))
    (is (= [0.3 0.3] (mapv :radius (m/walkers W))))))

(deftest t16-walker-radius-unscaled
  (is (= 0.4 (:radius (m/walker {:id :a :route [[0 0]] :radius 0.4})))))

(deftest t17-scaled-walkers-avoid
  (let [W14 (assoc-in W [:motion :display-scale] 1.4)
        [final min-d min-s picks] (sim (m/walkers W14) (a/statics W) 2400)]
    (is (>= min-d 0.84))
    (is (>= min-s 0))
    (is (= [1 2 1 0 1 2 1 0] (take 8 (picks 0))))
    (is (= [1 2 1 0 1 2 1 0] (take 8 (picks 1))))
    (is (>= (:leg (nth final 0)) 12))
    (is (>= (:leg (nth final 1)) 12))))

;; Labels

(deftest t18-label-height-scaled-top
  (is (near? 2.59 (m/label-height 1.6 1.4))))

(deftest t19-label-height-top
  (is (near? 1.95 (m/label-height 1.6 1)))
  (is (near? 2.15 (m/label-height 1.8 1))))

(deftest t20-label-height-nil-top
  (is (near? 1.95 (m/label-height nil 1)))
  (is (near? 2.73 (m/label-height nil 1.4))))

(deftest t21-label-height-invalid-top
  (is (near? 1.95 (m/label-height 0 1)))
  (is (near? 1.95 (m/label-height -1 1)))
  (is (near? 1.95 (m/label-height ##NaN 1)))
  (is (near? 1.95 (m/label-height ##Inf 1))))

;; HUD

(deftest t22-char-errors-text-empty
  (is (nil? (m/char-errors-text [])))
  (is (nil? (m/char-errors-text nil))))

(deftest t23-char-errors-text-one
  (is (= "mannequin for Un Automate (Failed to fetch)"
         (m/char-errors-text [{:name "Un Automate" :reason "Failed to fetch"}]))))

(deftest t24-char-errors-text-two
  (is (= "mannequin for A (x), B (no rig mixamoX)"
         (m/char-errors-text [{:name "A" :reason "x"} {:name "B" :reason "no rig mixamoX"}]))))

(deftest t25-hud-text-char-errors-one
  (is (= "motion: bricks webgpu · 2 walkers · gen 420 ms · mannequin for Un Automate (Failed to fetch)"
         (m/hud-text {:mode :bricks :backend "webgpu" :walkers 2 :gen-ms 420.4
                       :char-errors [{:name "Un Automate" :reason "Failed to fetch"}]}))))

(deftest t26-hud-text-char-errors-and-detours
  (is (= "motion: bricks wasm · 2 walkers · mannequin for A (x), B (y) · 1 detour"
         (m/hud-text {:mode :bricks :backend "wasm" :walkers 2
                       :char-errors [{:name "A" :reason "x"} {:name "B" :reason "y"}]
                       :detours 1}))))

(deftest t27-hud-text-no-char-errors
  (is (= "motion: bricks webgpu · 2 walkers · gen 420 ms"
         (m/hud-text {:mode :bricks :backend "webgpu" :walkers 2 :gen-ms 420.4 :char-errors []}))))

(deftest t28-hud-text-char-errors-ignored-other-modes
  (is (= "motion: kinematic"
         (m/hud-text {:mode :kinematic :char-errors [{:name "A" :reason "x"}]})))
  (is (= "motion: kinematic (bricks failed: z)"
         (m/hud-text {:mode :failed :reason "z" :char-errors [{:name "A" :reason "x"}]}))))
