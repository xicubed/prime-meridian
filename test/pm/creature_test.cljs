(ns pm.creature-test
  (:require [clojure.string :as str]
            [cljs.test :refer [deftest is]]
            [pm.creature :as c]
            [pm.species :as species]))

(defn approx=
  ([a b] (approx= a b 1e-6))
  ([a b eps]
   (cond
     (and (number? a) (number? b)) (< (js/Math.abs (- a b)) eps)
     (and (sequential? a) (sequential? b))
     (and (= (count a) (count b)) (every? true? (map #(approx= %1 %2 eps) a b)))
     (and (map? a) (map? b))
     (and (= (set (keys a)) (set (keys b)))
          (every? (fn [k] (approx= (get a k) (get b k) eps)) (keys a)))
     :else (= a b))))

(defn count-substr [s sub]
  (let [sub-len (count sub)]
    (loop [i 0 n 0]
      (let [idx (.indexOf s sub i)]
        (if (= idx -1) n (recur (+ idx sub-len) (inc n)))))))

(def horse (c/parse (get species/texts :horse)))
(def dog (c/parse (get species/texts :dog)))

(def f {:kind :fixture :density 1000 :kp 10 :damping 1 :colors {:c 0x808080}
        :bodies [{:name :root :capsule {:from [0 0 0] :to [0.1 0 0] :r 0.05} :color :c}
                 {:name :a :parent :root :at [0 0 0] :joint {:axis [0 1 0] :range [-10 10]}
                  :capsule {:from [0 0 0] :to [0 0 -0.1] :r 0.02} :color :c}
                 {:name :b :parent :root :at [0 0 0]
                  :capsule {:from [0 0 0] :to [0 0.2 0] :r 0.05} :color :c}
                 {:name :a2 :parent :a :at [0 0 -0.1] :joint {:axis [0 1 0] :range [0 90]}
                  :capsule {:from [0 0 0] :to [0 0 -0.1] :r 0.02} :color :c}]})

;; C1

(deftest c01-shipped-species-valid
  (is (= [] (c/problems horse)))
  (is (= [] (c/problems dog)))
  (is (= :horse (:kind horse)))
  (is (= :dog (:kind dog)))
  (is (= #{:horse :dog} (set (keys species/texts)))))

;; C2

(deftest c02-density-must-be-positive
  (is (= [":density must be a positive number"] (c/problems (assoc horse :density 0)))))

;; C3

(deftest c03-unknown-parent-and-parse-throws
  (let [bad (assoc-in horse [:bodies 4 :parent] :fl-uper)]
    (is (= ["body :fl-lower: unknown parent :fl-uper"] (c/problems bad)))
    (let [e (try (c/parse (pr-str bad)) (catch :default e e))]
      (is (= "bad species :horse: body :fl-lower: unknown parent :fl-uper" (ex-message e)))
      (is (= ["body :fl-lower: unknown parent :fl-uper"] (:problems (ex-data e)))))))

;; C4

(deftest c04-joint-axis-range-problem
  (is (= ["body :fl-upper: :joint needs a non-zero :axis [x y z] and :range [lo hi] with -180 <= lo < hi <= 180"]
         (c/problems (assoc-in dog [:bodies 3 :joint :range] [45 -45]))))
  (is (= ["body :fl-upper: :joint needs a non-zero :axis [x y z] and :range [lo hi] with -180 <= lo < hi <= 180"]
         (c/problems (assoc-in dog [:bodies 3 :joint :axis] [0 0 0])))))

;; C5

(deftest c05-color-not-in-colors
  (is (= ["body :head: :color :pink is not in :colors"]
         (c/problems (assoc-in horse [:bodies 2 :color] :pink)))))

;; C6

(deftest c06-duplicate-name
  (is (= ["body :neck: duplicate name"]
         (c/problems (update horse :bodies conj (get-in horse [:bodies 1]))))))

;; C7

(deftest c07-root-must-have-no-parent-at-joint
  (is (= ["root :torso must have no :parent, :at or :joint"]
         (c/problems (assoc-in horse [:bodies 0 :joint] {:axis [0 1 0] :range [-10 10]})))))

;; C8

(deftest c08-parse-unreadable-not-map-problems-nil
  (let [e (try (c/parse "{:kind") (catch :default e e))]
    (is (str/starts-with? (ex-message e) "bad species: not EDN")))
  (let [e (try (c/parse "[1 2]") (catch :default e e))]
    (is (= "bad species: not a map" (ex-message e))))
  (let [ps (c/problems nil)]
    (is (seq ps))
    (is (= ":kind must be a keyword" (first ps)))))

;; C9

(deftest c09-bodies-must-be-non-empty-and-needs-joint
  (is (= [":bodies must be a non-empty vector"] (c/problems (assoc horse :bodies []))))
  (is (= ["needs at least one :joint"]
         (c/problems (assoc horse :bodies [(get-in horse [:bodies 0])])))))

;; C10

(deftest c10-capsule-needs-from-to-r
  (is (= ["body :fl-upper: :capsule needs :from, :to ([x y z], different) and :r > 0"]
         (c/problems (assoc-in horse [:bodies 3 :capsule :to] [0 0 0]))))
  (is (= ["body :fl-upper: :capsule needs :from, :to ([x y z], different) and :r > 0"]
         (c/problems (assoc-in horse [:bodies 3 :capsule :r] 0)))))

;; C11

(deftest c11-all-problems-in-order
  (is (= [":density must be a positive number" ":kp must be a positive number"]
         (c/problems (-> dog (assoc :density -1) (assoc :kp 0))))))

;; C12

(deftest c12-fmt
  (is (= "8" (c/fmt 8.0)))
  (is (= "3.03" (c/fmt 3.0300000000000002)))
  (is (= "-0.698132" (c/fmt -0.6981317007977318)))
  (is (= "0" (c/fmt -0.0)))
  (is (= "0" (c/fmt -1e-7)))
  (is (= "1.5" (c/fmt 1.5)))
  (is (= "0.707107" (c/fmt 0.7071067811865476))))

;; C13

(deftest c13-bodies-dfs-order
  (is (= [:torso :neck :head :fl-upper :fl-lower :fr-upper :fr-lower
          :hl-upper :hl-lower :hr-upper :hr-lower]
         (mapv :name (c/bodies horse))))
  (is (= [:torso :neck :head :fl-upper :fl-lower :fr-upper :fr-lower
          :hl-upper :hl-lower :hr-upper :hr-lower]
         (mapv :name (c/bodies dog))))
  (is (= [] (c/problems f)))
  (is (= [:root :a :a2 :b] (mapv :name (c/bodies f))))
  (is (= [:a :a2] (mapv :name (c/joints f)))))

;; C14

(deftest c14-joints-order-and-ranges
  (is (= [:neck :fl-upper :fl-lower :fr-upper :fr-lower :hl-upper :hl-lower :hr-upper :hr-lower]
         (mapv :name (c/joints horse))))
  (let [expected [[-0.698132 0.523599] [-0.785398 0.785398] [-0.174533 1.745329]
                   [-0.785398 0.785398] [-0.174533 1.745329] [-0.785398 0.785398]
                   [-1.745329 0.174533] [-0.785398 0.785398] [-1.745329 0.174533]]]
    (is (approx= expected (c/ctrl-ranges horse)))
    (is (approx= expected (c/ctrl-ranges dog))))
  (is (= [0 1 0] (:axis (first (c/joints dog))))))

;; C15

(deftest c15-counts
  (is (= {:nbody 12 :njnt 10 :nu 9 :nq 16 :nv 15 :ngeom 12} (c/counts horse)))
  (is (= {:nbody 12 :njnt 10 :nu 9 :nq 16 :nv 15 :ngeom 12} (c/counts dog)))
  (is (= {:nbody 5 :njnt 3 :nu 2 :nq 9 :nv 8 :ngeom 5} (c/counts f))))

;; C16

(deftest c16-horse-body-masses
  (let [expected [[:torso 56.548668] [:neck 4.297699] [:head 5.439144]
                   [:fl-upper 3.508112] [:fl-lower 2.127905]
                   [:fr-upper 3.508112] [:fr-lower 2.127905]
                   [:hl-upper 3.508112] [:hl-lower 2.127905]
                   [:hr-upper 3.508112] [:hr-lower 2.127905]]
        masses (c/body-masses horse)]
    (is (approx= expected masses))
    (is (approx= 88.829579 (reduce + (map second masses))))))

;; C17

(deftest c17-dog-body-masses
  (let [expected [[:torso 18.011798] [:neck 1.335962] [:head 1.544616]
                   [:fl-upper 0.949285] [:fl-lower 0.622035]
                   [:fr-upper 0.949285] [:fr-lower 0.622035]
                   [:hl-upper 0.949285] [:hl-lower 0.622035]
                   [:hr-upper 0.949285] [:hr-lower 0.622035]]
        masses (c/body-masses dog)]
    (is (approx= expected masses))
    (is (approx= 27.177656 (reduce + (map second masses))))
    (is (every? #(pos? (second %)) masses))
    (is (every? #(pos? (second %)) (c/body-masses horse)))
    (is (approx= 36.023596 (second (first (c/body-masses (assoc dog :density 2000))))))))

;; C18

(deftest c18-rest-bottom
  (is (approx= -0.84 (c/rest-bottom horse)))
  (is (approx= -0.44 (c/rest-bottom dog)))
  (is (approx= -0.22 (c/rest-bottom f))))

;; C19

(deftest c19-spawn
  (is (approx= {:ground [4.5 8 1.79] :torso [4.5 8 3.03] :quat [1 0 0 0]}
               (c/spawn horse {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})))
  (is (approx= [4.5 8 2.63] (:torso (c/spawn dog {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4}))))
  (is (approx= {:ground [0 0 0] :torso [0 0 0.84] :quat [0.707107 0 0 0.707107]}
               (c/spawn dog {:at [0 0 0] :yaw 90 :drop-m 0.4})))
  (is (= (c/spawn horse {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})
         (c/spawn horse {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4 :flag "pony" :seed 3}))))

;; C20

(deftest c20-mjcf-horse
  (let [x (c/mjcf horse {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})]
    (is (= 11 (count-substr x "<body ")))
    (is (= 9 (count-substr x "<joint ")))
    (is (= 1 (count-substr x "<freejoint ")))
    (is (= 9 (count-substr x "<position ")))
    (is (= 12 (count-substr x "<geom ")))
    (is (= 1 (count-substr x "type=\"plane\"")))
    (is (str/starts-with? x "<mujoco model=\"horse\">\n"))
    (is (str/ends-with? x "</mujoco>\n"))
    (let [trimmed (set (map str/trim (str/split-lines x)))]
      (is (contains? trimmed "<option timestep=\"0.002\" integrator=\"implicitfast\"/>"))
      (is (contains? trimmed "<geom name=\"ground\" type=\"plane\" pos=\"4.5 8 1.79\" size=\"2 2 0.1\" contype=\"1\" conaffinity=\"0\"/>"))
      (is (contains? trimmed "<body name=\"torso\" pos=\"4.5 8 3.03\" quat=\"1 0 0 0\">"))
      (is (contains? trimmed "<joint name=\"hl-lower\" type=\"hinge\" axis=\"0 1 0\" limited=\"true\" range=\"-1.745329 0.174533\" damping=\"5\"/>"))
      (is (contains? trimmed "<geom name=\"head\" type=\"capsule\" fromto=\"0 0 0 0.24 0 -0.1\" size=\"0.07\" density=\"1000\" contype=\"0\" conaffinity=\"1\"/>"))
      (is (contains? trimmed "<position name=\"neck\" joint=\"neck\" kp=\"150\" ctrllimited=\"true\" ctrlrange=\"-0.698132 0.523599\"/>")))
    (let [lines (str/split-lines x)
          idx (first (keep-indexed (fn [i l] (when (str/includes? l "<body name=\"head\" pos=\"0.18 0 0.24\">") i)) lines))]
      (is (some? idx))
      (is (str/starts-with? (str/trim (nth lines (inc idx))) "<geom ")))))

;; C21

(deftest c21-mjcf-dog
  (let [x (c/mjcf dog {:at [4.5 1.79 -8.0] :yaw 0 :drop-m 0.4})]
    (is (= 11 (count-substr x "<body ")))
    (is (= 9 (count-substr x "<joint ")))
    (is (= 1 (count-substr x "<freejoint ")))
    (is (= 9 (count-substr x "<position ")))
    (is (= 12 (count-substr x "<geom ")))
    (is (= 1 (count-substr x "type=\"plane\"")))
    (let [trimmed (set (map str/trim (str/split-lines x)))]
      (is (contains? trimmed "<mujoco model=\"dog\">"))
      (is (contains? trimmed "<body name=\"torso\" pos=\"4.5 8 2.63\" quat=\"1 0 0 0\">"))
      (is (contains? trimmed "<geom name=\"fl-upper\" type=\"capsule\" fromto=\"0 0 0 0 0 -0.2\" size=\"0.035\" density=\"1000\" contype=\"0\" conaffinity=\"1\"/>"))
      (is (contains? trimmed "<joint name=\"neck\" type=\"hinge\" axis=\"0 1 0\" limited=\"true\" range=\"-0.698132 0.523599\" damping=\"1.5\"/>"))
      (is (contains? trimmed "<position name=\"hr-lower\" joint=\"hr-lower\" kp=\"40\" ctrllimited=\"true\" ctrlrange=\"-1.745329 0.174533\"/>")))))

;; C22

(deftest c22-mjcf-yaw-and-origin
  (let [x (c/mjcf dog {:at [0 0 0] :yaw 90 :drop-m 0.4})
        trimmed (set (map str/trim (str/split-lines x)))]
    (is (contains? trimmed "<geom name=\"ground\" type=\"plane\" pos=\"0 0 0\" size=\"2 2 0.1\" contype=\"1\" conaffinity=\"0\"/>"))
    (is (contains? trimmed "<body name=\"torso\" pos=\"0 0 0.84\" quat=\"0.707107 0 0 0.707107\">")))
  (let [y (c/mjcf horse c/origin)
        trimmed (set (map str/trim (str/split-lines y)))]
    (is (contains? trimmed "<body name=\"torso\" pos=\"0 0 1.24\" quat=\"1 0 0 0\">"))))

;; C23

(deftest c23-mjcf-throws-on-bad-species
  (let [e (try (c/mjcf (assoc horse :kp -1) c/origin) (catch :default e e))]
    (is (= "bad species :horse: :kp must be a positive number" (ex-message e)))))

;; C24

(deftest c24-mj-three-conversions
  (is (= [1 3 -2] (c/mj->three [1 2 3])))
  (is (= [1 2 3] (c/three->mj [1 3 -2])))
  (is (= [0 1 0] (c/mj->three [0 0 1])))
  (is (= [0 0 -1] (c/mj->three [0 1 0])))
  (is (= [4.5 8.0 1.79] (c/three->mj [4.5 1.79 -8.0])))
  (is (approx= [0.3 -0.7 2.5] (c/three->mj (c/mj->three [0.3 -0.7 2.5])))))

;; C25

(deftest c25-mj-quat->three
  (is (approx= [0 0 0 1] (c/mj-quat->three [1 0 0 0])))
  (is (approx= [0.1 0.3 -0.2 0.7] (c/mj-quat->three [0.7 0.1 0.2 0.3])))
  (is (approx= [0 0.707107 0 0.707107] (c/mj-quat->three [0.707107 0 0 0.707107])))
  (is (approx= [0 0 -0.6 0.8] (c/mj-quat->three [0.8 0 0.6 0]))))

;; C26

(deftest c26-body-part
  (is (approx= {:shape :capsule :size [0.9 0.3 0.3] :at [0 0 0] :rot [0 0 0] :color 0x9b6b43}
               (c/body-part horse (get-in horse [:bodies 0]))))
  (is (approx= {:shape :capsule :size [0.48 0.1 0.1] :at [0 -0.19 0] :rot [0 0 -90] :color 0x9b6b43}
               (c/body-part horse (nth (c/bodies horse) 3))))
  (is (approx= {:shape :capsule :size [0.45 0.08 0.08] :at [0 -0.185 0] :rot [0 0 -90] :color 0x2e2622}
               (c/body-part horse (nth (c/bodies horse) 4))))
  (is (approx= {:shape :capsule :size [0.42 0.12 0.12] :at [0.09 0.12 0] :rot [0 0 53.130102] :color 0x4a3426}
               (c/body-part horse (nth (c/bodies horse) 1))))
  (is (approx= {:shape :capsule :size [0.4 0.14 0.14] :at [0.12 -0.05 0] :rot [0 0 -22.619865] :color 0x9b6b43}
               (c/body-part horse (nth (c/bodies horse) 2))))
  (is (approx= {:shape :capsule :size [0.3 0.1 0.1] :at [0 0 -0.1] :rot [0 90 0] :color 0x808080}
               (c/body-part f (nth (c/bodies f) 3)))))

;; C27

(deftest c27-body-parts-dog
  (let [parts (c/body-parts dog)]
    (is (= 11 (count parts)))
    (is (= [1 2 3 4 5 6 7 8 9 10 11] (mapv :id parts)))
    (is (= [:torso :neck :head :fl-upper :fl-lower :fr-upper :fr-lower
            :hl-upper :hl-lower :hr-upper :hr-lower]
           (mapv :name parts)))
    (is (approx= {:shape :capsule :size [0.64 0.2 0.2] :at [0 0 0] :rot [0 0 0] :color 0xc49a6c}
                 (:part (first parts))))
    (is (approx= {:size [0.27 0.07 0.07] :at [0 -0.1 0] :rot [0 0 -90]}
                 (select-keys (:part (nth parts 3)) [:size :at :rot])))
    (is (= 0x6b4a32 (:color (:part (nth parts 2)))))))
