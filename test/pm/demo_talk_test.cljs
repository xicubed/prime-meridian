(ns pm.demo-talk-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            [pm.talk :as tk] [pm.crew :as crew] [pm.world :as w] [pm.jumeaux :as j]))

(def T0 1800000000000)
(def LINE "Ask {twin}, who runs the crew. In the full game I can answer you myself.")
(def HAL-LINE "Ask Hal, who runs the crew. In the full game I can answer you myself.")
(def ADA-LINE "Ask Ada, who runs the crew. In the full game I can answer you myself.")
(def WE (w/load-estate w/estate-edn))                 ; the real estate: twin "Hal", agent :hal
;; the second fixture: another player, another twin, and :hal is just a member here
(def WA {:jumeaux {:player {:name "Sam" :sex :f} :twin {:name "Ada"}}
         :member-talk {:demo LINE}
         :walkers [{:agent :ada :name "Ada"} {:agent :un-automate-monde :name "Un Automate"}
                   {:agent :hal :name "Hal"}]})
(def ok {:result :ok})
(defn e [t n] (tk/entry t n))
(def by-member :member)   ; contact-of for unread-by / call-line? (crew lines go to :member)

;; Estate and line

(deftest t01-estate-member-talk-block
  (is (= {:demo LINE} (:member-talk WE))))

(deftest t02-demo-member-line-estate
  (is (= HAL-LINE (tk/demo-member-line WE))))

(deftest t03-demo-member-line-second-fixture
  (is (= ADA-LINE (tk/demo-member-line WA))))

(deftest t04-demo-member-line-no-jumeaux
  (is (= "Ask Your twin, who runs the crew. In the full game I can answer you myself."
         (tk/demo-member-line {:member-talk {:demo LINE}}))))

(deftest t05-demo-member-line-edge-cases
  (is (= "Busy." (tk/demo-member-line {:member-talk {:demo "Busy."}})))
  (is (nil? (tk/demo-member-line {:member-talk {:demo "  "}})))
  (is (nil? (tk/demo-member-line {})))
  (is (nil? (tk/demo-member-line nil)))
  (is (nil? (tk/demo-member-line {:member-talk {:demo 7}}))))

;; The decision (say-reply)

(deftest t06-say-reply-not-demo
  (is (= {:post? true} (tk/say-reply false :hal :hal WE)))
  (is (= {:post? true} (tk/say-reply false :le-critique :hal WE)))
  (is (= {:post? true} (tk/say-reply nil :le-critique :hal WE))))

(deftest t07-say-reply-demo-to-twin
  (is (= {:post? false :outcome ok :reply :twin}
         (tk/say-reply true :hal :hal WE))))

(deftest t08-say-reply-demo-to-member
  (is (= {:post? false :outcome ok :reply :member :text HAL-LINE}
         (tk/say-reply true :le-critique :hal WE))))

(deftest t09-say-reply-second-fixture
  (is (= {:post? false :outcome ok :reply :twin}
         (tk/say-reply true :ada :ada WA)))
  (is (= {:post? false :outcome ok :reply :member :text ADA-LINE}
         (tk/say-reply true :un-automate-monde :ada WA)))
  (is (= {:post? false :outcome ok :reply :member :text ADA-LINE}
         (tk/say-reply true :hal :ada WA))))

(deftest t10-say-reply-demo-no-line-in-data
  (is (= {:post? false :outcome ok :reply nil}
         (tk/say-reply true :le-critique :hal {}))))

(deftest t11-say-reply-demo-no-twin-walker
  (is (= {:post? false :outcome ok :reply :member :text HAL-LINE}
         (tk/say-reply true :hal nil WE))))

(deftest t12-say-reply-not-a-keyword-member
  (is (nil? (tk/say-reply true "hal" :hal WE)))
  (is (nil? (tk/say-reply false nil :hal WE))))

;; The member's answer (crew-reply)

(deftest t13-crew-reply-basic
  (let [t (tk/crew-reply nil :le-critique "Le Critique" HAL-LINE T0)]
    (is (= 1 (:n t)))
    (is (= 0 (:queue-end t)))
    (is (= [{:n 1 :who "Le Critique" :text HAL-LINE :at T0 :kind :crew-reply :member :le-critique}]
           (:entries t)))))

(deftest t14-crew-reply-trims
  (is (= "Hi" (:text (e (tk/crew-reply nil :le-critique "Le Critique" "  Hi  " T0) 1)))))

(deftest t15-crew-reply-invalid-args-identical
  (let [t0 tk/empty-talk]
    (is (identical? t0 (tk/crew-reply t0 "le-critique" "Le Critique" HAL-LINE T0)))
    (is (identical? t0 (tk/crew-reply t0 :le-critique "" HAL-LINE T0)))
    (is (identical? t0 (tk/crew-reply t0 :le-critique "Le Critique" nil T0)))
    (is (identical? t0 (tk/crew-reply t0 :le-critique "Le Critique" "   " T0)))
    (is (identical? t0 (tk/crew-reply t0 :le-critique "Le Critique" HAL-LINE nil)))))

(deftest t16-crew-label-crew-reply
  (let [entry (e (tk/crew-reply nil :le-critique "Le Critique" HAL-LINE T0) 1)]
    (is (= "Le Critique · Ask Hal, who runs the crew. In the full game I can answer you myself."
           (tk/crew-label entry 0)))
    (is (= "Le Critique · Ask Hal, who runs the crew. In the full game I can answer you myself."
           (tk/crew-label entry 0 {:twin "Ada"})))))

(deftest t17-crew-reply-counts-and-kind
  (is (true? (tk/counts? {:kind :crew-reply :who "Le Critique"})))
  (is (true? (contains? tk/crew-kinds :crew-reply))))

;; Whole flows: the same calls on-say makes, in the same order

(deftest t18-flow-demo-to-twin
  (let [t1 (tk/crew-say nil :hal "Hal" "put a bench here" T0)]
    (is (= :sending (:state (e t1 1))))
    (let [p  (tk/say-reply true :hal :hal WE)
          t2 (tk/crew-result t1 1 (:outcome p) T0)]
      (is (= :sent (:state (e t2 1))))
      (is (false? (contains? (e t2 1) :error)))
      (is (= "You → Hal: put a bench here" (tk/crew-label (e t2 1) 0)))
      (is (not (str/includes? (tk/crew-label (e t2 1) 0) "not sent")))
      (is (= :twin (:reply p)))
      (is (= 1 (:n t2))))))

(deftest t19-flow-demo-to-member
  (let [t1 (tk/crew-say nil :le-critique "Le Critique" "can you help?" T0)
        p  (tk/say-reply true :le-critique :hal WE)
        t3 (-> t1
               (tk/crew-result 1 (:outcome p) T0)
               (tk/crew-reply :le-critique "Le Critique" (:text p) (+ T0 1)))]
    (is (= 2 (:n t3)))
    (is (= ["You → Le Critique: can you help?"
            "Le Critique · Ask Hal, who runs the crew. In the full game I can answer you myself."]
           (mapv #(tk/crew-label % 0) (:entries t3))))
    (is (= [{:n 1 :who "You" :text "can you help?" :at T0 :kind :crew-say
             :member :le-critique :to "Le Critique" :state :sent}
            {:n 2 :who "Le Critique" :text HAL-LINE :at (+ T0 1) :kind :crew-reply :member :le-critique}]
           (:entries t3)))
    (is (= :le-critique (:member (e t3 2))))))

(deftest t20-flow-demo-to-member-second-fixture
  (let [t1 (tk/crew-say nil :un-automate-monde "Un Automate" "hello" T0)
        p  (tk/say-reply true :un-automate-monde :ada WA)
        t3 (-> t1
               (tk/crew-result 1 (:outcome p) T0)
               (tk/crew-reply :un-automate-monde "Un Automate" (:text p) (+ T0 1)))]
    (is (= ["You → Un Automate: hello"
            "Un Automate · Ask Ada, who runs the crew. In the full game I can answer you myself."]
           (mapv #(tk/crew-label % 0) (:entries t3))))))

(deftest t21-unread-and-call-line
  (let [t1 (tk/crew-say nil :le-critique "Le Critique" "can you help?" T0)
        p  (tk/say-reply true :le-critique :hal WE)
        t3 (-> t1
               (tk/crew-result 1 (:outcome p) T0)
               (tk/crew-reply :le-critique "Le Critique" (:text p) (+ T0 1)))]
    (is (= 1 (tk/unread t3)))
    (is (= {:le-critique 1} (tk/unread-by t3 by-member)))
    (is (true? (tk/call-line? (e t3 2) by-member :le-critique)))
    (is (false? (tk/call-line? (e t3 1) by-member :le-critique)))))

(deftest t22-restore-roundtrip-and-drop
  (let [t1 (tk/crew-say nil :le-critique "Le Critique" "can you help?" T0)
        p  (tk/say-reply true :le-critique :hal WE)
        t3 (-> t1
               (tk/crew-result 1 (:outcome p) T0)
               (tk/crew-reply :le-critique "Le Critique" (:text p) (+ T0 1)))]
    (is (= (:entries t3) (:entries (tk/restore (tk/save-text t3)))))
    (is (= [] (:entries (tk/restore (pr-str {:v 1 :n 1
                                              :entries [{:n 1 :who "Le Critique" :text "x" :at T0
                                                         :kind :crew-reply}]})))))))

(deftest t23-not-demo-no-bridge-to-member
  (let [p  (tk/say-reply false :le-critique :hal WE)
        t4 (tk/crew-result (tk/crew-say nil :le-critique "Le Critique" "can you help?" T0)
                            1 (crew/outcome nil nil) T0)]
    (is (= {:post? true} p))
    (is (= :failed (:state (e t4 1))))
    (is (= "bridge off" (:error (e t4 1))))
    (is (= "You → Le Critique: can you help? · not sent: bridge off" (tk/crew-label (e t4 1) 0)))))

(deftest t24-not-demo-no-bridge-to-twin
  (let [t4 (tk/crew-result (tk/crew-say nil :hal "Hal" "put a bench here" T0)
                            1 (crew/outcome nil nil) T0)]
    (is (= :failed (:state (e t4 1))))
    (is (= "bridge off" (:error (e t4 1))))
    (is (= "You → Hal: put a bench here · not sent: bridge off" (tk/crew-label (e t4 1) 0)))))

(deftest t25-twin-thinking-demo
  (let [t1 (tk/crew-say nil :hal "Hal" "put a bench here" T0)
        p  (tk/say-reply true :hal :hal WE)
        t2 (tk/crew-result t1 1 (:outcome p) T0)]
    (is (true? (tk/twin-thinking? t2 {} :hal (+ T0 1000))))
    (is (false? (tk/twin-thinking? t2 {"hal" {:text "On it." :since (+ T0 1)}} :hal (+ T0 1000))))))

;; The demo roster (with-walkers 5-arity)

(deftest t26-with-walkers-open-arity
  (let [WW [{:agent :le-critique :name "Le Critique"} {:agent :hal :name "Hal"}
            {:agent :le-testeur :name "Le Testeur"}]]
    (is (= [[:le-critique true] [:le-testeur true]]
           (mapv (juxt :member :say?) (tk/with-walkers [] WW :hal #{} true))))
    (is (= [[:le-critique false] [:le-testeur false]]
           (mapv (juxt :member :say?) (tk/with-walkers [] WW :hal #{} false))))
    (is (= [true false] (mapv :say? (tk/with-walkers [] WW :hal #{:le-critique}))))))

(deftest t27-with-walkers-second-fixture
  (is (= [[:un-automate-monde "Un Automate" :unknown true] [:hal "Hal" :unknown true]]
         (mapv (juxt :member :who :status :say?) (tk/with-walkers [] (:walkers WA) :ada #{} true)))))
