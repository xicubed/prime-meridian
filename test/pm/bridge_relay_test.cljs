(ns pm.bridge-relay-test
  (:require [cljs.test :refer [deftest is]]
            [pm.bridge :as b]))

(def Q {:type :agent-event :agent "le-critique" :kind :question :text "Rocks: round or jagged?"})
(def A {:type :answer :agent "le-critique" :text "jagged"})
(def M #{:le-critique})

(deftest t01-a-question-is-relayed-and-remembered
  (let [r (b/relay {} Q M :cli #{:cli :game})]
    (is (:broadcast? r))
    (is (= {"le-critique" {:text "Rocks: round or jagged?" :sample? false :from :cli}} (:questions r)))
    (is (nil? (:deliver r)))))

(deftest t02-an-answer-reaches-the-member-with-its-question
  (let [r (b/relay (:questions (b/relay {} Q M :cli #{:cli :game})) A M :game #{:game})]
    (is (:broadcast? r))
    (is (= {} (:questions r)))
    (is (= {:member :le-critique :text "The player answered your question \"Rocks: round or jagged?\": jagged"}
           (:deliver r)))))

(deftest t03-a-sample-question-never-reaches-a-pane
  (is (nil? (:deliver (b/relay {"le-critique" {:text "x" :sample? true}} A M :game #{:game})))))

(deftest t04-not-a-crew-member-or-nothing-asked
  (is (nil? (:deliver (b/relay (:questions (b/relay {} Q M :cli #{:cli :game})) A #{} :game #{:game}))))
  (is (nil? (:deliver (b/relay {} A M :game #{:game})))))

(def S {:type :agent-event :agent "hal" :kind :say :text "On it."})

(deftest t06-a-say-is-relayed-but-never-remembered-as-a-question
  (let [r (b/relay {} S M :cli #{:cli :game})]
    (is (:broadcast? r))
    (is (= {} (:questions r)))
    (is (nil? (:deliver r)))))

(deftest t05-an-asker-still-waiting-gets-it-not-the-pane
  (is (nil? (:deliver (b/relay (:questions (b/relay {} Q M :cli #{:cli :game})) A M :game #{:cli :game})))))
