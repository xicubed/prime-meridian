(ns pm.tidy-after-remove-test
  "Tests for TASKS T-16 (crew round tidy-after-remove-v0): the lobby signpost
   goes while the lobby stays an errand spot (A), the trial card stays
   pinned on screen up close (B), and a real build's sign drops
   \"proposed: \" from its summary (C)."
  (:require [cljs.test :refer [deftest is]]
            [pm.plan :as p]
            [pm.pick :as pk]
            [pm.blueprint :as bp]
            [pm.world :as w]
            [pm.content :as c]))

(def WE (w/load-estate w/estate-edn))
(def TXT {:hint "click to build" :trial "trial: try it out" :removing "will be removed" :failed "won't build"})
(def BL {:id :lamp    :at [1 2 3] :summary "proposed: a lamp post"})
(def BB {:id :bench   :at [0 0 0] :summary "Proposed: a bench"})
(def BP {:id :planter :at [5 0 5] :summary "a planter"})
(def WB (bp/apply-real-builds {:blueprint-text TXT :blueprints [BL BB BP]} {:real [:lamp :bench :planter]}))
(defn bp-of [w id] (first (filter #(= id (:id %)) (bp/all w))))
(def LOOK {:parts [{:shape :box :size [0.1 1.4 0.1]  :at [0 0.7 0] :color :brown}
                   {:shape :box :size [0.9 0.4 0.06] :at [0 1.3 0] :color :tin}]})
(defn near? [a b] (and (= (count a) (count b)) (every? #(< (js/Math.abs %) 1e-9) (map - a b))))
(def SRC {:agent "hal-say" :session "t" :machine "local"})

;; -- 4.2 Lobby ---------------------------------------------------------------

(deftest L1-no-look-no-text->nil
  (is (nil? (p/lobby-item {:lobby {:at [1 2 3] :yaw 0}}))))

(deftest L2-text-but-no-look->nil
  (is (nil? (p/lobby-item {:lobby {:at [1 2 3] :text "Lobby"}}))))

(deftest L3-look-present->todays-item
  (let [item (p/lobby-item {:lobby {:at [1 2 3] :yaw 90 :look LOOK :text "Lobby"}})]
    (is (= [:lobby :box] (:key item)))
    (is (= [1 2 3] (:at item)))
    (is (= 90 (:yaw item)))
    (is (= 2 (count (:parts item))))
    (is (= :sign (:label item)))
    (is (= "Lobby" (:text item)))
    (is (near? [1 2.75 3] (:center item)))
    (is (near? [1 3.8 3] (:label-at item)))))

(deftest L4-shipped-lobby-keys
  (is (= #{:at :yaw} (set (keys (:lobby WE)))))
  (is (nil? (p/lobby-item WE))))

(deftest L5-the-other-fixtures-still-draw
  (is (= [:workshop :box] (:key (p/workshop-item WE))))
  (is (= [:mailbox :box] (:key (p/mailbox-item WE c/sample-inbox))))
  (is (= [:phone-booth :box] (:key (p/phone-booth-item WE)))))

(deftest L6-still-an-errand-spot
  (is (near? [2.7 -22.4] (w/errand-target WE "lobby" [] [2.7 -20.6] 1.2))))

(deftest L7-already-close
  (is (= [2.7 -23.0] (w/errand-target WE "lobby" [] [2.7 -23.0] 1.2))))

(deftest L8-the-errand-event-to-the-lobby-is-recorded
  (is (= {:where "lobby" :text "Tea's on." :since 1800000000000 :state :pending}
         (get-in (w/apply-event WE {:type :agent-event :agent "hal" :kind :errand
                                     :where "lobby" :text "Tea's on." :source SRC}
                                 1800000000000)
                 [:errands "hal"]))))

;; -- 4.3 screen-of with min-y --------------------------------------------------

(deftest P1-anchor-above-the-top->pinned
  (is (= {:x 400 :y 68 :visible? true} (pk/screen-of [0 1.5 0.5] [800 600] 68))))

(deftest P2-far-above-off-centre
  (is (= {:x 200 :y 68 :visible? true} (pk/screen-of [-0.5 3 0.2] [800 600] 68))))

(deftest P3-anchor-exactly-at-the-top-edge
  (is (= {:x 400 :y 68 :visible? true} (pk/screen-of [0 1 0.5] [800 600] 68))))

(deftest P4-inside-near-the-top-card-would-hang-off
  (is (= {:x 600 :y 68 :visible? true} (pk/screen-of [0.5 0.9 0.5] [800 600] 68))))

(deftest P5-inside-clear-of-min-y->unchanged
  (is (= {:x 400 :y 300 :visible? true} (pk/screen-of [0 0 0.5] [800 600] 68)))
  (is (= {:x 600 :y 450 :visible? true} (pk/screen-of [0.5 -0.5 0.9] [800 600] 68))))

(deftest P6-bottom-edge-still-visible
  (is (= {:x 400 :y 600 :visible? true} (pk/screen-of [0 -1 0.5] [800 600] 68))))

(deftest P7-off-the-right-or-left->hidden-same-as-2-arity
  (doseq [n [[1.2 1.5 0.5] [1.2 0 0.5] [-1.01 0 0.5]]]
    (is (= (pk/screen-of n [800 600]) (pk/screen-of n [800 600] 68)))
    (is (false? (:visible? (pk/screen-of n [800 600] 68))))))

(deftest P8-below-the-bottom->hidden
  (is (= (pk/screen-of [0 -1.2 0.5] [800 600]) (pk/screen-of [0 -1.2 0.5] [800 600] 68)))
  (is (false? (:visible? (pk/screen-of [0 -1.2 0.5] [800 600] 68)))))

(deftest P9-behind-the-camera->hidden
  (is (= {:x 400 :y -150 :visible? false} (pk/screen-of [0 1.5 1.2] [800 600] 68))))

(deftest P10-no-or-bad-min-y->2-arity
  (doseq [m [nil js/NaN js/Infinity "68"]]
    (is (= {:x 400 :y -150 :visible? false} (pk/screen-of [0 1.5 0.5] [800 600] m)))
    (is (= {:x 400 :y 300 :visible? true} (pk/screen-of [0 0 0.5] [800 600] m)))))

(deftest P11-bad-ndc-or-size-with-min-y
  (is (= {:x 0 :y 0 :visible? false} (pk/screen-of nil [800 600] 68)))
  (is (= {:x 0 :y 0 :visible? false} (pk/screen-of [0 0] [800 600] 68)))
  (is (= {:x 0 :y 0 :visible? false} (pk/screen-of [js/NaN 0 0] [800 600] 68)))
  (is (= {:x 0 :y 0 :visible? false} (pk/screen-of [0 0 0] [0 600] 68)))
  (is (= {:x 0 :y 0 :visible? false} (pk/screen-of [0 0 0] nil 68))))

(deftest P12-the-gap
  (is (= 8 pk/card-pin-gap-px)))

;; -- 4.4 pin-min-y --------------------------------------------------------------

(deftest M1-base-on-screen
  (is (= 60 (pk/pin-min-y [0 0 0.5] 52))))

(deftest M2-base-below-the-bottom-tall-item-fills-the-screen
  (is (= 60 (pk/pin-min-y [0 -1.5 0.5] 52))))

(deftest M3-base-at-the-top-edge
  (is (= 60 (pk/pin-min-y [0 1 0.5] 52))))

(deftest M4-base-above-the-top-whole-item-above
  (is (nil? (pk/pin-min-y [0 1.01 0.5] 52))))

(deftest M5-base-behind-the-camera
  (is (nil? (pk/pin-min-y [0 0 1.2] 52))))

(deftest M6-base-off-the-side-x-not-checked
  (is (= 60 (pk/pin-min-y [1.5 0 0.5] 52))))

(deftest M7-card-not-measured-yet
  (is (= 8 (pk/pin-min-y [0 0 0.5] 0))))

(deftest M8-bad-base
  (doseq [base [nil [0 0] [js/NaN 0 0] "x"]]
    (is (nil? (pk/pin-min-y base 52)))))

(deftest M9-bad-card-h
  (doseq [card-h [nil -1 js/NaN "52"]]
    (is (nil? (pk/pin-min-y [0 0 0.5] card-h)))))

(deftest M10-together-item-on-screen
  (is (= {:x 400 :y 60 :visible? true}
         (pk/screen-of [0 1.5 0.5] [800 600] (pk/pin-min-y [0 -0.3 0.5] 52)))))

(deftest M11-together-item-above-the-top
  (is (= {:x 400 :y -150 :visible? false}
         (pk/screen-of [0 1.5 0.5] [800 600] (pk/pin-min-y [0 1.2 0.5] 52)))))

;; -- 4.5 Real-build summary -------------------------------------------------------

(deftest R1-prefix-dropped
  (is (= "a lamp post" (bp/sign-summary {:summary "proposed: a lamp post" :status :real}))))

(deftest R2-case-insensitive
  (is (= "a bench" (bp/sign-summary {:summary "Proposed: a bench" :status :real})))
  (is (= "a pond" (bp/sign-summary {:summary "PROPOSED:a pond" :status :real}))))

(deftest R3-extra-spaces
  (is (= "a well" (bp/sign-summary {:summary "proposed:   a well" :status :real}))))

(deftest R4-no-prefix
  (is (= "a planter" (bp/sign-summary {:summary "a planter" :status :real}))))

(deftest R5-not-leading-no-colon
  (is (= "the proposed: path" (bp/sign-summary {:summary "the proposed: path" :status :real})))
  (is (= "proposed a gate" (bp/sign-summary {:summary "proposed a gate" :status :real}))))

(deftest R6-only-one
  (is (= "proposed: x" (bp/sign-summary {:summary "proposed: proposed: x" :status :real}))))

(deftest R7-prefix-only
  (is (= "" (bp/sign-summary {:summary "proposed:" :status :real}))))

(deftest R8-other-statuses-unchanged
  (doseq [status [:pending :approved :removing :rejected nil]]
    (is (= "proposed: a lamp post" (bp/sign-summary {:summary "proposed: a lamp post" :status status})))))

(deftest R9-non-string-summary
  (is (nil? (bp/sign-summary {:status :real})))
  (is (= 7 (bp/sign-summary {:summary 7 :status :real}))))

;; -- 4.6 Signs -----------------------------------------------------------------

(deftest G1-real-prefix
  (is (= "a lamp post" (bp/sign-text WB (bp-of WB :lamp)))))

(deftest G2-the-second-real-build
  (is (= "a bench" (bp/sign-text WB (bp-of WB :bench)))))

(deftest G3-real-no-prefix
  (is (= "a planter" (bp/sign-text WB (bp-of WB :planter)))))

(deftest G4-real-no-wording-in-the-data
  (is (= "a lamp post" (bp/sign-text {} (assoc BL :status :real)))))

(deftest G5-pending-unchanged
  (is (= "proposed: a lamp post\nclick to build"
         (:text (first (bp/ghost-plan {:blueprint-text TXT :blueprints [BL]}))))))

(deftest G6-failed-unchanged
  (is (= "proposed: a lamp post\nwon't build"
         (:text (first (bp/ghost-plan (bp/mark-failed {:blueprint-text TXT :blueprints [BL]} :lamp)))))))

(deftest G7-trial-unchanged
  (is (= "proposed: a lamp post\ntrial: try it out"
         (:text (first (bp/built-plan (bp/approve-blueprint {:blueprint-text TXT :blueprints [BL]} :lamp))))))
  (is (= "Proposed: a bench\ntrial: try it out"
         (:text (first (bp/built-plan (bp/approve-blueprint {:blueprint-text TXT :blueprints [BB]} :bench)))))))

(deftest G8-removing-unchanged
  (is (= "proposed: a lamp post\nwill be removed"
         (:text (first (filter #(= [:blueprint :lamp] (:key %))
                                (bp/built-plan (bp/start-removal WB :lamp))))))))

(deftest G9-a-real-build-still-has-no-sign-in-the-plan
  (let [plan (bp/built-plan WB)
        lamp (first (filter #(= [:blueprint :lamp] (:key %)) plan))
        bench (first (filter #(= [:blueprint :bench] (:key %)) plan))]
    (is (nil? (:label lamp)))
    (is (nil? (:text lamp)))
    (is (nil? (:label bench)))
    (is (nil? (:text bench)))))

(deftest G10-shipped-blueprints-made-real
  (let [WR (bp/apply-real-builds WE {:real [:bench-by-mailbox :lamp-post-by-canal]})]
    (is (= "a bench by the mailbox" (bp/sign-text WR (bp-of WR :bench-by-mailbox))))
    (is (= "a lamp post by the canal" (bp/sign-text WR (bp-of WR :lamp-post-by-canal))))))
