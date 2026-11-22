;; Deterministic tests for the pure parts of the scored content loop
;; (Assignment 5). Run: `bb content-test` (also part of .crew/gate.sh).
(ns pipeline-test
  (:require [clojure.string :as str]
            [clojure.test :refer [deftest is testing run-tests]]))

(load-file (str (.getParent (.getAbsoluteFile (java.io.File. *file*))) "/pipeline.clj"))
(alias 'p 'pipeline)

(defn scores [a b c d s]
  (into {} (map (fn [k v] [k {:score v :reason (str (name k) " reason") :violations []}])
                [:A :B :C :D :S] [a b c d s])))

(deftest overall-is-the-weighted-mean
  (testing "case 1: all tens is 10.0"
    (is (= 10.0 (p/overall (scores 10 10 10 10 10)))))
  (testing "case 2: hand-worked: A7 B8 C5 D8 S7 -> (10.5+8+10+8+10.5)/7 = 47/7 = 6.714 -> 6.7"
    (is (= 6.7 (p/overall (scores 7 8 5 8 7)))))
  (testing "case 3: C weighs double: a 4 in C pulls harder than a 4 in B"
    (is (< (p/overall (scores 10 10 4 10 10)) (p/overall (scores 10 4 10 10 10))))
    (is (= 8.3 (p/overall (scores 10 10 4 10 10))))
    (is (= 9.1 (p/overall (scores 10 4 10 10 10))))))

(deftest threshold-is-8-and-none-under-6
  (testing "case 4: 8.0 exactly with every criterion 8 passes"
    (is (p/passes? (scores 8 8 8 8 8))))
  (testing "case 5: a high overall fails when one criterion is under 6 (9 9 9 9 5 -> 8.1)"
    (is (= 8.1 (p/overall (scores 9 9 9 9 5))))
    (is (not (p/passes? (scores 9 9 9 9 5)))))
  (testing "case 6: all 7s is 7.0: under the bar though no criterion is under 6"
    (is (not (p/passes? (scores 7 7 7 7 7)))))
  (testing "case 7: 6 is the floor: 10 10 10 10 6 passes (9.1)"
    (is (p/passes? (scores 10 10 10 10 6)))))

(deftest trajectory-line-format
  (testing "case 8: draft 2: 6.7 (A 7 B 8 C 5 D 8 S 7)"
    (is (= "draft 2: 6.7 (A 7 B 8 C 5 D 8 S 7)" (p/trajectory-line 2 (scores 7 8 5 8 7))))))

(deftest parsing-a-scorer-reply
  (testing "case 9: a missing criterion counts as 0 with the reason \"not scored\""
    (let [ps (p/parse-scores {:A {:score 9 :reason "fine" :violations []}
                              :B {:score 8 :reason "fine" :violations []}
                              :C {:score 10 :reason "fine" :violations []}
                              :D {:score 9 :reason "fine" :violations []}})]
      (is (= 0 (get-in ps [:S :score])))
      (is (= "not scored" (get-in ps [:S :reason])))
      (is (= [] (get-in ps [:S :violations])))
      (is (not (p/passes? ps)))))
  (testing "case 10: a non-map reply scores 0 everywhere"
    (is (every? #(= 0 (:score %)) (vals (p/parse-scores "nonsense"))))
    (is (= 0.0 (p/overall (p/parse-scores nil)))))
  (testing "case 11: scores are clamped to 0-10 and rounded; non-map violations are dropped"
    (let [ps (p/parse-scores {:A {:score 12 :reason "r" :violations [{:where "x"} "junk"]}
                              :C {:score -3 :reason "r"} :D {:score 7.6 :reason "r"}})]
      (is (= 10 (get-in ps [:A :score])))
      (is (= 1 (count (get-in ps [:A :violations]))))
      (is (= 0 (get-in ps [:C :score])))
      (is (= 8 (get-in ps [:D :score])))))
  (testing "case 12: low-criteria lists what's under 6, lowest first"
    (is (= [:C :A] (p/low-criteria (scores 5 8 3 8 8))))))

(deftest refiner-prompt-names-every-violation
  (testing "case 13: every violation's where/issue/fix and every reason appear in the Refiner's prompt"
    (let [req {:title "T" :brief "B" :shape "S" :rules "R"}
          sc (-> (scores 9 9 4 9 8)
                 (assoc-in [:C :violations] [{:where ":weekly-card[1]" :rule 11 :issue "reads as a score" :fix "name the trend"}
                                             {:where ":coach-habit[0]" :rule 12 :issue "edges toward shame" :fix "use the player's words"}])
                 (assoc-in [:A :violations] [{:where ":jack-in[2]" :rule 4 :issue "quotes the film" :fix "say it plainly"}]))
          prompt (p/refiner-prompt req "CONTEXT" "STYLE" {:draft-text "{:jack-in [\"x\"]}" :scores sc :errors ["E1"]})]
      (doseq [v (p/all-violations sc) k [:where :issue :fix]]
        (is (str/includes? prompt (get v k)) (str (name k) " of " (:where v))))
      (is (str/includes? prompt "rule 11"))
      (is (str/includes? prompt "C reason"))
      (is (str/includes? prompt "E1"))
      (is (str/includes? prompt "STYLE"))
      (is (str/includes? prompt "overall 7.4"))))
  (testing "case 14: the Evaluator's prompt asks for a score and reason per criterion, never a verdict"
    (let [prompt (p/scorer-prompt {:title "T" :brief "B"} "CONTEXT" "STYLE" "DRAFT")]
      (doseq [k [:A :B :C :D :S]] (is (str/includes? prompt (str ":" (name k) " {:score"))))
      (is (str/includes? prompt "No :verdict")))))

(let [r (run-tests 'pipeline-test)]
  (System/exit (if (zero? (+ (:fail r) (:error r))) 0 1)))
