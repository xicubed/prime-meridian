(ns pm.calls-test
  (:require [cljs.test :refer [deftest is]]
            [pm.talk :as talk]
            [pm.call :as call]
            [pm.crew :as crew]))

;; Fixtures (spec §5, copied verbatim)

(defn contact-of [e]          ; = pm.ui/entry-contact with the twin's agent :hal
  (cond (contains? talk/crew-kinds (:kind e)) (:member e)
        (= :hal (:kind e))                     :hal
        (keyword? (:mirror e))                 (:mirror e)
        :else nil))

(def T {:n 9 :seen 0 :open? false
        :entries [{:n 1 :who "Hal" :text "Morning." :kind :hal}
                  {:n 2 :who "Flora" :text "I'm hungry" :kind :creature :mirror :flora}
                  {:n 3 :who "Le Testeur" :text "wants to run ls" :kind :crew-prompt :member :le-testeur :state :pending}
                  {:n 4 :who "You" :text "hi" :kind :crew-say :member :hal :to "Hal" :state :sent}
                  {:n 5 :who "Hal" :text "Do you read me?" :kind :hal}
                  {:n 6 :who "Juniper" :text "Hay, please?" :kind :creature :mirror :juniper}
                  {:n 7 :who "Hal" :text "allowed `ls` for Le Testeur" :kind :crew-log :member :le-testeur}
                  {:n 8 :who "Le Testeur" :text "done" :kind :crew-status :member :le-testeur}
                  {:n 9 :who "Flora" :text "Worms left: 2" :kind :creature :mirror :flora}]})

(def W {:voices  {:hal {:rate 1.0 :pitch 0.9 :lang "en-US"} :creature {:rate 1.1 :pitch 1.6 :lang "en-US"}}
        :walkers [{:agent :hal :name "Hal"}
                  {:agent :le-testeur :name "Le Testeur" :voice {:pitch 1.2}}
                  {:agent :le-docteur :name "Le Docteur" :voice {:rate 99 :lang ""}}]
        :mirrors {:flora   {:name "Flora" :kind :creature}
                  :juniper {:name "Juniper" :kind :creature :voice {:rate 0.9 :pitch 0.8}}}})

(def WQ {:asks-player {"le-testeur" {:text "Ship it?" :since 1}}})
(def ROWS [{:member :hal :who "Hal"} {:member :le-testeur :who "Le Testeur"} {:member :flora :who "Flora" :kind :creature}])
(def KEY-ROWS [{:id :hal :agent :hal :name "Hal"} {:id :juniper :name "Juniper" :kind :creature}
               {:id :phone-booth :name "Phone booth" :kind :place}])

;; Spoken lines: talk/call-lines, talk/call-line?

(deftest c01-call-lines-hal-since-0
  (is (= [1 5] (mapv :n (talk/call-lines T contact-of :hal 0)))))

(deftest c02-call-lines-hal-since-1
  (is (= [5] (mapv :n (talk/call-lines T contact-of :hal 1)))))

(deftest c03-call-lines-hal-since-5-and-9
  (is (= [] (talk/call-lines T contact-of :hal 5)))
  (is (= [] (talk/call-lines T contact-of :hal 9))))

(deftest c04-call-lines-flora-since-0
  (is (= [2 9] (mapv :n (talk/call-lines T contact-of :flora 0)))))

(deftest c05-call-lines-flora-since-2
  (is (= [9] (mapv :n (talk/call-lines T contact-of :flora 2)))))

(deftest c06-call-lines-juniper-second-mirror
  (is (= [6] (mapv :n (talk/call-lines T contact-of :juniper 0))))
  (is (= [] (talk/call-lines T contact-of :juniper 6))))

(deftest c07-call-lines-crew-member-quiet-entries
  (is (= [3] (mapv :n (talk/call-lines T contact-of :le-testeur 0)))))

(deftest c08-call-lines-nil-with-and-nil-talk
  (is (= [] (talk/call-lines T contact-of nil 0)))
  (is (= [] (talk/call-lines nil contact-of :hal 0))))

(deftest c09-call-lines-nil-since-treated-as-0
  (is (= [1 5] (mapv :n (talk/call-lines T contact-of :hal nil)))))

(deftest c10-call-lines-vector-of-whole-entries
  (is (vector? (talk/call-lines T contact-of :flora 0)))
  (is (= (get-in T [:entries 1]) (first (talk/call-lines T contact-of :flora 0)))))

(deftest c11-call-line?-true-for-hal-line
  (is (= true (talk/call-line? (get-in T [:entries 4]) contact-of :hal))))

(deftest c12-call-line?-false-for-players-own-line
  (is (= false (talk/call-line? (get-in T [:entries 3]) contact-of :hal))))

(deftest c13-call-line?-false-for-wrong-contact
  (is (= false (talk/call-line? (get-in T [:entries 1]) contact-of :hal))))

(deftest c14-call-line?-false-for-nil-with
  (is (= false (talk/call-line? (get-in T [:entries 4]) contact-of nil))))

;; Voice: talk/call-voice

(deftest c15-call-voice-hal
  (is (= {:rate 1.0 :pitch 0.9 :lang "en-US"}
         (talk/call-voice W :hal (get-in T [:entries 4])))))

(deftest c16-call-voice-crew-member-layered
  (is (= {:rate 1.0 :pitch 1.2 :lang "en-US"}
         (talk/call-voice W :le-testeur (get-in T [:entries 2])))))

(deftest c17-call-voice-invalid-fields-ignored
  (is (= {:rate 1.0 :pitch 0.9 :lang "en-US"}
         (talk/call-voice W :le-docteur {:n 20 :who "Le Docteur" :text "x" :kind :crew-prompt :member :le-docteur}))))

(deftest c18-call-voice-flora-no-voice
  (is (= {:rate 1.1 :pitch 1.6 :lang "en-US"}
         (talk/call-voice W :flora (get-in T [:entries 1])))))

(deftest c19-call-voice-juniper-with-voice
  (is (= {:rate 0.9 :pitch 0.8 :lang "en-US"}
         (talk/call-voice W :juniper (get-in T [:entries 5])))))

(deftest c20-call-voice-empty-world-default
  (is (= {:rate 1 :pitch 1 :lang "en-US"}
         (talk/call-voice {} :hal (get-in T [:entries 4])))))

(deftest c21-call-voice-no-such-walker
  (is (= {:rate 1.0 :pitch 0.9 :lang "en-US"}
         (talk/call-voice W :nobody {:n 21 :who "X" :text "y" :kind :hal}))))

;; Call state: start, end, switch replaces

(deftest c22-start-call-basic
  (is (= {:with :hal :since 1000} (:call (call/start-call {} :hal 1000)))))

(deftest c23-start-call-replaces-other-call
  (is (= {:with :le-testeur :since 2000}
         (:call (-> {} (call/start-call :hal 1000) (call/start-call :le-testeur 2000))))))

(deftest c24-start-call-replaces-both-mirrors
  (is (= {:with :juniper :since 3000}
         (:call (-> {} (call/start-call :flora 1000) (call/start-call :juniper 3000))))))

(deftest c25-start-call-same-contact-unchanged
  (let [w (call/start-call {} :hal 1000)]
    (is (identical? w (call/start-call w :hal 5000)))))

(deftest c26-end-call-dissocs
  (is (= false (contains? (-> {} (call/start-call :hal 1000) call/end-call) :call))))

(deftest c27-end-call-no-call-unchanged
  (let [w {:t 1}]
    (is (identical? w (call/end-call w)))))

(deftest c28-start-call-rejected-inputs
  (let [w {:t 1}]
    (is (= [true true true true true]
           [(identical? w (call/start-call w :all 1))
            (identical? w (call/start-call w "hal" 1))
            (identical? w (call/start-call w nil 1))
            (identical? w (call/start-call w :hal nil))
            (identical? w (call/start-call w :hal ##NaN))]))))

(deftest c29-call-with
  (is (= [nil :juniper]
         [(call/call-with {}) (call/call-with (call/start-call {} :juniper 7))])))

;; Ending: call/end-reason

(deftest c30-end-reason-nil-call
  (is (= nil (call/end-reason nil :hal true))))

(deftest c31-end-reason-ongoing
  (is (= nil (call/end-reason {:with :hal :since 1} :hal true))))

(deftest c32-end-reason-closed
  (is (= :closed (call/end-reason {:with :hal :since 1} :hal false))))

(deftest c33-end-reason-switched-to-contacts
  (is (= :switched (call/end-reason {:with :hal :since 1} nil true))))

(deftest c34-end-reason-switched-to-other-thread-or-all
  (is (= :switched (call/end-reason {:with :hal :since 1} :le-testeur true)))
  (is (= :switched (call/end-reason {:with :hal :since 1} :all true))))

(deftest c35-end-reason-closed-wins-over-switched
  (is (= :closed (call/end-reason {:with :hal :since 1} :le-testeur false))))

(deftest c36-end-reason-both-mirrors
  (is (= [nil :switched]
         [(call/end-reason {:with :flora :since 1} :flora true)
          (call/end-reason {:with :juniper :since 1} :flora true)])))

;; The 📞 button and where the 🎙 shows

(deftest c37-call-button-no-thread-or-all
  (is (= [nil nil nil]
         [(call/call-button nil nil) (call/call-button nil :all) (call/call-button {:with :hal :since 1} :all)])))

(deftest c38-call-button-off-both-twin-and-creature
  (is (= {:label "📞 Call" :on? false :title "Call: their lines are spoken as they arrive"}
         (call/call-button nil :hal)))
  (is (= {:label "📞 Call" :on? false :title "Call: their lines are spoken as they arrive"}
         (call/call-button nil :juniper))))

(deftest c39-call-button-on-for-this-thread
  (is (= {:label "End" :on? true :title "End the call"}
         (call/call-button {:with :hal :since 1} :hal))))

(deftest c40-call-button-off-for-other-thread-during-call
  (is (= {:label "📞 Call" :on? false :title "Call: their lines are spoken as they arrive"}
         (call/call-button {:with :hal :since 1} :flora))))

(deftest c41-mic-shown?
  (is (= [true false false false]
         [(call/mic-shown? {:with :hal} {:member :hal})
          (call/mic-shown? {:with :hal} {:member :le-testeur})
          (call/mic-shown? nil {:member :hal})
          (call/mic-shown? {:with :flora} {:member :flora :kind :creature})])))

;; The mic: call/mic-step, call/mic-line?, call/mic-view

(deftest c42-mic-step-start
  (is (= {:status :listening :member :hal :text ""} (call/mic-step nil [:start :hal])))
  (is (= {:status :listening :member :le-testeur :text ""} (call/mic-step {:status :idle} [:start :le-testeur]))))

(deftest c43-mic-step-interim-while-listening
  (is (= {:status :listening :member :hal :text "do you"}
         (call/mic-step {:status :listening :member :hal :text ""} [:interim "do you"]))))

(deftest c44-mic-step-interim-while-idle-unchanged
  (is (= {:status :idle} (call/mic-step {:status :idle} [:interim "x"]))))

(deftest c45-mic-step-final-end-stop
  (is (= {:status :idle} (call/mic-step {:status :listening :member :hal :text "do you"} [:final "Do you read me?"])))
  (is (= {:status :idle} (call/mic-step {:status :listening :member :hal :text "do you"} [:end])))
  (is (= {:status :idle} (call/mic-step {:status :listening :member :hal :text "do you"} [:stop]))))

(deftest c46-mic-step-error-refused
  (is (= {:status :off :why :refused}
         (call/mic-step {:status :listening :member :hal :text ""} [:error "not-allowed"])))
  (is (= {:status :off :why :refused}
         (call/mic-step {:status :idle} [:error "service-not-allowed"]))))

(deftest c47-mic-step-error-no-mic
  (is (= {:status :off :why :no-mic}
         (call/mic-step {:status :listening :member :hal :text ""} [:error "audio-capture"]))))

(deftest c48-mic-step-error-other-codes
  (is (= {:status :idle} (call/mic-step {:status :listening :member :hal :text ""} [:error "no-speech"])))
  (is (= {:status :idle} (call/mic-step {:status :listening :member :hal :text ""} [:error "aborted"])))
  (is (= {:status :idle} (call/mic-step {:status :listening :member :hal :text ""} [:error "network"])))
  (is (= {:status :idle} (call/mic-step {:status :listening :member :hal :text ""} [:error "start-failed"]))))

(deftest c49-mic-step-unsupported
  (is (= {:status :off :why :unsupported} (call/mic-step {:status :idle} [:unsupported]))))

(deftest c50-mic-step-off-sticks
  (let [O {:status :off :why :refused}]
    (is (= O (call/mic-step O [:start :hal])))
    (is (= O (call/mic-step O [:end])))
    (is (= O (call/mic-step O [:error "no-speech"])))
    (is (= O (call/mic-step O [:unsupported])))))

(deftest c51-mic-step-nil-normalised
  (is (= {:status :idle} (call/mic-step nil [:bogus])))
  (is (= {:status :idle} (call/mic-step nil [:end]))))

(deftest c52-mic-line?
  (is (= [true true false false]
         [(call/mic-line? nil {:status :off :why :refused})
          (call/mic-line? {:status :listening :member :hal :text ""} {:status :off :why :unsupported})
          (call/mic-line? {:status :off :why :refused} {:status :off :why :refused})
          (call/mic-line? {:status :listening} {:status :idle})])))

(deftest c53-mic-view-idle
  (is (= {:label "🎙 Talk" :disabled? false :title "Talk instead of typing"} (call/mic-view nil)))
  (is (= {:label "🎙 Talk" :disabled? false :title "Talk instead of typing"} (call/mic-view {:status :idle}))))

(deftest c54-mic-view-listening
  (is (= {:label "listening…" :disabled? true :title "Listening… it stops when you pause"}
         (call/mic-view {:status :listening :member :hal :text "do"}))))

(deftest c55-mic-view-off-refused
  (is (= {:label "🎙 Talk" :disabled? true :title "The browser refused the microphone; allow it for this page to talk"}
         (call/mic-view {:status :off :why :refused}))))

(deftest c56-mic-view-off-other-reasons
  (is (= {:label "🎙 Talk" :disabled? true :title "This browser can't hear you (no speech recognition); type instead"}
         (call/mic-view {:status :off :why :unsupported})))
  (is (= {:label "🎙 Talk" :disabled? true :title "No microphone found; type instead"}
         (call/mic-view {:status :off :why :no-mic})))
  (is (= {:label "🎙 Talk" :disabled? true :title "The mic is off; type instead"}
         (call/mic-view {:status :off}))))

(deftest c57-no-mic-line
  (is (= "No mic here. Type to me." call/no-mic-line)))

;; Recognised text: on-say's decision, call/say-route

(deftest c58-say-route-answers-pending-question
  (is (= {:route :answer :agent "le-testeur" :text "yes"}
         (call/say-route WQ :le-testeur "  yes  "))))

(deftest c59-say-route-say-to-hal
  (is (= {:route :say :member :hal :text "Do you read me?"}
         (call/say-route WQ :hal "Do you read me?"))))

(deftest c60-say-route-say-no-pending-question
  (is (= {:route :say :member :le-docteur :text "hello"}
         (call/say-route WQ :le-docteur " hello ")))
  (is (= {:route :say :member :hal :text "hi"}
         (call/say-route {} :hal " hi "))))

(deftest c61-say-route-rejected-inputs
  (is (= [nil nil nil nil]
         [(call/say-route WQ :le-testeur "   ")
          (call/say-route WQ :hal "")
          (call/say-route WQ :hal nil)
          (call/say-route WQ "hal" "hi")])))

;; Plate and rows, with and without a call

(deftest c62-plate-name-on-call
  (is (= "Hal · on a call"
         (crew/plate-name {:titles {}} {:agent :hal :name "Hal"} {:with :hal :since 1}))))

(deftest c63-plate-name-not-on-call
  (is (= "Hal" (crew/plate-name {:titles {}} {:agent :hal :name "Hal"} nil)))
  (is (= "Hal" (crew/plate-name {:titles {}} {:agent :hal :name "Hal"} {:with :le-testeur :since 1}))))

(deftest c64-plate-name-with-title
  (is (= "Le Docteur (docs) · on a call"
         (crew/plate-name {:titles {:le-docteur "Le Docteur (docs)"}} {:agent :le-docteur :name "Le Docteur"} {:with :le-docteur :since 1}))))

(deftest c65-plate-name-nil-agent-never-matches
  (is (= "X · on a call" (crew/plate-name nil {:agent :x :name "X"} {:with :x})))
  (is (= "X" (crew/plate-name nil {:name "X"} {:with nil}))))

(deftest c66-plate-name-2-arity-untouched
  (is (= "Hal" (crew/plate-name {:titles {}} {:agent :hal :name "Hal"}))))

(deftest c67-mark-on-call-hal
  (is (= [{:member :hal :who "Hal" :on-call? true}
          {:member :le-testeur :who "Le Testeur"}
          {:member :flora :who "Flora" :kind :creature}]
         (call/mark-on-call ROWS {:with :hal :since 1} :member))))

(deftest c68-mark-on-call-flora
  (is (= [{:member :hal :who "Hal"}
          {:member :le-testeur :who "Le Testeur"}
          {:member :flora :who "Flora" :kind :creature :on-call? true}]
         (call/mark-on-call ROWS {:with :flora :since 1} :member))))

(deftest c69-mark-on-call-nil-call
  (is (= ROWS (call/mark-on-call ROWS nil :member)))
  (is (vector? (call/mark-on-call (seq ROWS) nil :member))))

(deftest c70-mark-on-call-crew-key
  (is (= [nil true nil]
         (mapv :on-call? (call/mark-on-call KEY-ROWS {:with :juniper :since 1} #(or (:agent %) (:id %))))))
  (is (= [true nil nil]
         (mapv :on-call? (call/mark-on-call KEY-ROWS {:with :hal :since 1} #(or (:agent %) (:id %)))))))

(deftest c71-glyph-name
  (is (= ["Hal 📞" "Juniper 📞" "Hal" "Hal" "📞" ""]
         [(call/glyph-name "Hal" true)
          (call/glyph-name "Juniper" true)
          (call/glyph-name "Hal" false)
          (call/glyph-name "Hal" nil)
          (call/glyph-name nil true)
          (call/glyph-name nil false)])))
