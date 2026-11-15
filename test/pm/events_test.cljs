(ns pm.events-test
  (:require [cljs.test :refer [deftest is testing]]
            [pm.events :as events]))

(def S {:agent "w-3" :session "a1b2c3" :machine "workshop-1"})

(def AGENT-EVENT
  {:type :agent-event :agent "w-3" :kind :question
   :text "Rocks: round or jagged?" :source S})

(def ANSWER
  {:type :answer :agent "w-3" :text "jagged" :source S})

(def CHORE-DONE
  {:event :chore-done :mirror :flora :chore :feed :via :sms :source S})

(def JACK-OUT
  {:event :jack-out :reason :appointment :who :household :text "Game at 6" :source S})

(deftest t01-valid-source
  (is (true? (events/valid-source? S)))
  (is (true? (events/valid-source? {:agent "w-3"})))
  (is (true? (events/valid-source? {:session "a1" :machine "m1"}))))

(deftest t02-source-refusals
  (testing "no source at all"
    (is (false? (events/valid-source? nil)))
    (is (false? (events/valid-source? {}))))
  (testing "unknown keys, blank or non-string values"
    (is (false? (events/valid-source? {:agent "w-3" :extra "x"})))
    (is (false? (events/valid-source? {:agent ""})))
    (is (false? (events/valid-source? {:agent 7})))))

(deftest t03-event-kind
  (is (= :agent-event (events/event-kind AGENT-EVENT)))
  (is (= :answer (events/event-kind ANSWER)))
  (is (= :chore-done (events/event-kind CHORE-DONE)))
  (is (= :jack-out (events/event-kind JACK-OUT)))
  (is (nil? (events/event-kind {:type :mystery})))
  (is (nil? (events/event-kind "not a map")))
  (is (nil? (events/event-kind nil))))

(deftest t04-the-four-events-are-valid
  (is (true? (events/valid-event? AGENT-EVENT)))
  (is (true? (events/valid-event? ANSWER)))
  (is (true? (events/valid-event? CHORE-DONE)))
  (is (true? (events/valid-event? JACK-OUT))))

(deftest t05-jack-out-text-is-optional
  (is (true? (events/valid-event? (dissoc JACK-OUT :text)))))

(deftest t06-sourceless-events-are-refused
  (is (false? (events/valid-event? (dissoc AGENT-EVENT :source))))
  (is (false? (events/valid-event? (dissoc ANSWER :source))))
  (is (false? (events/valid-event? (dissoc CHORE-DONE :source))))
  (is (false? (events/valid-event? (dissoc JACK-OUT :source))))
  (is (false? (events/valid-event? (assoc AGENT-EVENT :source {})))))

(deftest t07-malformed-events-are-refused
  (is (false? (events/valid-event? {})))
  (is (false? (events/valid-event? nil)))
  (is (false? (events/valid-event? "oops")))
  (is (false? (events/valid-event? (assoc AGENT-EVENT :kind :unknown-kind))))
  (is (false? (events/valid-event? (assoc AGENT-EVENT :agent "W-3"))))
  (is (false? (events/valid-event? (dissoc AGENT-EVENT :text))))
  (is (false? (events/valid-event? (assoc AGENT-EVENT :text ""))))
  (is (false? (events/valid-event? (assoc CHORE-DONE :mirror "flora"))))
  (is (false? (events/valid-event? (assoc CHORE-DONE :via "sms"))))
  (is (false? (events/valid-event? (assoc JACK-OUT :reason "appointment"))))
  (is (false? (events/valid-event? (assoc JACK-OUT :text "")))))

(deftest t08-parse
  (is (= [1 2 3] (events/parse "[1 2 3]")))
  (is (nil? (events/parse "{:a 1}")))
  (is (nil? (events/parse "not edn (")))
  (is (nil? (events/parse nil)))
  (is (nil? (events/parse 42))))

(deftest t09-valid-events-filters
  (is (= [AGENT-EVENT ANSWER]
         (events/valid-events [AGENT-EVENT "junk" nil (dissoc ANSWER :source) ANSWER]))))

(deftest t10-hud-segment
  (is (nil? (events/hud-segment [])))
  (is (nil? (events/hud-segment nil)))
  (is (= "events 2/3 ok"
         (events/hud-segment [AGENT-EVENT ANSWER (dissoc CHORE-DONE :source)]))))

(deftest t11-the-shipped-sample-has-exactly-one-refusal
  (let [text (str "[" (pr-str AGENT-EVENT) " " (pr-str ANSWER) " "
                  (pr-str CHORE-DONE) " " (pr-str JACK-OUT) " "
                  (pr-str (dissoc AGENT-EVENT :source)) "]")
        parsed (events/parse text)]
    (is (= 5 (count parsed)))
    (is (= 4 (count (events/valid-events parsed))))))

(deftest t12-event-json-round-trip
  (doseq [e [AGENT-EVENT ANSWER CHORE-DONE JACK-OUT (dissoc JACK-OUT :text)]]
    (let [j (events/event->json e)]
      (is (map? j) e)
      (is (every? string? (keys j)) e)
      (is (= e (events/json->event j)) e))))

(deftest t13-event-json-refusals
  (is (nil? (events/event->json (dissoc AGENT-EVENT :source))))
  (is (nil? (events/event->json {})))
  (is (nil? (events/event->json nil)))
  (is (nil? (events/event->json "oops")))
  (is (nil? (events/json->event "not a map")))
  (is (nil? (events/json->event nil)))
  (is (nil? (events/json->event {"type" "mystery"})))
  (is (false? (events/valid-event?
                (events/json->event {"type" "agent-event" "agent" "w-3" "kind" "question" "text" "hi"})))))
