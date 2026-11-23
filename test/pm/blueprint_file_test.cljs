(ns pm.blueprint-file-test
  (:require [cljs.test :refer [deftest is]]
            [pm.blueprint-file :as bf]
            [pm.blueprint :as bp]))

(def LOOK  {:parts [{:shape :box :size [0.6 0.4 0.6] :at [0 0.4 0] :color :brown}]}) ; no feet (0.4 ≠ 0.4/2)
(def CB-IN {:id :bench-by-creek :at [1.0 1.2 -9.0] :yaw 0 :summary "proposed: a bench by the creek" :look LOOK})
(def PL-IN {:id :planter :at [-2.0 1.0 -4.0] :summary "proposed: a planter by the path" :look LOOK})
(def CB (assoc CB-IN :by :un-automate-monde))
(def PL (assoc PL-IN :by :le-testeur))          ; second member, second blueprint: nothing is specific to one
(def E-BENCH {:id :bench-by-mailbox :at [4.0 1.66 -6.5] :yaw 90 :summary "proposed: a bench by the mailbox"})
(def E-LAMP  {:id :lamp-post-by-canal :at [3.8 0.63 -5.0] :summary "proposed: a lamp post by the canal"})
(def E #{:bench-by-mailbox :lamp-post-by-canal})
(def W0 {:blueprints [E-BENCH E-LAMP]
         :walkers [{:id :un-automate-monde :name "Un Automate" :agent :un-automate-monde}
                   {:id :le-testeur :name "Le Testeur" :agent :le-testeur}]})
(defn ids [w] (mapv :id (:blueprints w)))

(deftest p1-valid-blueprint-has-no-problems
  (is (= [] (bf/problems CB-IN)))
  (is (= [] (bf/problems CB)))
  (is (true? (bf/valid? CB-IN))))

(deftest p2-not-a-map
  (is (= ["not a map"] (bf/problems :junk)))
  (is (= ["not a map"] (bf/problems nil))))

(deftest p3-bad-id
  (is (= [":id must be a keyword"] (bf/problems (dissoc CB-IN :id))))
  (is (= [":id must be a keyword"] (bf/problems (assoc CB-IN :id "bench")))))

(deftest p4-bad-at
  (is (= [":at must be [x y z], three numbers"] (bf/problems (assoc CB-IN :at [1 2]))))
  (is (= [":at must be [x y z], three numbers"] (bf/problems (assoc CB-IN :at ["x" 2 3]))))
  (is (= [":at must be [x y z], three numbers"] (bf/problems (assoc CB-IN :at '(1 2 3))))))

(deftest p5-bad-summary
  (is (= [":summary must be a non-blank string"] (bf/problems (assoc CB-IN :summary "  "))))
  (is (= [":summary must be a non-blank string"] (bf/problems (dissoc CB-IN :summary)))))

(deftest p6-every-rule-at-once
  (is (= [":id must be a keyword" ":at must be [x y z], three numbers" ":summary must be a non-blank string"]
         (bf/problems {}))))

(deftest p7-one-validator-shared-with-pm-blueprint
  (is (= 1 (count (bp/all {:blueprints [CB-IN (dissoc CB-IN :id) (assoc CB-IN :summary "")]})))))

(deftest n1-normalize-nil
  (is (= {:as-of 0 :blueprints []} (bf/normalize nil))))

(deftest n2-normalize-drops-non-maps-and-other-keys
  (is (= {:as-of 7 :blueprints [CB PL]} (bf/normalize {:as-of 7 :blueprints (list CB :junk PL) :x 1}))))

(deftest n3-normalize-bad-as-of-and-blueprints
  (is (= {:as-of 0 :blueprints []} (bf/normalize {:as-of "7" :blueprints :x}))))

(deftest n4-normalize-not-a-map
  (is (= {:as-of 0 :blueprints []} (bf/normalize [1 2]))))

(deftest d1-add-into-nil-file
  (is (= {:ok {:as-of 1000 :blueprints [CB]} :id :bench-by-creek}
         (bf/add nil E :un-automate-monde CB-IN 1000))))

(deftest d2-add-second-member
  (is (= {:ok {:as-of 2000 :blueprints [CB PL]} :id :planter}
         (bf/add {:as-of 1000 :blueprints [CB]} E :le-testeur PL-IN 2000))))

(deftest d3-as-of-always-advances
  (let [r (bf/add {:as-of 5000 :blueprints [CB]} E :le-testeur PL-IN 4000)]
    (is (= 5001 (get-in r [:ok :as-of])))))

(deftest d4-add-sets-by-drops-status-and-seated
  (is (= {:ok {:as-of 10 :blueprints [(assoc CB-IN :by :le-testeur)]} :id :bench-by-creek}
         (bf/add nil E :le-testeur (assoc CB-IN :by :someone-else :status :real :seated? true) 10))))

(deftest d5-estate-id-refused
  (is (= {:refused "id :bench-by-mailbox is already in the estate"}
         (bf/add nil E :le-testeur (assoc PL-IN :id :bench-by-mailbox) 10))))

(deftest d6-duplicate-file-id-refused
  (is (= {:refused "id :bench-by-creek is already in state/blueprints.edn"}
         (bf/add {:as-of 1000 :blueprints [CB]} E :le-testeur CB-IN 2000))))

(deftest d7-invalid-blueprint-refused-with-reason
  (is (= {:refused "invalid blueprint: :summary must be a non-blank string"}
         (bf/add nil E :le-testeur (dissoc PL-IN :summary) 10))))

(deftest d8-invalid-blueprint-all-reasons-joined
  (is (= {:refused "invalid blueprint: :id must be a keyword; :at must be [x y z], three numbers; :summary must be a non-blank string"}
         (bf/add nil E :le-testeur {:look LOOK} 10))))

(deftest d9-invalid-blueprint-not-a-map
  (is (= {:refused "invalid blueprint: not a map"}
         (bf/add nil E :le-testeur [1 2] 10))))

(deftest d10-id-must-be-lowercase-letters-digits-dashes
  (is (= {:refused "id :Planter_1 must be lowercase letters, digits and dashes"}
         (bf/add nil E :le-testeur (assoc PL-IN :id :Planter_1) 10))))

(deftest d11-command-to-game
  (let [f (:ok (bf/add (:ok (bf/add nil E :un-automate-monde CB-IN 1000)) E :le-testeur PL-IN 2000))
        r (bp/merge-file W0 f)]
    (is (= [:bench-by-creek :planter] (:added r)))
    (is (= [] (:announce r)) "page load")
    (is (= [:bench-by-mailbox :lamp-post-by-canal :bench-by-creek :planter] (ids (:world r))))))

(deftest r1-remove-a-file-blueprint
  (is (= {:ok {:as-of 3000 :blueprints [CB]} :id :planter}
         (bf/remove-id {:as-of 2000 :blueprints [CB PL]} E :planter 3000))))

(deftest r2-estate-id-cant-be-removed
  (is (= {:refused "id :bench-by-mailbox is in the estate; only state/blueprints.edn entries can be removed"}
         (bf/remove-id {:as-of 2000 :blueprints [CB PL]} E :bench-by-mailbox 3000))))

(deftest r3-unknown-id-refused
  (is (= {:refused "no blueprint :gazebo in state/blueprints.edn"}
         (bf/remove-id {:as-of 2000 :blueprints [CB PL]} E :gazebo 3000))))

(deftest r4-remove-as-of-always-advances
  (is (= {:ok {:as-of 5001 :blueprints []} :id :bench-by-creek}
         (bf/remove-id {:as-of 5000 :blueprints [CB]} E :bench-by-creek 10))))

(deftest r5-remove-from-nil-file-refused
  (is (= {:refused "no blueprint :planter in state/blueprints.edn"}
         (bf/remove-id nil E :planter 10))))

(deftest u1-parse-add-args
  (is (= {:mode :add :member :un-automate-monde :path "/tmp/x.edn"}
         (bf/parse-args ["un-automate-monde" "/tmp/x.edn"])))
  (is (= {:mode :add :member :le-testeur :path "bench.edn"}
         (bf/parse-args ["le-testeur" "bench.edn"]))))

(deftest u2-parse-remove-args
  (is (= {:mode :remove :id :planter} (bf/parse-args ["--remove" "planter"]))))

(deftest u3-parse-remove-args-strips-one-leading-colon
  (is (= {:mode :remove :id :planter} (bf/parse-args ["--remove" ":planter"]))))

(deftest u4-parse-args-usage
  (doseq [args [nil [] ["le-testeur"] ["Le Testeur" "f.edn"] ["le-testeur" ""]
                ["--remove"] ["--remove" "Bad!"] ["--remove" "x" "y"] ["a" "b" "c"]]]
    (is (= {:usage bf/usage} (bf/parse-args args)) (str "args " (pr-str args)))))
