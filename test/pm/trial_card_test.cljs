(ns pm.trial-card-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            [pm.blueprint :as bp]
            [pm.pick :as pk]
            [pm.world :as w]))

(def TXT {:trial "trial: try it out" :make-real "Make it real" :undo "Undo" :saving "Saving…"})
(def B1  {:id :bench   :at [1 2 3] :summary "proposed: a bench"})
(def B2  {:id :planter :at [0 0 0] :summary "proposed: a planter"})   ; the second blueprint: nothing is specific to one
(def W   {:blueprint-text TXT :blueprints [B1 B2]})
(def ap  bp/approve-blueprint)
(def BTN (fn [real-label disabled?] [{:action :real :label real-label :disabled? disabled?}
                                     {:action :undo :label "Undo" :disabled? disabled?}]))

;; -- unapprove ----------------------------------------------------------------

(deftest u1-unapprove-sends-an-approved-trial-back-to-pending
  (let [w (-> W (ap :bench) (bp/unapprove :bench))]
    (is (= :pending (bp/status w :bench)))
    (is (= [:bench :planter] (mapv :id (bp/pending w))))))

(deftest u2-unapprove-pending-is-a-no-op
  (is (identical? W (bp/unapprove W :bench))))

(deftest u3-unapprove-real-is-a-no-op
  (let [w (bp/apply-real-builds W {:real [:bench]})]
    (is (identical? w (bp/unapprove w :bench)))
    (is (= :real (bp/status w :bench)))))

(deftest u4-unapprove-rejected-is-a-no-op
  (let [w (bp/reject-blueprint W :bench)]
    (is (identical? w (bp/unapprove w :bench)))
    (is (= :rejected (bp/status w :bench)))))

(deftest u5-unapprove-unknown-id-is-a-no-op
  (is (identical? W (bp/unapprove W :gazebo))))

(deftest u6-unapprove-second-blueprint-only-that-one-changes
  (let [w (-> W (ap :bench) (ap :planter) (bp/unapprove :planter))]
    (is (= [:approved :pending] (mapv :status (bp/all w))))))

(deftest u7-unapprove-then-try-again
  (let [w (-> W (ap :bench) (bp/unapprove :bench) (ap :bench))]
    (is (= :approved (bp/status w :bench)))
    (is (= :bench (bp/card-owner w)))))

(deftest u8-unapprove-signs
  (is (= "proposed: a bench\nclick to build"
         (:text (first (bp/ghost-plan (-> (assoc-in W [:blueprint-text :hint] "click to build")
                                           (ap :bench)
                                           (bp/unapprove :bench)))))))
  (is (= "proposed: a bench\ntrial: try it out"
         (:text (first (bp/built-plan (ap W :bench)))))))

;; -- :trial-order ---------------------------------------------------------------

(deftest t1-approve-sets-trial-order
  (is (= [:bench] (:trial-order (ap W :bench)))))

(deftest t2-trial-order-moves-a-re-approved-id-to-the-end
  (is (= [:planter :bench] (:trial-order (-> W (ap :bench) (ap :planter) (ap :bench))))))

(deftest t3-approve-unknown-id-is-a-no-op
  (let [r (ap W :gazebo)]
    (is (= W r))
    (is (not (contains? r :trial-order)))))

;; -- card-owner -----------------------------------------------------------------

(deftest o1-card-owner-nil-when-nothing-approved
  (is (nil? (bp/card-owner W))))

(deftest o2-card-owner-the-one-trial
  (is (= :bench (bp/card-owner (ap W :bench)))))

(deftest o3-card-owner-the-latest-trial
  (is (= :planter (bp/card-owner (-> W (ap :bench) (ap :planter))))))

(deftest o4-card-owner-skips-one-made-real
  (is (= :bench (bp/card-owner (-> W (ap :bench) (ap :planter) (bp/make-real :planter))))))

(deftest o5-card-owner-skips-one-undone
  (is (= :bench (bp/card-owner (-> W (ap :bench) (ap :planter) (bp/unapprove :planter))))))

(deftest o6-card-owner-skips-one-rejected
  (is (= :bench (bp/card-owner (-> W (ap :bench) (ap :planter) (bp/reject-blueprint :planter))))))

(deftest o7-card-owner-nil-when-the-only-trial-is-real
  (is (nil? (bp/card-owner (-> W (ap :bench) (bp/make-real :bench))))))

(deftest o8-card-owner-falls-back-without-trial-order
  (is (= :planter (bp/card-owner {:blueprints [(assoc B1 :status :approved) (assoc B2 :status :approved)]}))))

(deftest o9-card-owner-re-tried-moves-to-the-end
  (is (= :bench (bp/card-owner (-> W (ap :bench) (ap :planter) (bp/unapprove :bench) (ap :bench))))))

;; -- trial-card -----------------------------------------------------------------

(deftest c1-trial-card-nil-when-no-owner
  (is (nil? (bp/trial-card W nil))))

(deftest c2-trial-card-for-the-one-trial
  (is (= {:id :bench :buttons (BTN "Make it real" false)} (bp/trial-card (ap W :bench) nil))))

(deftest c3-trial-card-saving-this-one
  (is (= {:id :bench :buttons (BTN "Saving…" true)} (bp/trial-card (ap W :bench) :bench))))

(deftest c4-trial-card-saving-an-older-one-disables-this-cards-buttons-too
  (is (= {:id :planter :buttons (BTN "Make it real" true)}
         (bp/trial-card (-> W (ap :bench) (ap :planter)) :bench))))

(deftest c5-trial-card-nil-when-words-missing
  (is (nil? (bp/trial-card (ap (update W :blueprint-text dissoc :undo) :bench) nil)))
  (is (nil? (bp/trial-card (ap (assoc-in W [:blueprint-text :make-real] " ") :bench) nil)))
  (is (nil? (bp/trial-card (ap (update W :blueprint-text dissoc :saving) :bench) nil))))

(deftest c6-trial-card-second-blueprint
  (is (= {:id :planter :buttons (BTN "Make it real" false)} (bp/trial-card (ap W :planter) nil))))

;; -- screen-of (pk/screen-of) -----------------------------------------------------

(deftest p1-screen-of-centre
  (is (= {:x 400 :y 300 :visible? true} (pk/screen-of [0 0 0.5] [800 600]))))

(deftest p2-screen-of-off-centre
  (is (= {:x 600 :y 450 :visible? true} (pk/screen-of [0.5 -0.5 0.9] [800 600]))))

(deftest p3-screen-of-edges-are-on-screen
  (is (= {:x 0 :y 0 :visible? true} (pk/screen-of [-1 1 0] [1000 500])))
  (is (= {:x 1000 :y 500 :visible? true} (pk/screen-of [1 -1 1] [1000 500]))))

(deftest p4-screen-of-off-the-right-or-left
  (is (false? (:visible? (pk/screen-of [1.2 0 0.5] [800 600]))))
  (is (false? (:visible? (pk/screen-of [-1.01 0 0.5] [800 600])))))

(deftest p5-screen-of-off-the-top-or-bottom
  (is (false? (:visible? (pk/screen-of [0 1.5 0.5] [800 600]))))
  (is (false? (:visible? (pk/screen-of [0 -1.2 0.5] [800 600])))))

(deftest p6-screen-of-behind-the-camera
  (is (= {:x 400 :y 300 :visible? false} (pk/screen-of [0 0 1.2] [800 600]))))

(deftest p7-screen-of-bad-point
  (is (= {:x 0 :y 0 :visible? false} (pk/screen-of nil [800 600])))
  (is (= {:x 0 :y 0 :visible? false} (pk/screen-of [js/NaN 0 0] [800 600])))
  (is (= {:x 0 :y 0 :visible? false} (pk/screen-of [0 0] [800 600]))))

(deftest p8-screen-of-bad-size
  (is (= {:x 0 :y 0 :visible? false} (pk/screen-of [0 0 0] [0 600])))
  (is (= {:x 0 :y 0 :visible? false} (pk/screen-of [0 0 0] nil))))

;; -- the estate's words ----------------------------------------------------------

(deftest e1-estate-trial-button-words-are-non-blank-strings
  (let [t (:blueprint-text (w/load-estate w/estate-edn))]
    (doseq [k [:trial :make-real :undo :saving]]
      (let [v (get t k)]
        (is (string? v) (str k " is a string"))
        (is (not (str/blank? v)) (str k " is not blank"))))))

(deftest e2-estate-trial-button-words-exactly
  (let [t (:blueprint-text (w/load-estate w/estate-edn))]
    (is (= {:trial "trial: try it out" :make-real "Make it real" :undo "Undo" :saving "Saving…"}
           (select-keys t [:trial :make-real :undo :saving])))))

(deftest e3-the-shipped-estate-gets-a-card
  (is (= {:id :bench-by-mailbox :buttons (BTN "Make it real" false)}
         (bp/trial-card (ap (w/load-estate w/estate-edn) :bench-by-mailbox) nil)))
  (is (= 0.3 bp/card-lift-m)))
