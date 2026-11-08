(ns pm.crew-walkers-test
  (:require [cljs.test :refer [deftest is]]
            [pm.motion :as m] [pm.avoid :as a] [pm.crew :as c] [pm.tint :as tint] [pm.world :as w]))

(def W (w/load-estate w/estate-edn))
(def WS (m/walkers W))
(defn by-id [id] (first (filter #(= id (:id %)) WS)))
(defn near? [x y] (< (js/Math.abs (- x y)) 1e-6))
(defn near3? [x y] (< (js/Math.abs (- x y)) 5e-4))   ; spec minima quoted to 3 decimals
(defn dist [[x1 z1] [x2 z2]] (js/Math.hypot (- x1 x2) (- z1 z2)))
(defn seg-dist [p a b]   ; distance from point p to segment ab
  (let [[px pz] p [ax az] a [bx bz] b dx (- bx ax) dz (- bz az) L (+ (* dx dx) (* dz dz))
        t (if (zero? L) 0 (max 0 (min 1 (/ (+ (* (- px ax) dx) (* (- pz az) dz)) L))))]
    (dist p [(+ ax (* t dx)) (+ az (* t dz))])))
(defn segs [wk] (let [r (:route wk) s (mapv vector r (rest r))]
                  (if (:loop wk) (conj s [(peek r) (first r)]) s)))
(def CREEK (mapv (fn [[x _ z _]] [x z]) (:points (first (:sounds W)))))
(defn creek-dist [p] (reduce min (map (fn [[a b]] (seg-dist p a b)) (map vector CREEK (rest CREEK)))))
(def NEW [:un-architecte :le-coureur :le-critique :le-docteur])

;; Six walkers from the data

(deftest t01-ids-agents-names
  (is (= [:un-automate-monde :le-testeur :un-architecte :le-coureur :le-critique :le-docteur]
         (mapv :id WS)))
  (is (= (mapv :id WS) (mapv :agent WS)))
  (is (= ["Un Automate" "Le Testeur" "Un Architecte" "Le Coureur" "Le Critique" "Le Docteur"]
         (mapv :name WS))))

(deftest t02-routes-and-pauses
  (is (= [[[-6.5 -6.5] [-6 -10] [-5 -14]]
          [[-5 -14] [-6 -10] [-6.5 -6.5]]
          [[6 -4] [4 -6] [3.5 -7.5]]
          [[3 -8.5] [5 -10] [3 -12] [2.2 -10]]
          [[-8 -4] [-7.2 -5.2]]
          [[-6 -17.5] [-6 -20]]]
         (mapv :route WS)))
  (is (= [3 5 8 1 6 6] (mapv :pause-s WS)))
  (is (= (mapv :pause-s WS) (mapv :pause WS)))
  (is (= (mapv first (mapv :route WS)) (mapv :pos WS))))

(deftest t03-tints-loop-face-speed-character
  (is (= [nil nil 0x4a7bd0 0xe8833a 0x8e5cc4 0x2f9e8f] (mapv :tint WS)))
  (is (= [nil nil 4881360 15237946 9329860 3120783] (mapv :tint WS)))
  (is (= [false false false true false false] (mapv :loop WS)))
  (is (= [nil nil nil nil [-6 -10] nil] (mapv :face WS)))
  (is (= [nil nil nil 1.9 nil nil] (mapv :speed WS)))
  (is (= [{:file "/assets/characters/polybot.glb" :rig :mixamo} nil nil nil nil nil]
         (mapv :character WS)))
  (is (= (vec (repeat 6 0.3)) (mapv :radius WS))))

(deftest t04-starting-headings
  (is (near? 1.4288993 (:heading (by-id :un-automate-monde))))
  (is (near? -1.8157750 (:heading (by-id :le-testeur))))
  (is (near? 2.3561945 (:heading (by-id :un-architecte))))
  (is (near? 0.6435011 (:heading (by-id :le-coureur))))
  (is (near? 0.9827937 (:heading (by-id :le-critique))))
  (is (near? 1.5707963 (:heading (by-id :le-docteur)))))

(deftest t05-walker-validation-of-new-keys
  (doseq [t [0x1000000 -1 "blue" 1.5 nil]]
    (is (nil? (:tint (m/walker {:id :a :route [[0 0]] :tint t}))) t))
  (is (= 0 (:tint (m/walker {:id :a :route [[0 0]] :tint 0}))))
  (is (= 16777215 (:tint (m/walker {:id :a :route [[0 0]] :tint 0xffffff}))))
  (is (false? (:loop (m/walker {:id :a :route [[0 0]] :loop "yes"}))))
  (is (true? (:loop (m/walker {:id :a :route [[0 0]] :loop true}))))
  (doseq [f [[1] "x" [1 "2"]]]
    (is (nil? (:face (m/walker {:id :a :route [[0 0]] :face f}))) f))
  (is (= [1 2] (:face (m/walker {:id :a :route [[0 0]] :face [1 2]})))))

;; Loop and face

(deftest t06-next-index-with-loop
  (is (= [1 1] (m/next-index 4 0 1 true)))
  (is (= [0 1] (m/next-index 4 3 1 true)))
  (is (= [3 1] (m/next-index 4 2 -1 true)))
  (is (= [0 1] (m/next-index 1 0 1 true)))
  (is (= [0 1] (m/next-index 2 1 1 true)))
  (is (= [2 -1] (m/next-index 4 3 1 false)))
  (is (= (m/next-index 4 3 1) (m/next-index 4 3 1 false))))

(deftest t07-looping-walker-goes-round
  (let [SQ (m/walker {:id :sq :route [[0 0] [1 0] [1 1] [0 1]] :loop true :pause-s 0})
        picks (loop [w SQ i 0 picks []]
                (if (>= i 200)
                  picks
                  (let [w' (m/step w 1 0.1)
                        picks' (if (not= (:leg w) (:leg w')) (conj picks (:to w')) picks)]
                    (recur w' (inc i) picks'))))
        SQ2 (dissoc SQ :loop)
        picks2 (loop [w SQ2 i 0 picks []]
                 (if (>= i 200)
                   picks
                   (let [w' (m/step w 1 0.1)
                         picks' (if (not= (:leg w) (:leg w')) (conj picks (:to w')) picks)]
                     (recur w' (inc i) picks'))))]
    (is (= [1 2 3 0 1 2 3 0] (take 8 picks)))
    (is (= [1 2 3 2 1 0 1 2] (take 8 picks2))))
  (let [[w' _] (m/advance [(assoc (by-id :le-coureur) :pause 0 :to 1 :leg 1)] [] {:speed 1.2 :horizon-s 2} 0.1 0)
        wk (first w')]
    (is (near? 3.152 (first (:pos wk))))
    (is (near? -8.614 (second (:pos wk))))
    (is (near? 1.52 (first (:vel wk))))
    (is (near? -1.14 (second (:vel wk))))))

(deftest t08-le-critique-faces-the-pair
  (let [K (by-id :le-critique)]
    (let [s1 (m/step K 1.2 0.5)]
      (is (near? 5.5 (:pause s1)))
      (is (near? 1.2490458 (:heading s1)))
      (is (near? 1.2490458 (:facing s1)))
      (is (= [0 0] (:vel s1)))
      (is (= [-8 -4] (:pos s1))))
    (let [s2 (m/step (assoc K :pos [-7.2 -5.2] :pause 3) 1.2 0.5)]
      (is (near? 1.3258177 (:heading s2))))
    (let [s3 (m/step (assoc K :pause 0.2) 1.2 0.5)]
      (is (= 1 (:to s3)))
      (is (near? 0 (:pause s3)))
      (is (near? 0.9827937 (:heading s3))))
    (let [f1 (m/follow K [-8 -4] true 0.5)]
      (is (near? 5.5 (:pause f1)))
      (is (near? 1.2490458 (:heading f1)))
      (is (near? 1.2490458 (:facing f1)))
      (is (= [0 0] (:vel f1)))))
  (is (near? -1.8157750 (:heading (m/step (by-id :le-testeur) 1.2 0.5))))
  (let [Z (m/walker {:id :z :route [[0 0] [0 1]] :pause-s 2 :face [0 0]})
        sz (m/step Z 1.2 0.5)]
    (is (near? -1.5707963 (:heading sz)))
    (is (nil? (:facing sz)))))

;; Walkable routes

(deftest t09-clear-of-the-statics
  (let [S (a/statics W)]
    (doseq [wk WS p (:route wk) s S]
      (is (>= (- (dist p (:at s)) (:r s) (:radius wk)) -1e-9) [wk p s]))
    (is (near? 0.2 (reduce min (for [wk WS p (:route wk) s S]
                                 (- (dist p (:at s)) (:r s) (:radius wk))))))
    (doseq [id NEW]
      (let [wk (by-id id) S (a/statics W)]
        (doseq [[a b] (segs wk) s S]
          (is (>= (- (seg-dist (:at s) a b) (:r s) (:radius wk)) -1e-9) [id a b s]))))
    (let [mins (for [id NEW]
                 (let [wk (by-id id)]
                   (reduce min (for [[a b] (segs wk) s S]
                                 (- (seg-dist (:at s) a b) (:r s) (:radius wk))))))]
      (is (every? true? (map near3? [7.329 3.381 2.842 4.857] mins))))))

(deftest t10-out-of-creek-and-in-patches
  (is (every? #(>= % 3.0) (for [wk WS p (:route wk)] (creek-dist p))))
  (is (near3? 3.328 (reduce min (for [wk WS p (:route wk)] (creek-dist p)))))
  (doseq [id [:un-automate-monde :le-testeur :le-critique]]
    (doseq [[x _] (:route (by-id id))] (is (< x -4) id)))
  (doseq [id [:un-architecte :le-coureur]]
    (doseq [[x _] (:route (by-id id))] (is (> x 2) id)))
  (is (> (reduce min (map second (:route (by-id :un-architecte))))
         (reduce max (map second (:route (by-id :le-coureur))))))
  (is (> (reduce min (map second (:route (by-id :le-critique))))
         (reduce max (map second (concat (:route (by-id :un-automate-monde))
                                          (:route (by-id :le-testeur)))))))
  (doseq [[_ z] (:route (by-id :le-docteur))] (is (<= z -17.5)))
  (is (every? #(>= (dist % [-5 -17]) 1.0) (:route (by-id :le-docteur))))
  (is (near? 1.1180340 (reduce min (map #(dist % [-5 -17]) (:route (by-id :le-docteur)))))))

(deftest t11-probe-stub-walkable-at-every-sample
  (let [probe (fn [x z]
                (cond
                  (some #(< (dist [x z] (:at %)) (:r %)) (a/statics W)) nil
                  (< (creek-dist [x z]) 1.5) nil
                  (< (dist [x z] [-5 -17]) 0.6) nil
                  (< z -16) 3.5
                  (and (< x -7) (> z -6)) 2.0
                  (< x -2) 2.65
                  (> z -8) 1.65
                  :else 1.8))]
    (doseq [wk WS [[x0 z0] [x1 z1]] (segs wk)]
      (let [n (max 1 (js/Math.ceil (/ (dist [x0 z0] [x1 z1]) 0.25)))]
        (doseq [i (range (inc n))]
          (let [t (/ i n)
                x (+ x0 (* t (- x1 x0)))
                z (+ z0 (* t (- z1 z0)))]
            (is (true? (m/walkable? (probe x z) (probe x0 z0) 0.4))
                [wk x0 z0 x z])))))))

;; Colours

(deftest t12-tint-hex
  (is (= 0x4775c2 (m/tint-hex 0xf4f2ee 0x4a7bd0)))
  (is (= 0xde7c36 (m/tint-hex 0xf4f2ee 0xe8833a)))
  (is (= 0x8857b7 (m/tint-hex 0xf4f2ee 0x8e5cc4)))
  (is (= 0x2d9685 (m/tint-hex 0xf4f2ee 0x2f9e8f)))
  (is (= 0x060b15 (m/tint-hex 0x16171a 0x4a7bd0)))
  (is (= 0x404040 (m/tint-hex 0x808080 0x808080)))
  (is (= 0x4a7bd0 (m/tint-hex 0xffffff 0x4a7bd0)))
  (is (= 0x123456 (m/tint-hex 0x123456 0xffffff)))
  (is (= 0x123456 (m/tint-hex 0x123456 nil)))
  (is (= 0x123456 (m/tint-hex 0x123456 -1)))
  (is (nil? (m/tint-hex nil 0x4a7bd0))))

(def PAL {:crew 0x5b7fa3 :crew-head 0xf0e6d6 :eye 0x222222})

(deftest t13-kinematic-figure-colours
  (is (= [0x1a3d85 0x466faf 0x222222] (m/figure-hexes PAL 0x4a7bd0)))
  (is (= [0x534125 0xda7631 0x222222] (m/figure-hexes PAL 0xe8833a)))
  (is (= [0x5b7fa3 0xf0e6d6 0x222222] (m/figure-hexes PAL nil))))

(defn stub-mat [hex]
  (let [col #js {:v hex}]
    (set! (.-getHex col) (fn [] (.-v col)))
    (set! (.-setHex col) (fn [h] (set! (.-v col) h) col))
    (let [mt #js {:color col}] (set! (.-clone mt) (fn [] (stub-mat (.-v col)))) mt)))
(defn stub-obj [nodes] (let [o #js {}] (set! (.-traverse o) (fn [f] (f o) (doseq [n nodes] (f n)))) o))

(deftest t14-tint-object-clones-shares-untouched
  (let [^js BODY (stub-mat 0xf4f2ee)
        ^js INK (stub-mat 0x16171a)
        ^js PLAIN #js {:clone (fn [] #js {})}
        ^js MA #js {:isMesh true :material BODY}
        ^js MB #js {:isMesh true :material BODY}
        ^js MC #js {:isMesh true :material #js [INK PLAIN]}
        ^js G #js {:isMesh false :material BODY}
        ^js O (stub-obj [MA MB MC G])]
    (is (= 2 (tint/tint-object! O 0x4a7bd0)))
    (is (identical? (.-material MA) (.-material MB)))
    (is (not (identical? (.-material MA) BODY)))
    (is (= 0x4775c2 (.getHex (.-color (.-material MA)))))
    (let [^js m0 (aget (.-material MC) 0)]
      (is (= 0x060b15 (.getHex (.-color m0)))))
    (is (identical? (aget (.-material MC) 1) PLAIN))
    (is (identical? (.-material G) BODY))
    (is (= 0xf4f2ee (.getHex (.-color BODY))))
    (let [^js MD #js {:isMesh true :material BODY}]
      (is (= 1 (tint/tint-object! (stub-obj [MD]) 0xe8833a)))
      (is (= 0xde7c36 (.getHex (.-color (.-material MD)))))
      (is (= 0x4775c2 (.getHex (.-color (.-material MA))))))
    (doseq [t [nil -1 "blue"]]
      (let [^js ME #js {:isMesh true :material BODY}]
        (is (= 0 (tint/tint-object! (stub-obj [ME]) t)) t)
        (is (identical? (.-material ME) BODY))))))

;; Names

(deftest t15-plate-name
  (is (= "Le Docteur (docs)"
         (c/plate-name {:titles {:le-docteur "Le Docteur (docs)"}} {:agent :le-docteur :name "Le Docteur"})))
  (is (= "Le Docteur" (c/plate-name {:titles {}} {:agent :le-docteur :name "Le Docteur"})))
  (is (= "X" (c/plate-name nil {:agent :x :name "X"})))
  (is (= "X" (c/plate-name {:titles {:x ""}} {:agent :x :name "X"}))))

(deftest t16-end-to-end-through-crew-edn
  (let [CE (c/parse (pr-str {:as-of 1000
                              :agents {:un-automate-monde {:status :working} :le-docteur {:status :blocked}}
                              :titles {:un-automate-monde "Un Automate (monde)" :le-testeur "Le Testeur"
                                       :un-architecte "Un Architecte" :le-coureur "Le Coureur"
                                       :le-critique "Le Critique" :le-docteur "Le Docteur"}}))]
    (is (= ["Un Automate (monde)" "Le Testeur" "Un Architecte" "Le Coureur" "Le Critique" "Le Docteur"]
           (mapv #(c/plate-name CE %) WS)))
    (is (= ["● Un Automate (monde)" "○ Le Testeur" "○ Un Architecte" "○ Le Coureur" "○ Le Critique" "● Le Docteur"]
           (mapv #(c/label-text (c/dot CE (:agent %) 3000 (c/colors W)) (c/plate-name CE %)) WS)))
    (is (= [false false false false false true]
           (mapv #(:pulse? (c/dot CE (:agent %) 3000 (c/colors W))) WS)))))

;; Generation gate

(deftest t17-enqueue-start-done
  (let [G0 m/empty-gate
        [G1 a] (m/gate-enqueue G0)
        [G2 b] (m/gate-enqueue G1)]
    (is (= 0 a))
    (is (= 1 b))
    (is (= {:queue [0 1] :in-flight nil :last-start nil :next-id 2} G2))
    (let [[G3 s] (m/gate-start G2 10)]
      (is (= 0 s))
      (is (= {:queue [1] :in-flight 0 :last-start 10 :next-id 2} G3))
      (is (= [G3 nil] (m/gate-start G3 11)))
      (let [G4 (m/gate-done G3 0)]
        (is (= {:queue [1] :in-flight nil :last-start 10 :next-id 2} G4))
        (is (= [G4 nil] (m/gate-start G4 10)))
        (is (= [{:queue [] :in-flight 1 :last-start 11 :next-id 2} 1] (m/gate-start G4 11))))
      (is (= G3 (m/gate-done G3 7)))
      (is (= [G0 nil] (m/gate-start G0 5)))
      (is (= [0 2 1] (mapv m/gate-queued [G0 G2 G3]))))))

(defn run-gate [n gen-frames last-frame]
  (loop [g (reduce (fn [g _] (first (m/gate-enqueue g))) m/empty-gate (range n)) f 1 started [] fly nil]
    (if (> f last-frame)
      started
      (let [fin?   (and fly (>= f (+ (:at fly) gen-frames)))
            g      (if fin? (m/gate-done g (:id fly)) g)
            fly    (if fin? nil fly)
            [g id] (m/gate-start g f)
            fly    (if id {:id id :at f} fly)
            now?   (and id (zero? gen-frames))
            g      (if now? (m/gate-done g id) g)
            fly    (if now? nil fly)]
        (recur g (inc f) (if id (conj started [f id]) started) fly)))))

(deftest t18-stagger-across-frames
  (let [r1 (run-gate 6 0 10)
        r2 (run-gate 3 3 10)
        r3 (run-gate 0 0 5)]
    (is (= [[1 0] [2 1] [3 2] [4 3] [5 4] [6 5]] r1))
    (is (= [[1 0] [4 1] [7 2]] r2))
    (is (= [] r3))
    (doseq [r [r1 r2 r3]]
      (when (>= (count r) 2)
        (is (apply distinct? (map first r)))))))

(deftest t19-hud-queue
  (is (= "motion: bricks webgpu · 6 walkers · gen 123 ms · queue 2"
         (m/hud-text {:mode :bricks :backend "webgpu" :walkers 6 :gen-ms 123.4 :queue 2})))
  (is (= "motion: bricks webgpu · 6 walkers · gen 123 ms"
         (m/hud-text {:mode :bricks :backend "webgpu" :walkers 6 :gen-ms 123.4 :queue 0})))
  (is (= "motion: bricks webgpu · 6 walkers · gen 123 ms"
         (m/hud-text {:mode :bricks :backend "webgpu" :walkers 6 :gen-ms 123.4 :queue nil})))
  (is (= "motion: bricks webgpu · 6 walkers · gen 123 ms"
         (m/hud-text {:mode :bricks :backend "webgpu" :walkers 6 :gen-ms 123.4})))
  (is (= "motion: bricks webgpu · 6 walkers · gen 123 ms · queue 2 · 1 detour"
         (m/hud-text {:mode :bricks :backend "webgpu" :walkers 6 :gen-ms 123.4 :queue 2 :detours 1})))
  (is (= "motion: bricks webgpu · 6 walkers · gen 123 ms · queue 1 · mannequin for Un Automate (no rig x)"
         (m/hud-text {:mode :bricks :backend "webgpu" :walkers 6 :gen-ms 123.4 :queue 1
                      :char-errors [{:name "Un Automate" :reason "no rig x"}]})))
  (is (= "motion: kinematic" (m/hud-text {:mode :kinematic :queue 3}))))

;; Six walkers together (kinematic)

(deftest t20-estate-scenario-all-six
  (let [cfg {:speed 1.2 :horizon-s 2}
        statics (a/statics W)
        n 2400
        dt 0.05
        res (loop [ws WS acc 0 i 0 min-pair js/Infinity min-static js/Infinity picks {}]
              (if (>= i n)
                {:ws ws :min-pair min-pair :min-static min-static :picks picks}
                (let [[ws' acc'] (m/advance ws statics cfg dt acc)
                      pair (reduce min js/Infinity
                                   (for [i1 (range (count ws')) i2 (range (inc i1) (count ws'))]
                                     (dist (:pos (nth ws' i1)) (:pos (nth ws' i2)))))
                      static (reduce min js/Infinity
                                     (for [wk ws' s statics]
                                       (- (dist (:pos wk) (:at s)) (:r s) (:radius wk))))
                      picks' (reduce (fn [p idx]
                                       (let [w (nth ws idx) w' (nth ws' idx)]
                                         (if (not= (:leg w) (:leg w'))
                                           (update p idx (fnil conj []) (:to w'))
                                           p)))
                                     picks (range (count ws)))]
                  (recur ws' acc' (inc i) (min min-pair pair) (min min-static static) picks'))))
        idx-of (fn [id] (first (keep-indexed (fn [i w] (when (= id (:id w)) i)) WS)))]
    (is (>= (:min-pair res) 0.6))
    (is (>= (:min-static res) -1e-9))
    (is (= [1 2 3 0 1 2 3 0] (take 8 (get (:picks res) (idx-of :le-coureur)))))
    (is (= [1 0 1 0 1 0 1 0] (take 8 (get (:picks res) (idx-of :le-critique)))))
    (is (= [1 0 1 0 1 0 1 0] (take 8 (get (:picks res) (idx-of :le-docteur)))))
    (is (= [1 2 1 0 1 2 1 0] (take 8 (get (:picks res) (idx-of :un-architecte)))))
    (is (>= (:leg (nth (:ws res) (idx-of :un-automate-monde))) 12))
    (is (>= (:leg (nth (:ws res) (idx-of :le-testeur))) 12))
    (is (>= (:leg (nth (:ws res) (idx-of :le-coureur))) 40))
    (let [[x z] (:pos (nth (:ws res) (idx-of :le-critique)))]
      (is (<= -8.01 x -7.19))
      (is (<= -5.21 z -3.99)))
    (let [[x z] (:pos (nth (:ws res) (idx-of :le-docteur)))]
      (is (near? -6 x))
      (is (<= -20.01 z -17.49)))))
