(ns pm.blueprint-test
  (:require [cljs.test :refer [deftest is testing]]
            [pm.blueprint :as bp]))

(def B1 {:id :bench :at [1 2 3] :summary "proposed: a bench"
         :look {:parts [{:shape :box :size [1 0.2 0.4] :color :brown}]}})

(def B2 {:id :light :at [0 0 0] :yaw 90 :status :approved
         :summary "proposed: a path light"})

(deftest t01-all-defaults-pending
  (is (= [:pending] (mapv :status (bp/all {:blueprints [B1]})))))

(deftest t02-all-keeps-a-given-status
  (is (= [:approved] (mapv :status (bp/all {:blueprints [B2]})))))

(deftest t03-all-drops-invalid-entries
  (testing "missing id, bad :at, blank/missing summary, not a map"
    (is (= [] (bp/all {:blueprints [(dissoc B1 :id)]})))
    (is (= [] (bp/all {:blueprints [(assoc B1 :at [1 2])]})))
    (is (= [] (bp/all {:blueprints [(assoc B1 :at ["x" 2 3])]})))
    (is (= [] (bp/all {:blueprints [(assoc B1 :summary "")]})))
    (is (= [] (bp/all {:blueprints [(dissoc B1 :summary)]})))
    (is (= [] (bp/all {:blueprints [:not-a-map]})))
    (is (= [] (bp/all {})))
    (is (= [] (bp/all {:blueprints nil})))))

(deftest t04-pending-filters-other-statuses
  (is (= [:bench] (mapv :id (bp/pending {:blueprints [B1 B2]}))))
  (is (= [] (bp/pending {:blueprints [B2]}))))

(deftest t05-approve-moves-it-out-of-pending
  (let [w (bp/approve-blueprint {:blueprints [B1]} :bench)]
    (is (= :approved (:status (first (:blueprints w)))))
    (is (= [] (bp/pending w)))))

(deftest t06-reject-moves-it-out-of-pending
  (let [w (bp/reject-blueprint {:blueprints [B1]} :bench)]
    (is (= :rejected (:status (first (:blueprints w)))))
    (is (= [] (bp/pending w)))))

(deftest t07-approve-unknown-id-is-a-no-op
  (is (= [B1] (:blueprints (bp/approve-blueprint {:blueprints [B1]} :nope)))))

(deftest t08-ghost-plan-shape
  (let [[item] (bp/ghost-plan {:blueprints [B1]})]
    (is (= [:blueprint :bench] (:key item)))
    (is (true? (:ghost? item)))
    (is (= [1 2 3] (:at item)))
    (is (= 0 (:yaw item)))
    (is (= :sign (:label item)))
    (is (= "proposed: a bench" (:text item)))
    (is (= 1 (count (:parts item))))))

(deftest t09-ghost-plan-only-pending
  (is (= [] (bp/ghost-plan {:blueprints [B2]}))))

(deftest t10-ghost-plan-defaults-look-and-yaw
  (let [[item] (bp/ghost-plan {:blueprints [{:id :x :at [0 0 0] :summary "s"}]})]
    (is (= 0 (:yaw item)))
    (is (seq (:parts item)))))

(deftest t11-ghost-plan-empty-world
  (is (= [] (bp/ghost-plan {}))))

(deftest t12-breaks-build-false-for-a-good-look
  (is (false? (bp/breaks-build? B1))))

(deftest t13-breaks-build-false-with-no-parts
  (is (false? (bp/breaks-build? B2))))

(deftest t14-breaks-build-false-for-empty-parts
  (is (false? (bp/breaks-build? {:look {:parts []}}))))

(deftest t15-breaks-build-true-for-an-unknown-shape
  (is (true? (bp/breaks-build? {:look {:parts [{:shape :pyramid :size [1 1 1]}]}}))))

(deftest t16-breaks-build-true-for-an-oversized-part
  (is (true? (bp/breaks-build? {:look {:parts [{:shape :box :size [1 999 1]}]}}))))

(deftest t17-breaks-build-true-when-any-part-is-bad
  (is (true? (bp/breaks-build?
               {:look {:parts [{:shape :box :size [1 1 1]}
                                {:shape :box :size [-1 1 1]}]}}))))

(deftest t18-breaks-build-false-for-non-map-look
  (is (false? (bp/breaks-build? {:id :x})))
  (is (false? (bp/breaks-build? {:look nil}))))

;; Hal's review changes (goal-agent run 4): the sign says how to act on a
;; ghost, and why a refused approval didn't take; the wording is data.
(def TXT {:hint "click to build" :failed "won't build"})

(deftest t20-sign-text-hint-and-failed
  (is (= "proposed: a bench · click to build"
         (:text (first (bp/ghost-plan {:blueprint-text TXT :blueprints [B1]})))))
  (is (= "proposed: a bench · won't build"
         (:text (first (bp/ghost-plan (bp/mark-failed {:blueprint-text TXT :blueprints [B1]} :bench))))))
  (testing "no wording in the data: just the summary"
    (is (= "proposed: a bench" (bp/sign-text {} B1)))))

(deftest t21-mark-failed-keeps-it-pending
  (let [w (bp/mark-failed {:blueprints [B1]} :bench)]
    (is (= [:bench] (mapv :id (bp/pending w))))
    (is (= w (bp/mark-failed w :nope)) "unknown id: no-op")))


(deftest t22-refuse-says-it-in-the-caption
  (let [w (bp/refuse {:blueprint-text TXT :blueprints [B1]} :bench 1000)]
    (is (= "won't build" (get-in w [:hal :text])))
    (is (= :blueprint-refused (get-in w [:hal :event])))
    (is (< 1000 (get-in w [:hal :until])))
    (is (= [:bench] (mapv :id (bp/pending w))) "still a ghost"))
  (is (nil? (:hal (bp/refuse {:blueprints [B1]} :bench 1000))) "no wording, no caption"))

(deftest t23-built-plan-trial-and-real
  (let [w {:blueprint-text {:trial "trial"}
           :blueprints [(assoc B1 :status :approved) (assoc B2 :id :x :status :pending)
                        (assoc B1 :id :y :status :real)]}
        [trial real & more] (bp/built-plan w)]
    (is (nil? more))
    (is (= [:blueprint :bench] (:key trial)))
    (is (nil? (:ghost? trial)))
    (is (= "proposed: a bench · trial" (:text trial)) "a trial says how to make it real")
    (is (nil? (:label real)) "a real build has no sign")))

(deftest t24-trial-real-and-immutable
  (let [w  {:blueprints [B1]}
        w1 (bp/approve-blueprint w :bench)]
    (is (= w (bp/make-real w :bench)) "a ghost can't skip straight to real")
    (is (= :real (bp/status (bp/make-real w1 :bench) :bench)))
    (is (= :rejected (bp/status (bp/reject-blueprint w1 :bench) :bench)) "a trial can be undone")
    (is (= :real (bp/status (bp/reject-blueprint (bp/make-real w1 :bench) :bench) :bench)) "a real one can't")
    (is (= :real (bp/status (bp/apply-real-builds w {:real [:bench :nope]}) :bench)) "the real-builds file wins")))
