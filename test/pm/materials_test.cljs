(ns pm.materials-test
  (:require [cljs.test :refer [deftest is testing]]
            [pm.materials :as m]
            [pm.world :as w]))

(def KM  {:brown {:name "MAT_Hiroshi_Timber" :tile-m 0.6} :tin {:name "MAT_Hiroshi_Metal"}})
(def IDX {"MAT_Hiroshi_Timber" :timber "MAT_Hiroshi_Metal" :metal "MAT_Hiroshi_Stone" :stone})
(def SEAT {:shape :box  :size [1.1 0.1 0.4]   :at [0 0.45 0] :color :brown})
(def LEG  {:shape :box  :size [0.08 0.45 0.08] :at [0.45 0.225 0.15] :color :brown})
(def POST {:shape :box  :size [0.3 2.4 0.3]   :at [0 1.2 0] :color :tin})
(def SHADE {:shape :cone :size [0.7 0.4 0.7]  :at [0 2.75 0] :color :tin})
(def BULB {:shape :sphere :size [0.3 0.3 0.3] :at [0 2.45 0] :color :gold :glow true})
(defn near? [a b] (and (= (count a) (count b)) (every? #(< (js/Math.abs %) 1e-6) (map - a b))))
(defn near-faces? [m e] (every? (fn [k] (near? (get m k) (get e k))) [:x :y :z]))

;; 4.1 index-materials

(deftest case-1-index-materials-basic
  (is (= {"MAT_Hiroshi_Timber" :t "MAT_Hiroshi_Metal" :m}
         (m/index-materials [["MAT_Hiroshi_Timber" :t] ["MAT_Hiroshi_Metal" :m]]))))

(deftest case-2-index-materials-first-wins
  (is (= {"A" :a1} (m/index-materials [["A" :a1] ["A" :a2]]))))

(deftest case-3-index-materials-wind-ignored
  (is (= {"MAT_Hiroshi_Stone" :s}
         (m/index-materials [["MAT_Hiroshi_Foliage / wind" :w] ["MAT_Hiroshi_Stone" :s]]))))

(deftest case-4-index-materials-bad-names-and-nil-materials-dropped
  (is (= {} (m/index-materials [["" :x] ["  " :x2] [nil :y] [7 :z] ["B" nil]]))))

(deftest case-5-index-materials-nil-and-empty
  (is (= {} (m/index-materials nil)))
  (is (= {} (m/index-materials []))))

;; 4.2 props-item?

(deftest case-6-props-item-true-for-blueprint-and-workshop
  (is (true? (m/props-item? {:key [:blueprint :bench-x]})))
  (is (true? (m/props-item? {:key [:workshop :box]}))))

(deftest case-7-props-item-false-for-mirrors
  (is (false? (m/props-item? {:key [:flora :body] :mirror :flora})))
  (is (false? (m/props-item? {:key [:juniper :body] :mirror :juniper}))))

;; 4.3 material-for

(deftest case-8-material-for-mapped-with-tile
  (is (= {:material :timber :tile-m 0.6} (m/material-for SEAT KM IDX)))
  (is (= {:material :timber :tile-m 0.6} (m/material-for LEG KM IDX))))

(deftest case-9-material-for-second-key-no-tile
  (is (= {:material :metal :tile-m nil} (m/material-for POST KM IDX)))
  (is (= {:material :metal :tile-m nil} (m/material-for SHADE KM IDX))))

(deftest case-10-material-for-unmapped-keys
  (is (nil? (m/material-for (assoc SEAT :color :gold) KM IDX)))
  (is (nil? (m/material-for (assoc SEAT :color :booth-red) KM IDX)))
  (is (nil? (m/material-for (assoc SEAT :color :glass) KM IDX))))

(deftest case-11-material-for-glow-part
  (is (nil? (m/material-for BULB KM IDX)))
  (is (nil? (m/material-for (assoc POST :glow true) KM IDX))))

(deftest case-12-material-for-no-kit-materials
  (is (nil? (m/material-for SEAT nil IDX)))
  (is (nil? (m/material-for SEAT {} IDX))))

(deftest case-13-material-for-kit-not-up
  (is (nil? (m/material-for SEAT KM {})))
  (is (nil? (m/material-for SEAT KM nil))))

(deftest case-14-material-for-name-not-in-kit
  (is (nil? (m/material-for SEAT {:brown {:name "MAT_Nope"}} IDX))))

(deftest case-15-material-for-wind-name
  (is (nil? (m/material-for SEAT {:brown {:name "MAT_Hiroshi_Timber / wind"}}
                             (assoc IDX "MAT_Hiroshi_Timber / wind" :w)))))

(deftest case-16-material-for-bad-entries
  (is (nil? (m/material-for SEAT {:brown "MAT_Hiroshi_Timber"} IDX)))
  (is (nil? (m/material-for SEAT {:brown {:name 7}} IDX)))
  (is (nil? (m/material-for (assoc SEAT :color "brown") KM IDX))))

(deftest case-17-material-for-bad-tile
  (doseq [t [0 -1 "0.6" js/NaN]]
    (is (= {:material :timber :tile-m nil}
           (m/material-for SEAT {:brown {:name "MAT_Hiroshi_Timber" :tile-m t}} IDX)))))

(deftest case-18-material-for-any-other-kit-material
  (is (= {:material :stone :tile-m 1}
         (m/material-for SEAT {:brown {:name "MAT_Hiroshi_Stone" :tile-m 1}} IDX))))

;; 4.4 box-face-repeats

(deftest case-19-box-face-repeats-seat
  (is (near-faces? (m/box-face-repeats [1.1 0.1 0.4] 0.6)
                   {:x [0.6666667 0.1666667] :y [1.8333333 0.6666667] :z [1.8333333 0.1666667]})))

(deftest case-20-box-face-repeats-leg
  (is (near-faces? (m/box-face-repeats [0.08 0.45 0.08] 0.6)
                   {:x [0.1333333 0.75] :y [0.1333333 0.1333333] :z [0.1333333 0.75]})))

(deftest case-21-box-face-repeats-exact
  (is (= {:x [4 3] :y [2 4] :z [2 3]} (m/box-face-repeats [2 3 4] 1))))

(deftest case-22-box-face-repeats-no-tile
  (is (= {:x [1 1] :y [1 1] :z [1 1]} (m/box-face-repeats [1.1 0.1 0.4] nil))))

(deftest case-23-box-face-repeats-bad-tile
  (doseq [t [0 -1 "0.6" js/NaN]]
    (is (= {:x [1 1] :y [1 1] :z [1 1]} (m/box-face-repeats [1.1 0.1 0.4] t)))))

(deftest case-24-box-face-repeats-bad-size
  (doseq [size [nil [1 1] [1 0 1] [1 -2 1]]]
    (is (= {:x [1 1] :y [1 1] :z [1 1]} (m/box-face-repeats size 1)))))

;; 4.5 shape-repeat

(deftest case-25-shape-repeat-cylinder
  (is (near? [0.4188790 1.5] (m/shape-repeat :cylinder [0.08 0.9 0.08] 0.6))))

(deftest case-26-shape-repeat-cone
  (is (near? [2.1991149 0.4] (m/shape-repeat :cone [0.7 0.4 0.7] 1))))

(deftest case-27-shape-repeat-other-shapes
  (is (= [1 1] (m/shape-repeat :sphere [0.3 0.3 0.3] 0.6)))
  (is (= [1 1] (m/shape-repeat :capsule [0.3 0.3 0.3] 0.6)))
  (is (= [1 1] (m/shape-repeat :box [0.3 0.3 0.3] 0.6))))

(deftest case-28-shape-repeat-bad-inputs
  (is (= [1 1] (m/shape-repeat :cylinder [0.08 0.9 0.08] nil)))
  (is (= [1 1] (m/shape-repeat :cylinder nil 0.6))))

;; 4.6 bevel-radius

(deftest case-29-bevel-radius-seat
  (is (= 0.02 (m/bevel-radius [1.1 0.1 0.4]))))

(deftest case-30-bevel-radius-leg
  (is (= 0.02 (m/bevel-radius [0.08 0.45 0.08]))))

(deftest case-31-bevel-radius-post
  (is (= 0.02 (m/bevel-radius [0.3 2.4 0.3]))))

(deftest case-32-bevel-radius-thin
  (is (= 0.0125 (m/bevel-radius [0.05 1 1])))
  (is (= 0.0015 (m/bevel-radius [0.09 0.024 0.006]))))

(deftest case-33-bevel-radius-bad
  (doseq [size [nil [1 1] [1 0 1] [1 -1 1] [1 js/NaN 1] "x"]]
    (is (= 0 (m/bevel-radius size)))))

(deftest case-34-bevel-constants
  (is (= 0.02 m/bevel-max-m))
  (is (= 5 m/bevel-segments)))

;; 4.7 round-box-vertex (size [1 0.5 0.4], r 0.02, n 5, box = [0.48 0.23 0.18])

(deftest case-35-round-box-vertex-face-middle
  (let [v (m/round-box-vertex [0.5 0.1 0.1] [1 0.5 0.4] 0.02 5)]
    (is (near? [0.5 0.23 0.18] (:pos v)))
    (is (near? [1 0 0] (:normal v)))))

(deftest case-36-round-box-vertex-mirrored-face-middle
  (let [v (m/round-box-vertex [0.5 -0.1 -0.1] [1 0.5 0.4] 0.02 5)]
    (is (near? [0.5 -0.23 -0.18] (:pos v)))
    (is (near? [1 0 0] (:normal v)))))

(deftest case-37-round-box-vertex-top-face-middle
  (let [v (m/round-box-vertex [0.1 0.5 -0.1] [1 0.5 0.4] 0.02 5)]
    (is (near? [0.48 0.25 -0.18] (:pos v)))
    (is (near? [0 1 0] (:normal v)))))

(deftest case-38-round-box-vertex-edge
  (let [v (m/round-box-vertex [0.5 0.5 0.1] [1 0.5 0.4] 0.02 5)]
    (is (near? [0.4941421 0.2441421 0.18] (:pos v)))
    (is (near? [0.7071068 0.7071068 0] (:normal v)))))

(deftest case-39-round-box-vertex-edge-part-way-round
  (let [v (m/round-box-vertex [0.5 0.3 0.1] [1 0.5 0.4] 0.02 5)]
    (is (near? [0.4978885 0.2389443 0.18] (:pos v)))
    (is (near? [0.8944272 0.4472136 0] (:normal v)))))

(deftest case-40-round-box-vertex-corner
  (let [v (m/round-box-vertex [0.5 0.5 0.5] [1 0.5 0.4] 0.02 5)]
    (is (near? [0.4915470 0.2415470 0.1915470] (:pos v)))
    (is (near? [0.5773503 0.5773503 0.5773503] (:normal v)))))

(deftest case-41-round-box-vertex-opposite-sign-corner
  (let [v (m/round-box-vertex [-0.5 0.5 -0.5] [1 0.5 0.4] 0.02 5)]
    (is (near? [-0.4915470 0.2415470 -0.1915470] (:pos v)))
    (is (near? [-0.5773503 0.5773503 -0.5773503] (:normal v)))))

(deftest case-42-round-box-vertex-exact-third
  (let [v (m/round-box-vertex [0.3 0.5 0.5] [1 0.5 0.4] 0.02 5)]
    (is (near? [0.3333333 0.6666667 0.6666667] (:normal v)))
    (is (near? [0.4866667 0.2433333 0.1933333] (:pos v)))))

(deftest case-43-round-box-vertex-same-extents-as-box
  (let [coords [-0.5 -0.3 -0.1 0.1 0.3 0.5]
        verts (for [x coords y coords z coords
                    :when (some #(= 0.5 (js/Math.abs %)) [x y z])]
                (:pos (m/round-box-vertex [x y z] [1 0.5 0.4] 0.02 5)))]
    (is (every? #(<= (js/Math.abs (nth % 0)) (+ 0.5 1e-9)) verts))
    (is (every? #(<= (js/Math.abs (nth % 1)) (+ 0.25 1e-9)) verts))
    (is (every? #(<= (js/Math.abs (nth % 2)) (+ 0.2 1e-9)) verts))
    (is (some #(> (js/Math.abs (nth % 0)) (- 0.5 1e-9)) verts))
    (is (some #(> (js/Math.abs (nth % 1)) (- 0.25 1e-9)) verts))
    (is (some #(> (js/Math.abs (nth % 2)) (- 0.2 1e-9)) verts))))

(deftest case-44-round-box-vertex-r-zero
  (let [v (m/round-box-vertex [0.5 0.3 0.1] [1 0.5 0.4] 0 5)]
    (is (near? [0.5 0.25 0.2] (:pos v)))
    (is (near? [0.8944272 0.4472136 0] (:normal v)))))

(deftest case-45-round-box-vertex-leg-size-and-radius
  (let [v (m/round-box-vertex [0.5 0.5 0.5] [0.08 0.45 0.08] 0.02 5)]
    (is (near? [0.0315470 0.2165470 0.0315470] (:pos v)))))

;; 4.8 missing-names

(deftest case-46-missing-names-all-found
  (is (= [] (m/missing-names KM IDX))))

(deftest case-47-missing-names-one-missing
  (is (= ["MAT_Hiroshi_Timber"] (m/missing-names KM {"MAT_Hiroshi_Metal" :m})))
  (is (= ["MAT_Hiroshi_Metal" "MAT_Hiroshi_Timber"] (m/missing-names KM {}))))

(deftest case-48-missing-names-distinct-sorted-wind-bad-entries
  (is (= ["W / wind" "X"]
         (m/missing-names {:brown {:name "X"} :tin {:name "X"} :gold {:name "W / wind"}
                            :moss "Y" :pink {:name 7}}
                           {"W / wind" :w}))))

(deftest case-49-missing-names-nil-and-empty-kit-materials
  (is (= [] (m/missing-names nil IDX)))
  (is (= [] (m/missing-names {} IDX))))

;; 4.9 the shipped data

(deftest case-50-shipped-kit-materials
  (is (= {:brown {:name "MAT_Hiroshi_Timber" :tile-m 0.6} :tin {:name "MAT_Hiroshi_Metal"}}
         (:kit-materials (w/load-estate w/estate-edn)))))

(deftest case-51-shipped-blueprints-use-kit-materials
  (let [world (w/load-estate w/estate-edn)
        km    (:kit-materials world)
        bps   (:blueprints world)
        bench (first (filter #(= :bench-by-mailbox (:id %)) bps))
        lamp  (first (filter #(= :lamp-post-by-canal (:id %)) bps))]
    (testing "the bench's parts all get timber, tiled"
      (doseq [part (get-in bench [:look :parts])]
        (is (= {:material :timber :tile-m 0.6} (m/material-for part km IDX)))))
    (testing "the lamp's post and shade get metal, untiled; the bulb stays unmapped"
      (let [[post shade bulb] (get-in lamp [:look :parts])]
        (is (= {:material :metal :tile-m nil} (m/material-for post km IDX)))
        (is (= {:material :metal :tile-m nil} (m/material-for shade km IDX)))
        (is (nil? (m/material-for bulb km IDX)))))))
