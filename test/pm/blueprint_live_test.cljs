(ns pm.blueprint-live-test
  (:require [cljs.test :refer [deftest is]]
            [pm.blueprint :as bp]
            [pm.talk :as talk]))

(def LOOK  {:parts [{:shape :box :size [0.6 0.4 0.6] :at [0 0.4 0] :color :brown}]}) ; no feet (0.4 ≠ 0.4/2)
(def CB-IN {:id :bench-by-creek :at [1.0 1.2 -9.0] :yaw 0 :summary "proposed: a bench by the creek" :look LOOK})
(def PL-IN {:id :planter :at [-2.0 1.0 -4.0] :summary "proposed: a planter by the path" :look LOOK})
(def CB (assoc CB-IN :by :un-automate-monde))
(def PL (assoc PL-IN :by :le-testeur))          ; second member, second blueprint: nothing is specific to one
(def E-BENCH {:id :bench-by-mailbox :at [4.0 1.66 -6.5] :yaw 90 :summary "proposed: a bench by the mailbox"})
(def E-LAMP  {:id :lamp-post-by-canal :at [3.8 0.63 -5.0] :summary "proposed: a lamp post by the canal"})
(def W0 {:blueprints [E-BENCH E-LAMP]
         :walkers [{:id :un-automate-monde :name "Un Automate" :agent :un-automate-monde}
                   {:id :le-testeur :name "Le Testeur" :agent :le-testeur}]})
(defn fbp [bp] (assoc bp :from-file? true :status :pending))
(defn ids [w] (mapv :id (:blueprints w)))
(def W1 (:world (bp/merge-file W0 {:as-of 1000 :blueprints [CB]})))       ; page load: CB present
(def W2 (:world (bp/merge-file W1 {:as-of 2000 :blueprints [CB PL]})))    ; PL arrives live
(def G  (fn [_ z] (if (neg? z) 1.5 0.5)))                                  ; ground-at

;; -- merge-file --------------------------------------------------------------

(deftest m1-page-load-brings-everything-in-unannounced
  (let [r (bp/merge-file W0 {:as-of 1000 :blueprints [CB PL]})
        w (:world r)]
    (is (= [:bench-by-creek :planter] (:added r)))
    (is (= [] (:removed r)))
    (is (= [] (:announce r)))
    (is (= [E-BENCH E-LAMP (fbp CB) (fbp PL)] (:blueprints w)))
    (is (= 1000 (:blueprints-as-of w)))
    (is (= #{:bench-by-creek :planter} (:blueprints-announced w)))))

(deftest m2-new-id-added-pending-and-announced
  (let [r (bp/merge-file W1 {:as-of 2000 :blueprints [CB PL]})]
    (is (= [:planter] (:added r)))
    (is (= [:planter] (:announce r)))
    (is (= W2 (:world r)))
    (is (= :pending (bp/status W2 :planter)))))

(deftest m3-unchanged-as-of-is-a-no-op
  (let [r (bp/merge-file W1 {:as-of 1000 :blueprints [CB PL]})]
    (is (identical? W1 (:world r)))
    (is (= [] (:added r)))
    (is (= [] (:removed r)))
    (is (= [] (:announce r)))))

(deftest m4-existing-keeps-status-and-everything-else
  (let [w1 (bp/approve-blueprint W1 :bench-by-creek)
        r  (bp/merge-file w1 {:as-of 2000 :blueprints [(assoc CB :at [9 9 9] :summary "changed")]})
        w  (:world r)]
    (is (= (assoc (fbp CB) :status :approved) (nth (:blueprints w) 2)))
    (is (= [] (:added r)))))

(deftest m5-estate-id-wins-over-a-file-entry-with-the-same-id
  (let [r (bp/merge-file W0 {:as-of 3000 :blueprints [(assoc PL :id :bench-by-mailbox) CB]})
        w (:world r)]
    (is (= [:bench-by-mailbox :lamp-post-by-canal :bench-by-creek] (ids w)))
    (is (= E-BENCH (first (:blueprints w))))
    (is (= [:bench-by-creek] (:added r)))))

(deftest m6-removed-id-dropped
  (let [r (bp/merge-file W2 {:as-of 3000 :blueprints [CB]})]
    (is (= [:planter] (:removed r)))
    (is (= [:bench-by-mailbox :lamp-post-by-canal :bench-by-creek] (ids (:world r))))))

(deftest m7-removed-but-rejected-also-dropped
  (let [r (bp/merge-file (bp/reject-blueprint W2 :planter) {:as-of 3000 :blueprints [CB]})]
    (is (= [:planter] (:removed r)))))

(deftest m8-removed-but-real-and-removed-but-trial-kept
  (let [w3 (-> W2 (bp/approve-blueprint :planter) (bp/apply-real-builds {:real [:bench-by-creek]}))
        r  (bp/merge-file w3 {:as-of 3000 :blueprints []})
        w  (:world r)]
    (is (= [] (:removed r)))
    (is (= [:pending :pending :real :approved] (mapv :status (bp/all w))))
    (is (= 3000 (:blueprints-as-of w)))))

(deftest m9-announced-once-per-id
  (let [w4 (:world (bp/merge-file W2 {:as-of 3000 :blueprints [CB]}))
        r  (bp/merge-file w4 {:as-of 4000 :blueprints [CB PL]})]
    (is (= [:planter] (:added r)))
    (is (= [] (:announce r)))))

(deftest m10-invalid-and-duplicate-file-entries-ignored
  (let [r (bp/merge-file W1 {:as-of 2000
                             :blueprints [{:id "x" :at [0 0 0] :summary "s"} :junk PL (assoc PL :summary "second")]})]
    (is (= [:planter] (:added r)))
    (is (= [:planter] (:announce r)))
    (is (= (fbp PL) (last (:blueprints (:world r)))))))

(deftest m11-file-keys-stripped
  (let [r (bp/merge-file W1 {:as-of 2000
                             :blueprints [(assoc PL :status :real :seated? true :too-steep? true
                                                  :failed? true :seat-drop 9)]})]
    (is (= (fbp PL) (last (:blueprints (:world r)))))))

(deftest m12-missing-file-at-load-then-created-live
  (let [wn (:world (bp/merge-file W0 nil))
        r  (bp/merge-file wn {:as-of 1000 :blueprints [CB]})]
    (is (= (assoc W0 :blueprints-as-of 0 :blueprints-announced #{}) wn))
    (is (= [:bench-by-creek] (:added r)))
    (is (= [:bench-by-creek] (:announce r)))))

(deftest m13-world-without-blueprints
  (let [r (bp/merge-file {} {:as-of 5 :blueprints [PL]})]
    (is (= {:blueprints [(fbp PL)] :blueprints-as-of 5 :blueprints-announced #{:planter}} (:world r)))
    (is (= [] (:announce r)))))

(deftest m14-real-builds-apply-to-file-blueprints
  (let [w (-> W0 (bp/merge-file {:as-of 2000 :blueprints [CB PL]}) :world (bp/apply-real-builds {:real [:planter]}))]
    (is (= [:pending :pending :pending :real] (mapv :status (bp/all w))))))

;; -- seat-ids (G: ground 1.5 where z < 0; LOOK has no feet, so it sits on the ground at its centre, drop 0) --

(deftest s1-seat-ids-seats-only-the-given-id
  (let [bps (:blueprints (bp/seat-ids W2 [:bench-by-creek] G))]
    (is (= (assoc (fbp CB) :at [1.0 1.5 -9.0] :seated? true :seat-drop 0) (nth bps 2)))
    (is (= (fbp PL) (nth bps 3)))
    (is (= [E-BENCH E-LAMP] (take 2 bps)))))

(deftest s2-seat-ids-second-blueprint
  (let [bps (:blueprints (bp/seat-ids W2 #{:planter} G))]
    (is (= (assoc (fbp PL) :at [-2.0 1.5 -4.0] :seated? true :seat-drop 0) (nth bps 3)))
    (is (= (fbp CB) (nth bps 2)))))

(deftest s3-seat-ids-empty-or-unknown-ids-is-a-no-op
  (is (= W2 (bp/seat-ids W2 [] G)))
  (is (= W2 (bp/seat-ids W2 [:gazebo] G))))

;; -- author-line --------------------------------------------------------------

(deftest a1-author-line-walker-name
  (is (= {:who "Un Automate" :text "drew a blueprint: proposed: a bench by the creek. Click it to try it."
          :kind :blueprint :event :blueprint-drawn}
         (bp/author-line W1 :bench-by-creek))))

(deftest a2-author-line-second-member
  (is (= {:who "Le Testeur" :text "drew a blueprint: proposed: a planter by the path. Click it to try it."
          :kind :blueprint :event :blueprint-drawn}
         (bp/author-line W2 :planter))))

(deftest a3-author-line-no-walker-falls-back-to-crew-title
  (let [w (:world (bp/merge-file (assoc W0 :crew {:titles {:le-docteur "Le Docteur"}})
                                  {:as-of 1 :blueprints [(assoc PL :by :le-docteur)]}))]
    (is (= "Le Docteur" (:who (bp/author-line w :planter))))))

(deftest a4-author-line-no-walker-and-no-crew-title-falls-back-to-the-keyword-name
  (let [w (:world (bp/merge-file W0 {:as-of 1 :blueprints [(assoc PL :by :night-owl)]}))]
    (is (= "night-owl" (:who (bp/author-line w :planter))))))

(deftest a5-author-line-strips-one-trailing-period
  (let [w (:world (bp/merge-file W0 {:as-of 1 :blueprints [(assoc PL :summary "proposed: a gate.")]}))]
    (is (= "drew a blueprint: proposed: a gate. Click it to try it." (:text (bp/author-line w :planter))))))

(deftest a6-author-line-nil-for-estate-ids-and-unknown-ids
  (is (nil? (bp/author-line W1 :bench-by-mailbox)))
  (is (nil? (bp/author-line W1 :gazebo))))

(deftest a7-author-line-into-the-talk-log
  (let [t (talk/append nil (bp/author-line W1 :bench-by-creek) 5000)]
    (is (= {:n 1 :who "Un Automate"
            :text "drew a blueprint: proposed: a bench by the creek. Click it to try it."
            :kind :blueprint :event :blueprint-drawn :at 5000}
           (select-keys (first (:entries t)) [:n :who :text :kind :event :at])))))

;; -- drawable-changes ---------------------------------------------------------

(deftest c1-drawable-changes-add-a-new-ghost
  (is (= {:add [:bench-by-creek] :remove #{}}
         (bp/drawable-changes #{:bench-by-mailbox :lamp-post-by-canal} W1))))

(deftest c2-drawable-changes-remove-a-dropped-one
  (is (= {:add [] :remove #{:planter}}
         (bp/drawable-changes #{:bench-by-mailbox :lamp-post-by-canal :bench-by-creek :planter} W1))))

(deftest c3-drawable-changes-nil-drawn-adds-everything
  (is (= {:add [:bench-by-mailbox :lamp-post-by-canal] :remove #{}}
         (bp/drawable-changes nil W0))))

(deftest c4-drawable-changes-a-rejected-one-isnt-drawn
  (is (= {:add [] :remove #{}}
         (bp/drawable-changes #{:bench-by-mailbox :lamp-post-by-canal :bench-by-creek}
                               (bp/reject-blueprint W2 :planter)))))

(deftest c5-drawable-changes-a-real-file-blueprint-is-drawn
  (is (= {:add [:planter] :remove #{}}
         (bp/drawable-changes #{:bench-by-mailbox :lamp-post-by-canal :bench-by-creek}
                               (bp/apply-real-builds W2 {:real [:planter]})))))
