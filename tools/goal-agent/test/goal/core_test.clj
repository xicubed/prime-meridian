(ns goal.core-test
  (:require [clojure.test :refer [deftest is testing]]
            [goal.core :as g]))

(def files {"src/pm/world.cljs" "(ns pm.world)\n(defn due? [x] x)\n(defn chore-done [w] w)\n"
            "test/pm/world_test.cljs" "(ns pm.world-test)\n(deftest due-test (is true))\n"})

(deftest scan-file-reads-ns-defs-and-tests
  (is (= {:path "a.cljs" :ns "pm.a" :defs ["x" "f" "g!"] :tests ["t"]}
         (g/scan-file "a.cljs" "(ns pm.a)\n(def x 1)\n(defn f [])\n(defn- ^:private g! [])\n(deftest t)\n"))))

(deftest status-from-probes
  (is (= :done (g/status files {:evidence [{:path "src/pm/" :re "defn due\\?"}]})))
  (is (= :partial (g/status files {:evidence [{:path "src/pm/" :re "defn due\\?"}
                                              {:path "src/pm/" :re "defn closing-time"}]})))
  (is (= :missing (g/status files {:evidence [{:path "src/pm/" :re "defn closing-time"}]})))
  (testing "a probe only looks under its path"
    (is (= :missing (g/status files {:evidence [{:path "test/" :re "defn due\\?"}]}))))
  (testing "no probes = missing, never done"
    (is (= :missing (g/status files {:evidence []})))))

(deftest urgency-rises-when-overdue
  (is (= 2.0 (g/urgency "2026-11-14" "2026-11-14")))
  (is (= 3.0 (g/urgency "2026-11-14" "2026-11-07")))
  (is (= 4.0 (g/urgency "2026-11-14" "2026-10-01")))      ; capped
  (is (= 1.0 (g/urgency "2026-11-14" "2026-11-21"))))

(def reqs [{:id :a :milestone "2026-11-09" :size 2 :demo? true}
           {:id :b :milestone "2026-11-09" :size 2 :demo? true :depends [:a]}
           {:id :c :milestone "2026-11-09" :size 2 :demo? false}
           {:id :d :milestone "2026-11-09" :size 1 :deferred? true}
           {:id :e :milestone "2026-11-09" :size 1 :depends [:b]}])

(def open-all {:a {:status :missing} :b {:status :missing} :c {:status :missing}
               :d {:status :missing} :e {:status :missing}})

(deftest dependents-are-transitive
  (is (= #{:b :e} (g/dependents reqs :a))))

(deftest priority-score-formula
  ;; today 2026-11-14 -> milestone 5 days overdue -> urgency 2 + 5/7
  (let [u (+ 2.0 (/ 5.0 7.0))
        r2 #(/ (Math/round (* % 100.0)) 100.0)]
    (testing "demo x (1 + 2 unblocked) / size"
      (is (= (r2 (/ (* u 2 3) 2)) (g/priority-score (first reqs) open-all reqs "2026-11-14"))))
    (testing "blocked by an open dependency: x0.25"
      (is (= (r2 (/ (* u 2 2 0.25) 2)) (g/priority-score (second reqs) open-all reqs "2026-11-14"))))
    (testing "partly built costs 0.6 of its size"
      (is (= (r2 (/ u (* 2 0.6))) (g/priority-score (nth reqs 2) (assoc-in open-all [:c :status] :partial) reqs "2026-11-14"))))
    (testing "deferred, done and parked score 0"
      (is (= 0.0 (g/priority-score (nth reqs 3) open-all reqs "2026-11-14")))
      (is (= 0.0 (g/priority-score (first reqs) (assoc-in open-all [:a :status] :done) reqs "2026-11-14")))
      (is (= 0.0 (g/priority-score (first reqs) (assoc-in open-all [:a :status] :parked) reqs "2026-11-14"))))))

(deftest pick-next-and-stop
  (is (= :a (get-in (g/pick-next reqs open-all "2026-11-14") [:req :id])))
  (let [done (into {} (for [r reqs] [(:id r) {:status :done}]))]
    (is (nil? (g/pick-next reqs done "2026-11-14")))
    (is (true? (g/all-met? reqs (assoc done :d {:status :missing}))) "deferred doesn't count")))

(deftest the-writer-stays-in-its-lane
  (is (= [] (g/outside-allowed ["src/pm/day.cljs" "test/pm/day_test.cljs" "resources/estate.edn" "scripts/events_bridge.clj"])))
  (is (= ["package.json" ".crew/gate.sh" "bb.edn"] (g/outside-allowed ["src/pm/a.cljs" "package.json" ".crew/gate.sh" "bb.edn"]))))

(deftest requirement-shape
  (is (g/valid-requirement? {:id :x :title "t" :milestone "2026-11-09" :evidence [{:path "src/" :re "a"}]}))
  (is (not (g/valid-requirement? {:id :x :title "t" :milestone "Nov 9" :evidence []}))))
