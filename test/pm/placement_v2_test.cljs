(ns pm.placement-v2-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            [cljs.reader :as reader]
            [pm.lot :as lot]
            [pm.view :as view]
            [pm.ask-build :as ab]
            [pm.blueprint :as bp]
            [pm.camera :as camera]
            [pm.blueprint-file :as bf]
            [pm.world :as w]))

(defn approx= [a b] (< (js/Math.abs (- a b)) 1e-9))

;; ---------------------------------------------------------------------------
;; fixtures (spec §9)

(def V   {:as-of 100000 :camera {:pos [2 10 -3] :target [2 1 2]} :center [2.2 1.0 1.9] :walkers {}})
(def GB  {:origin [0 0] :step 0.5 :cols 9 :rows 9                         ; all bushes: --at never looks
          :kinds (vec (repeat 9 "BBBBBBBBB")) :y (vec (repeat 9 (vec (repeat 9 1.25))))})
(def RAW (reader/read-string w/estate-edn))
(def DB  (:demo-builds RAW))
(def RULES (:build-rules RAW))
(def BENCH (get-in DB [:looks :bench :look]))
(def E0  {:demo-builds DB :mirrors [] :walkers []})
(def Em  (assoc E0 :mailbox {:at [4 1 -8]}))
(defn hctx [estate world here camera]
  {:estate estate :world world :file ab/empty-file :rules RULES :here here :camera camera :by :tw :now 5000})
(defn steep-bench []   ; pm.blueprint-seat-test's bench: feet at local x ±0.45
  {:id :bench :at [0 0 0] :yaw 0 :summary "proposed: a bench"
   :look {:parts [{:shape :box :size [1.1 0.1 0.4] :at [0 0.45 0] :color :brown}
                  {:shape :box :size [0.08 0.45 0.08] :at [0.45 0.225 0] :color :brown}
                  {:shape :box :size [0.08 0.45 0.08] :at [-0.45 0.225 0] :color :brown}]}})

;; ---------------------------------------------------------------------------
;; A. Facing the camera (lot/face-yaw)

(deftest t01-face-yaw-compass
  (is (= 0   (lot/face-yaw [0 0 0] [0 0 10])))
  (is (= 45  (lot/face-yaw [0 0 0] [10 0 10])))
  (is (= 90  (lot/face-yaw [0 0 0] [10 0 0])))
  (is (= 135 (lot/face-yaw [0 0 0] [10 0 -10])))
  (is (= 180 (lot/face-yaw [0 0 0] [0 0 -10])))
  (is (= 225 (lot/face-yaw [0 0 0] [-10 0 -10])))
  (is (= 270 (lot/face-yaw [0 0 0] [-10 0 0])))
  (is (= 315 (lot/face-yaw [0 0 0] [-10 0 10]))))

(deftest t02-face-yaw-front-points-at-camera
  (let [cases [[[0 0 10] 0] [[10 0 10] 45] [[10 0 0] 90] [[10 0 -10] 135]
               [[0 0 -10] 180] [[-10 0 -10] 225] [[-10 0 0] 270] [[-10 0 10] 315]]]
    (doseq [[cam yaw] cases]
      (is (= yaw (lot/face-yaw [0 0 0] cam))))
    (let [[x z] (lot/turn [0 1] 45)]
      (is (approx= 0.7071067811865476 x))
      (is (approx= 0.7071067811865476 z)))
    (let [[x z] (lot/turn [0 1] 135)]
      (is (approx= 0.7071067811865476 x))
      (is (approx= -0.7071067811865476 z)))
    (is (approx= -1 (first (lot/turn [0 1] 270))))
    (is (approx= 0 (second (lot/turn [0 1] 270))))))

(deftest t03-face-yaw-snapping
  (is (= 90 (lot/face-yaw [0 1 0] [10 5 4])))
  (is (= 45 (lot/face-yaw [0 1 0] [10 5 5]))))

(deftest t04-face-yaw-height-ignored
  (is (= 0 (lot/face-yaw [0 1 0] [0 50 10]))))

(deftest t05-face-yaw-degenerate
  (is (= 0 (lot/face-yaw [2 1 3] [2 30 3])))
  (is (= 0 (lot/face-yaw [2 1 3] nil)))
  (is (= 0 (lot/face-yaw nil [0 0 10]))))

(deftest t06-face-yaw-not-at-the-origin
  (is (= 180 (lot/face-yaw [2.2 1.0 1.9] [2 10 -3]))))

(deftest t07-here-word
  (is (= "where you're looking" lot/here-word))
  (is (= "where you're looking" (:word (:here (lot/view-landmarks V {}))))))

;; ---------------------------------------------------------------------------
;; B. --at here: the view (view/at-here)

(deftest t08-at-here-fresh
  (is (= {:ok true :at [2.2 1.0 1.9] :yaw 180 :landmark :here :facing? true}
         (view/at-here V 110000))))

(deftest t09-at-here-boundary
  (is (= {:ok true :at [2.2 1.0 1.9] :yaw 180 :landmark :here :facing? true}
         (view/at-here V 130000))))

(deftest t10-at-here-stale
  (is (= {:ok false :reason view/here-stale} (view/at-here V 130001)))
  (is (= "I can't see where you're looking: the game's view is more than 30 s old; look at the ground and ask again"
         view/here-stale)))

(deftest t11-at-here-missing-view
  (is (= {:ok false :reason lot/no-view} (view/at-here nil 110000)))
  (is (= {:ok false :reason lot/no-view} (view/at-here "junk" 110000)))
  (is (= {:ok false :reason lot/no-view} (view/at-here (dissoc V :as-of) 110000))))

(deftest t12-at-here-sky
  (is (= {:ok false :reason view/here-sky} (view/at-here (assoc V :center nil) 110000)))
  (is (= "the middle of your screen is sky: look at the ground and ask again" view/here-sky))
  (is (= {:ok false :reason view/here-sky} (view/at-here (assoc V :center [1 2]) 110000))))

(deftest t13-at-here-stale-before-sky
  (is (= view/here-stale (:reason (view/at-here (assoc V :center nil) 200000)))))

(deftest t14-at-here-no-camera
  (is (= {:ok true :at [2.2 1.0 1.9] :yaw 0 :landmark :here :facing? false}
         (view/at-here (assoc V :camera nil) 110000))))

(deftest t15-here-ms-and-fresh-unchanged
  (is (= 30000 view/here-ms))
  (is (= {:ok true :view V} (view/fresh V 200000))))

;; ---------------------------------------------------------------------------
;; C. --at x,z (view/at-xz)

(deftest t16-at-xz-with-ground-and-fresh-view
  (is (= {:ok true :at [1.5 1.25 2] :yaw 180 :landmark [1.5 2] :facing? true}
         (view/at-xz [1.5 2] GB V 110000))))

(deftest t17-at-xz-no-ground-map
  (is (= {:ok true :at [1.5 0 2] :yaw 180 :landmark [1.5 2] :facing? true}
         (view/at-xz [1.5 2] nil V 110000))))

(deftest t18-at-xz-no-view
  (is (= {:ok true :at [1.5 1.25 2] :yaw 0 :landmark [1.5 2] :facing? false}
         (view/at-xz [1.5 2] GB nil 110000)))
  (is (= {:ok true :at [1.5 1.25 2] :yaw 0 :landmark [1.5 2] :facing? false}
         (view/at-xz [1.5 2] GB V 200000))))

(deftest t19-at-xz-off-the-grid
  (is (= {:ok true :at [50 0 50] :yaw 225 :landmark [50 50] :facing? true}
         (view/at-xz [50 50] GB V 110000))))

;; ---------------------------------------------------------------------------
;; D. The command's pure parts (lot/parse-args, lot/at-line, lot/blueprint)

(deftest t20-parse-args-at-here
  (is (= {:mode :at :member :member-a :path "d.edn" :at :here :dry-run false}
         (lot/parse-args ["member-a" "d.edn" "--at" "here"]))))

(deftest t21-parse-args-at-xz-dry-run
  (is (= {:mode :at :member :member-a :path "d.edn" :at [1.5 -2] :dry-run true}
         (lot/parse-args ["member-a" "d.edn" "--at" "1.5,-2" "--dry-run"]))))

(deftest t22-parse-args-dry-run-before-at
  (is (= {:mode :at :member :member-a :path "d.edn" :at :here :dry-run true}
         (lot/parse-args ["member-a" "d.edn" "--dry-run" "--at" "here"]))))

(deftest t23-parse-args-at-usage-errors
  (is (= {:usage lot/usage} (lot/parse-args ["member-a" "d.edn" "--at" "mailbox"])))
  (is (= {:usage lot/usage} (lot/parse-args ["member-a" "d.edn" "--at"])))
  (is (= {:usage lot/usage} (lot/parse-args ["member-a" "d.edn" "--at" "here" "--near" "creek"])))
  (is (= {:usage lot/usage} (lot/parse-args ["member-a" "d.edn" "--at" "here" "--fast"])))
  (is (= {:usage lot/usage} (lot/parse-args ["Member" "d.edn" "--at" "here"]))))

(deftest t24-parse-args-old-forms-unchanged-and-usage-text
  (is (= {:mode :draft :member :member-a :path "d.edn" :near :creek :dry-run false}
         (lot/parse-args ["member-a" "d.edn" "--near" "creek"])))
  (is (true? (str/includes? lot/usage "--at <here|x,z>"))))

(deftest t25-at-line-facing
  (is (= "placed bench-3 where you're looking, at [2.2 1 1.9] yaw 180, facing the camera"
         (lot/at-line {:id :bench-3} (view/at-here V 110000)))))

(deftest t26-at-line-no-facing-no-here
  (is (= "placed bench-3 at [1.5 0 2] yaw 0"
         (lot/at-line {:id :bench-3} (view/at-xz [1.5 2] nil nil 0)))))

(deftest t27-blueprint-exact
  (is (= {:id :bench-3 :at [2.2 1.0 1.9] :yaw 180 :summary "proposed: a bench" :look BENCH}
         (lot/blueprint {:id :bench-3 :summary "proposed: a bench" :look BENCH} (view/at-here V 110000))))
  (is (true? (bf/valid? (lot/blueprint {:id :bench-3 :summary "proposed: a bench" :look BENCH} (view/at-here V 110000))))))

;; ---------------------------------------------------------------------------
;; E. The demo's here path (ab/here?, ab/plan-here)

(deftest t28-here?
  (is (true? (ab/here? (ab/ask (hctx E0 {} [0 1 0] nil) "put a bench here"))))
  (is (true? (ab/here? (ab/ask (hctx E0 {} [0 1 0] nil) "a bench please"))))
  (is (false? (ab/here? (ab/ask (hctx Em {} [0 1 0] nil) "a bench by the mailbox"))))
  (is (false? (ab/here? {:say "x"})))
  (is (false? (ab/here? nil))))

(deftest t29-plan-here-at-centre-facing-camera
  (is (= {:ok true :id :bench-1
          :blueprint {:id :bench-1 :at [1.5 1.2 -2] :yaw 0 :summary "A bench" :look BENCH :by :tw}
          :file {:as-of 5000 :blueprints [{:id :bench-1 :at [1.5 1.2 -2] :yaw 0 :summary "A bench" :look BENCH :by :tw}]
                 :real [] :removed []}
          :say "A bench, drawn near where you're looking. Try it, and make it real if you like it."}
         (ab/plan-here (hctx E0 {} [1.5 1.2 -2] [1.5 6 8]) {:look :bench :near :here}))))

(deftest t30-plan-here-another-facing
  (let [p (ab/plan-here (hctx E0 {} [1.5 1.2 -2] [-8 6 -2]) {:look :bench :near :here})]
    (is (= 270 (:yaw (:blueprint p))))
    (is (= [1.5 1.2 -2] (:at (:blueprint p))))))

(deftest t31-plan-here-no-search-and-no-gap
  (let [world {:blueprints [{:id :bench-1 :at [1.5 1.2 -2] :summary "x"}]}
        p     (ab/plan-here (hctx E0 world [1.5 1.2 -2] [1.5 6 8]) {:look :bench :near :here})]
    (is (true? (:ok p)))
    (is (= :bench-2 (:id p)))
    (is (= [1.5 1.2 -2] (:at (:blueprint p))))))

(deftest t32-plan-here-no-camera
  (let [p (ab/plan-here (hctx E0 {} [1.5 1.2 -2] nil) {:look :bench :near :here})]
    (is (= 0 (:yaw (:blueprint p))))))

(deftest t33-plan-here-sky
  (is (= {:ok false :say "The middle of your screen is sky. Look at the ground and ask me again."}
         (ab/plan-here (hctx E0 {} nil [0 5 5]) {:look :bench :near :here}))))

(deftest t34-plan-here-unknown-look
  (is (= {:ok false :say (get-in DB [:lines :help])}
         (ab/plan-here (hctx E0 {} [1.5 1.2 -2] [1.5 6 8]) {:look :rocket :near :here}))))

(deftest t35-named-places-still-measure
  (is (= {:req {:look :bench :near :mailbox} :target [4 -8]
          :spec {:origin [-3 -15] :step 0.5 :cols 29 :rows 29}
          :wide-spec {:origin [-12 -24] :step 0.5 :cols 65 :rows 65}}
         (ab/ask (hctx Em {} [0 1 0] nil) "a bench by the mailbox"))))

;; ---------------------------------------------------------------------------
;; F. No too-steep gate (bp/seat, bp/sign-text, approve, make-real)

(deftest t36-steep-bank-still-seats
  (let [b (bp/seat (steep-bench) (fn [x _] (+ 0.86 (* 1.4 x))))]
    (is (approx= 1.49 (second (:at b))))
    (is (approx= 1.26 (:seat-drop b)))
    (is (true? (:seated? b)))
    (is (false? (contains? b :too-steep?)))))

(deftest t37-low-leg-reaches-the-ground
  (let [b (bp/seat (steep-bench) (fn [x _] (+ 0.86 (* 1.4 x))))
        [_ hi lo] (get-in b [:look :parts])]
    (is (approx= 1.71 (get-in lo [:size 1])))
    (is (approx= -0.405 (get-in lo [:at 1])))
    (is (approx= 0.23 (+ (second (:at b)) (get-in lo [:at 1]) (- (/ (get-in lo [:size 1]) 2)))))
    (is (= [0.08 0.45 0.08] (:size hi)))
    (is (= [0.45 0.225 0] (:at hi)))))

(deftest t38-sign-is-just-the-hint
  (let [w  (bp/seat-all {:blueprint-text {:hint "click to build" :steep "too steep here"}
                         :blueprints [(steep-bench)]}
                        (fn [x _] (* 1.4 x)))
        bk (first (bp/pending w))]
    (is (approx= 1.26 (:seat-drop bk)))
    (is (= "proposed: a bench\nclick to build" (bp/sign-text w bk)))))

(deftest t39-builds-and-can-be-made-real
  (let [w (bp/seat-all {:blueprint-text {:hint "click to build" :steep "too steep here"}
                        :blueprints [(steep-bench)]}
                       (fn [x _] (* 1.4 x)))]
    (is (= :approved (bp/status (bp/approve-blueprint w :bench) :bench)))
    (is (= :real (bp/status (bp/make-real (bp/approve-blueprint w :bench) :bench) :bench)))))

(deftest t40-estate-words-lost-steep
  (is (false? (contains? (:blueprint-text RAW) :steep)))
  (is (= "click to build · right-click to dismiss" (get-in RAW [:blueprint-text :hint]))))

(deftest t41-seating-flat-ground-unchanged-apart-from-the-key
  (let [b (bp/seat (steep-bench) (fn [_ _] 1.5))]
    (is (= [0 1.5 0] (:at b)))
    (is (= 0 (:seat-drop b)))
    (is (false? (contains? b :too-steep?)))))

;; ---------------------------------------------------------------------------
;; G. No flight for a here ghost (camera/fly-decision)

(def base {:ghost [10 1 0] :camera [0 5 0] :on-screen? true :card-open? false :last-move-ms nil :now-ms 100000})

(deftest t42-center-m
  (is (= 1.0 camera/center-m)))

(deftest t43-fly-decision-at-center
  (is (= :at-center (camera/fly-decision nil (assoc base :center [10.3 1 0.4])))))

(deftest t44-fly-decision-center-boundary
  (is (= :at-center (camera/fly-decision nil (assoc base :center [11 1 0]))))
  (is (= :fly (camera/fly-decision nil (assoc base :center [12 1 0])))))

(deftest t45-fly-decision-center-xz-only
  (is (= :at-center (camera/fly-decision nil (assoc base :center [10 50 0])))))

(deftest t46-fly-decision-off-screen
  (is (= :fly (camera/fly-decision nil (assoc base :on-screen? false :center [10 1 0])))))

(deftest t47-fly-decision-order
  (is (= :card-open (camera/fly-decision nil (assoc base :center [10 1 0] :card-open? true))))
  (is (= :player-moving (camera/fly-decision nil (assoc base :center [10 1 0] :last-move-ms 99500))))
  (is (= :in-view (camera/fly-decision nil (assoc base :ghost [3 1 4] :camera [0 1 0] :center [20 1 20])))))

(deftest t48-fly-decision-v1-unchanged-without-center
  (is (= :fly (camera/fly-decision nil base)))
  (is (= :fly (camera/fly-decision nil (assoc base :center nil)))))

;; ---------------------------------------------------------------------------
;; H. Named places unchanged (lot/place)

(deftest t49-named-place-still-checks-the-ground
  (is (= {:ok false :reason "no level open ground within 1 m of the well: most of it is bushes or walls"}
         (lot/place GB {:well {:at [2 1 2]}} []
                    {:step-m 0.5 :yaws [0] :search-m 1 :gap-m 0 :route-m 0 :max-spread-m 0.1}
                    {:id :s :summary "s" :look {:parts [{:shape :box :size [0.2 0.4 0.2] :at [0 0.2 0]}]}}
                    :well))))
