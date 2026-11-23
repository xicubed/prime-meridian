(ns pm.call
  "A call with one contact (the twin, a crew member, or a creature): one key
   in the world atom (`:call`), plus the pure rules around it -- starting
   and ending it, which lines it speaks, the 📞 button, the 🎙 mic and its
   step function, and who answers typed/recognised text. pm.core wires
   these to the DOM and speech each frame; nothing here touches either."
  (:require [clojure.string :as str]
            [pm.world :as world]))

(def no-mic-line "No mic here. Type to me.")
(def call-suffix-glyph "📞")

(defn- finite-number? [n]
  (and (number? n) (js/isFinite n)))

(defn start-call
  "Starts a call with `member` at `now` (epoch ms): one call at a time, so a
   call with someone else is replaced. `world` unchanged (identical) when
   `member` isn't a keyword, is `:all`, `now` isn't a finite number, or a
   call with this same member is already on (its :since stays put)."
  [world member now]
  (if-not (and (keyword? member) (not= :all member) (finite-number? now))
    world
    (if (= member (get-in world [:call :with]))
      world
      (assoc world :call {:with member :since now}))))

(def spoken-tag "🎙 ")

(defn spoken
  "A line that came in through the mic, marked so the far end (the twin's chat,
   a member's pane) knows it was said, not typed: keep the answer short enough
   to speak. Idempotent; blank stays blank."
  [text]
  (let [t (str/trim (or text ""))]
    (cond (str/blank? t) t
          (str/starts-with? t spoken-tag) t
          :else (str spoken-tag t))))

(defn end-call
  "Ends whatever call is on. `world` unchanged (identical) with no :call."
  [world]
  (if (contains? world :call)
    (dissoc world :call)
    world))

(defn call-with [world]
  (:with (:call world)))

(defn end-reason
  "Why a call on `call` should end right now, given the phone's own
   `thread` and whether the 💭 panel is `open?`. nil when it should go on."
  [call thread open?]
  (cond
    (nil? call)                  nil
    (not open?)                  :closed
    (not= thread (:with call))   :switched
    :else                        nil))

(defn call-button
  "The 📞/End button's look for `thread`, or nil where there's no button at
   all (no thread, or :all)."
  [call thread]
  (cond
    (or (nil? thread) (= :all thread)) nil
    (= thread (:with call))
    {:label "End" :on? true :title "End the call"}
    :else
    {:label "📞 Call" :on? false :title "Call: their lines are spoken as they arrive"}))

(defn mic-shown?
  "True when the 🎙 belongs on `row`'s composer: a call is on with its
   member, and it isn't a creature (a creature's thread has no composer)."
  [call row]
  (boolean (and call (= (:with call) (:member row)) (not= :creature (:kind row)))))

(defn mic-step
  "The mic's next state for `event` (a vector, e.g. `[:start member]`).
   `:off` lasts until a page reload: nothing moves it but a reload."
  [mic event]
  (let [mic      (or mic {:status :idle})
        [tag a]  event]
    (if (= :off (:status mic))
      mic
      (case tag
        :start       {:status :listening :member a :text ""}
        :interim     (if (= :listening (:status mic)) (assoc mic :text a) mic)
        :final       {:status :idle}
        :end         {:status :idle}
        :stop        {:status :idle}
        :error       (cond
                       (contains? #{"not-allowed" "service-not-allowed"} a) {:status :off :why :refused}
                       (= "audio-capture" a)                                {:status :off :why :no-mic}
                       :else                                                {:status :idle})
        :unsupported {:status :off :why :unsupported}
        mic))))

(defn mic-line?
  "True the moment the mic goes :off: when the twin says no-mic-line, once
   per page."
  [old new]
  (boolean (and (= :off (:status new)) (not= :off (:status old)))))

(defn mic-view
  "The 🎙 button's look for `mic`."
  [mic]
  (let [mic (or mic {:status :idle})]
    (case (:status mic)
      :idle      {:label "🎙 Talk" :disabled? false :title "Talk instead of typing"}
      :listening {:label "listening…" :disabled? true :title "Listening… it stops when you pause"}
      :off       (case (:why mic)
                   :refused     {:label "🎙 Talk" :disabled? true :title "The browser refused the microphone; allow it for this page to talk"}
                   :unsupported {:label "🎙 Talk" :disabled? true :title "This browser can't hear you (no speech recognition); type instead"}
                   :no-mic      {:label "🎙 Talk" :disabled? true :title "No microphone found; type instead"}
                   {:label "🎙 Talk" :disabled? true :title "The mic is off; type instead"})
      {:label "🎙 Talk" :disabled? false :title "Talk instead of typing"})))

(defn say-route
  "on-say's decision for typed or recognised `text` to `member`: an answer
   to a pending ask_player question, a plain say, or nil for nothing to
   send."
  [world member text]
  (when (and (keyword? member) (string? text))
    (let [t (str/trim text)]
      (when-not (str/blank? t)
        (if (world/needs-you? world (name member))
          {:route :answer :agent (name member) :text t}
          {:route :say :member member :text t})))))

(defn mark-on-call
  "`rows` with the one whose `(key-fn row)` is `call`'s :with given
   :on-call? true; every other row returned as is. `(vec rows)` with no
   call."
  [rows call key-fn]
  (if (nil? call)
    (vec rows)
    (mapv (fn [row] (if (= (:with call) (key-fn row)) (assoc row :on-call? true) row)) rows)))

(defn glyph-name
  "A name with the 📞 glyph appended when `on-call?`."
  [nm on-call?]
  (str/trim (str (or nm "") (when on-call? (str " " call-suffix-glyph)))))
