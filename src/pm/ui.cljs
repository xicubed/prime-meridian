(ns pm.ui
  "DOM and speech side effects for talk captions. No game logic: pm.core
   decides what to show, pm.ui only shows it."
  (:require [clojure.string :as str]
            [pm.talk :as talk]))

(defn- el [id]
  (js/document.getElementById id))

(defn speech? []
  (boolean (and (.-speechSynthesis js/window) (.-SpeechSynthesisUtterance js/window))))

(defn speak!
  "Say `text`. `on-start`, when given, runs once the browser actually starts
   speaking (it won't before the player's first click or key on the page)."
  ([text voice] (speak! text voice nil))
  ([text voice on-start]
   (when (and (speech?) (string? text))
     (try
       (let [^js u (js/SpeechSynthesisUtterance. text)]
         (set! (.-rate u) (:rate voice))
         (set! (.-pitch u) (:pitch voice))
         (set! (.-lang u) (:lang voice))
         (when on-start (set! (.-onstart u) (fn [_] (on-start))))
         ;; Chrome sometimes leaves the queue paused or stuck after a cancel: unstick it
         (when (.-paused js/speechSynthesis) (.resume js/speechSynthesis))
         (when (and (.-pending js/speechSynthesis) (not (.-speaking js/speechSynthesis)))
           (.cancel js/speechSynthesis))
         (.speak js/speechSynthesis u))
       (catch :default _)))))

(defn stop-speech! []
  (when (speech?)
    (try (.cancel js/speechSynthesis) (catch :default _))))

(defn show-caption! [entry]
  (when-let [^js who-el (el "caption-who")]
    (set! (.-textContent who-el) (:who entry)))
  (when-let [^js text-el (el "caption-text")]
    (set! (.-textContent text-el) (str " — " (:text entry))))
  (when-let [^js cap-el (el "caption")]
    (.add (.-classList cap-el) "show")))

(defn hide-caption! []
  (when-let [^js cap-el (el "caption")]
    (.remove (.-classList cap-el) "show")))

(defn- stay-late-el
  "The twin's one-time stay-late ask (GDD §2.3, §4.8): shown once closing
   time hits and hidden once answered. Created on first use, same
   self-contained pattern as the night veil, so it needs no
   public/index.html change."
  []
  (or (el "stay-late")
      (let [^js box (js/document.createElement "div")]
        (set! (.-id box) "stay-late")
        (set! (.. box -style -cssText)
              (str "position:fixed;left:50%;bottom:260px;transform:translateX(-50%);"
                   "z-index:230;padding:10px 16px;border-radius:10px;"
                   "background:rgba(20,24,32,.9);color:#f3efe2;"
                   "font:16px system-ui, sans-serif;display:none;"
                   "align-items:center;gap:10px;"))
        (let [^js q (js/document.createElement "span")
              ^js b (js/document.createElement "button")]
          (set! (.-id q) "stay-late-text")
          (set! (.-id b) "stay-late-button")
          (.appendChild box q)
          (.appendChild box b))
        (.appendChild js/document.body box)
        box)))

(defn wire-stay-late!
  "Calls `on-stay` once when the player clicks the ask's Stay button.
   Creates the prompt on first call if it doesn't exist yet."
  [on-stay]
  (stay-late-el)
  (when-let [^js btn (el "stay-late-button")]
    (.addEventListener btn "click"
      (fn [^js e] (.stopPropagation e) (on-stay)))))

(defn set-stay-late!
  "Shows or hides the ask, worded from `words` ({:ask :button}, the
   :stay-late-ask entry in estate.edn). Safe to call every frame: only text
   and the display style change, no listeners are touched."
  [asking? words]
  (when-let [^js box (stay-late-el)]
    (when-let [^js q (el "stay-late-text")] (set! (.-textContent q) (or (:ask words) "")))
    (when-let [^js b (el "stay-late-button")] (set! (.-textContent b) (or (:button words) "")))
    (set! (.. box -style -display) (if (and asking? (:button words)) "flex" "none"))))

(defn- white-room-el
  "The Construct's white room (GDD §2.7): a plain white screen above
   everything else, created on first use, same self-contained pattern as
   the night veil and the stay-late ask, so it needs no public/index.html
   change."
  []
  (or (el "construct-white-room")
      (let [^js box (js/document.createElement "div")]
        (set! (.-id box) "construct-white-room")
        (set! (.. box -style -cssText)
              (str "position:fixed;inset:0;z-index:500;background:#fff;"
                   "display:none;align-items:center;justify-content:center;"
                   "padding:10vh 10vw;box-sizing:border-box;text-align:center;"
                   "font:24px/1.5 system-ui, sans-serif;color:#1b1f2a;"))
        (.appendChild js/document.body box)
        box)))

(defn set-white-room!
  "Shows or hides the Construct's white room (GDD §2.7): blank and silent
   while `state` is {:phase :silent}, the twin's opening line once it's
   {:phase :twin :line ...}, gone once `state` is nil."
  [state]
  (let [^js box (white-room-el)
        waiting? (= :waiting (:phase state))]
    (set! (.-textContent box) (or (:line state) (:prompt state) ""))
    ;; the jack-in prompt reads as a quiet hint, the twin's line as speech
    (set! (.. box -style -color) (if waiting? "#9aa0a6" "#1b1f2a"))
    (set! (.. box -style -fontSize) (if waiting? "18px" "24px"))
    (set! (.. box -style -cursor) (if waiting? "pointer" "default"))
    (set! (.. box -style -display) (if state "flex" "none"))))

(defn wire-white-room!
  "Calls `on-click` when the player clicks the white room (the jack-in)."
  [on-click]
  (.addEventListener (white-room-el) "click" (fn [^js e] (.stopPropagation e) (on-click))))

(defn- program-rack-el
  "The Construct's program rack, beat 2 (GDD §2.6, §2.7): the rack of
   programs scrolling past, then your own agent's loaded line. Shares the
   white room's look, created on first use, same self-contained pattern,
   so it needs no public/index.html change."
  []
  (or (el "construct-program-rack")
      (let [^js box (js/document.createElement "div")]
        (set! (.-id box) "construct-program-rack")
        (set! (.. box -style -cssText)
              (str "position:fixed;inset:0;z-index:500;background:#fff;"
                   "display:none;align-items:center;justify-content:center;"
                   "padding:10vh 10vw;box-sizing:border-box;text-align:center;"
                   "font:24px/1.5 system-ui, sans-serif;color:#1b1f2a;"))
        (.appendChild js/document.body box)
        box)))

(defn set-program-rack!
  "Shows or hides the program rack (GDD §2.6, §2.7): the rack's names
   scrolling past while `state` is {:phase :rack :programs [...]}, the
   twin's loaded line once it's {:phase :loaded :line ...}, gone once
   `state` is nil."
  [state]
  (let [^js box (program-rack-el)
        phase   (name (or (:phase state) :none))]
    ;; rebuilt only when the phase changes: this runs every frame, and
    ;; rebuilding would restart the scroll
    (when (not= phase (.. box -dataset -phase))
      (set! (.. box -dataset -phase) phase)
      (set! (.-innerHTML box) "")
      (case (:phase state)
        :rack   (let [^js col (js/document.createElement "div")]
                  ;; the rack scrolls up past you, names big, your own agent last on screen
                  ;; so it lands as the rack stops
                  (set! (.. col -style -cssText)
                        (str "display:flex;flex-direction:column;gap:18px;font-size:34px;"
                             "animation:pm-rack-scroll 2.2s cubic-bezier(.2,.7,.3,1) forwards;"))
                  (doseq [p (reverse (:programs state))]
                    (let [^js d (js/document.createElement "div")]
                      (set! (.-textContent d) (:name p))
                      (.appendChild col d)))
                  (when-not (el "pm-rack-style")
                    (let [^js st (js/document.createElement "style")]
                      (set! (.-id st) "pm-rack-style")
                      (set! (.-textContent st) "@keyframes pm-rack-scroll{from{transform:translateY(60vh);opacity:.3}to{transform:translateY(-38%);opacity:1}}")
                      (.appendChild (.-head js/document) st)))
                  (set! (.. box -style -overflow) "hidden")
                  (.appendChild box col))
        :loaded (set! (.-textContent box) (:line state))
        nil))
    (set! (.. box -style -display) (if state "flex" "none"))))

(defn set-badge! [text alert?]
  (when-let [^js b (el "talk-badge")]
    (set! (.-textContent b) text))
  (when-let [^js btn (el "talk-toggle")]
    (if alert?
      (.add (.-classList btn) "alert")
      (.remove (.-classList btn) "alert"))))

(defn- focused-checkbox-n []
  (let [^js active (.-activeElement js/document)]
    (when (and active (= "INPUT" (.-tagName active)))
      (not-empty (.. active -dataset -n)))))

(defn- box-class [entry]
  (if (keyword? (:asks entry))
    "chore request"
    (str "chore" (when-not (:done? entry) " undone"))))

(defn- render-chore-row! [^js li entry offset-of label-of on-check]
  (set! (.-className li) (box-class entry))
  (let [^js time-span (js/document.createElement "span")
        ^js label-el  (js/document.createElement "label")
        ^js input     (js/document.createElement "input")
        ^js text-span (js/document.createElement "span")]
    (set! (.-className time-span) "time")
    (set! (.-textContent time-span) (talk/clock-text (:at entry) (offset-of (:at entry))))
    (set! (.-type input) "checkbox")
    (.setAttribute input "data-n" (str (:n entry)))
    (set! (.-checked input) (boolean (:done? entry)))
    (.addEventListener input "change" (fn [^js e] (on-check entry (.. e -target -checked))))
    (set! (.-className text-span) "text")
    (set! (.-textContent text-span) (label-of entry))
    (.appendChild label-el input)
    (.appendChild label-el text-span)
    (.appendChild li time-span)
    (.appendChild li label-el)))

(defn- render-say-row! [^js li entry offset-of on-pick]
  (let [^js time-span (js/document.createElement "span")
        ^js who-span  (js/document.createElement "span")
        ^js text-span (js/document.createElement "span")]
    (set! (.-className time-span) "time")
    (set! (.-textContent time-span) (talk/clock-text (:at entry) (offset-of (:at entry))))
    (set! (.-className who-span) "who")
    (set! (.-textContent who-span) (:who entry))
    (set! (.-className text-span) "text")
    (set! (.-textContent text-span) (:text entry))
    (.addEventListener li "click" (fn [_] (on-pick entry)))
    (.appendChild li time-span)
    (.appendChild li who-span)
    (.appendChild li text-span)))

(defn- render-crew-row! [^js li entry offset-of crew]
  (let [{:keys [bridge-on? on-allow on-deny jumeaux]} crew
        controls (talk/crew-controls entry bridge-on?)
        ^js time-span (js/document.createElement "span")
        ^js row       (js/document.createElement "div")
        ^js text-span (js/document.createElement "span")]
    (set! (.-className li) (str "crew" (when (= :crew-log (:kind entry)) " quiet")))
    (set! (.-className time-span) "time")
    (set! (.-textContent time-span) (talk/clock-text (:at entry) (offset-of (:at entry))))
    (set! (.-className row) "row")
    (when (and controls (:allow? controls))
      (let [^js cb (js/document.createElement "input")]
        (set! (.-type cb) "checkbox")
        (set! (.-disabled cb) (boolean (:disabled? controls)))
        (.addEventListener cb "change" (fn [^js e] (when (.. e -target -checked) (on-allow (:n entry)))))
        (.appendChild row cb)))
    (set! (.-className text-span) (str "text" (when (:risky (:prompt entry)) " risky")))
    (set! (.-textContent text-span) (talk/crew-label entry (offset-of (:at entry)) jumeaux))
    (.appendChild row text-span)
    (when controls
      (let [^js x   (js/document.createElement "button")
            ^js inp (js/document.createElement "input")]
        (set! (.-className x) "deny")
        (set! (.-textContent x) "✕")
        (set! (.-disabled x) (boolean (:disabled? controls)))
        (.addEventListener x "click" (fn [_] (on-deny (:n entry) nil)))
        (set! (.-type inp) "text")
        (.setAttribute inp "placeholder" "reply…")
        (.setAttribute inp "data-n" (str (:n entry)))
        (set! (.-disabled inp) (boolean (:disabled? controls)))
        (.addEventListener inp "keydown"
          (fn [^js e] (when (= "Enter" (.-key e))
                        (on-deny (:n entry) (.. e -target -value)))))
        (.appendChild row x)
        (.appendChild row inp)))
    (.appendChild li time-span)
    (.appendChild li row)
    (when (and controls (:hint controls))
      (let [^js hint (js/document.createElement "span")]
        (set! (.-className hint) "hint")
        (set! (.-textContent hint) (:hint controls))
        (.appendChild li hint)))))

(defn render-panel! [entries offset-of label-of on-pick on-check keep-scroll? crew gone?]
  (when-let [^js list-el (el "talk-list")]
    (let [^js panel      (el "talk-panel")
          focused-n      (focused-checkbox-n)
          focused-value  (let [^js active (.-activeElement js/document)]
                           (when (and active (= "INPUT" (.-tagName active)) (= "text" (.-type active)))
                             (not-empty (.-value active))))
          prev-scroll    (when panel (.-scrollTop panel))]
      (set! (.-innerHTML list-el) "")
      (doseq [entry entries]
        (let [^js li (js/document.createElement "li")]
          (cond
            (talk/box? entry) (render-chore-row! li entry offset-of label-of on-check)
            (contains? talk/crew-kinds (:kind entry)) (render-crew-row! li entry offset-of crew)
            :else (render-say-row! li entry offset-of on-pick))
          (when (and (fn? gone?) (gone? entry))
            (.add (.-classList li) "gone"))
          (.appendChild list-el li)))
      (when focused-n
        (when-let [^js refocus (.querySelector list-el (str "input[data-n=\"" focused-n "\"]"))]
          (when (and focused-value (= "text" (.-type refocus)))
            (set! (.-value refocus) focused-value))
          (.focus refocus)))
      (when panel
        (if keep-scroll?
          (set! (.-scrollTop panel) prev-scroll)
          (set! (.-scrollTop panel) (.-scrollHeight panel)))))))

(defn render-roster!
  "Fills #crew-roster with one row per member: its dot, the title, and a
   say… input enabled only when :say?. Enter calls (on-say member who
   value). Tapping the name calls (on-expand member); an expanded row lists
   its recent log entries underneath. A re-render keeps a typed value too."
  [rows on-say on-expand offset-of jum]
  (when-let [^js root (el "crew-roster")]
    (let [^js active    (.-activeElement js/document)
          focused-m     (when (and active (= "INPUT" (.-tagName active)))
                          (not-empty (.getAttribute active "data-member")))
          focused-value (when (and active (= "INPUT" (.-tagName active)) (= "text" (.-type active)))
                          (not-empty (.-value active)))]
      (set! (.-innerHTML root) "")
      (doseq [row rows]
        (let [^js r     (js/document.createElement "div")
              ^js glyph (js/document.createElement "span")
              ^js who   (js/document.createElement "span")
              ^js inp   (js/document.createElement "input")]
          (set! (.-className r) "row")
          (set! (.-textContent glyph) (cond (:question row) "!" (= :unknown (:status row)) "○" :else "●"))
          (when (:question row) (set! (.. glyph -style -color) "#f5a623"))
          (set! (.-className who) "who")
          (set! (.-textContent who) (:who row))
          (set! (.. who -style -cursor) "pointer")
          (.addEventListener who "click" (fn [_] (on-expand (:member row))))
          (set! (.-type inp) "text")
          (.setAttribute inp "placeholder" (if (:question row) "answer…" "say…"))
          (.setAttribute inp "data-member" (name (:member row)))
          (set! (.-disabled inp) (not (:say? row)))
          (.addEventListener inp "keydown"
            (fn [^js e] (when (= "Enter" (.-key e))
                          (on-say (:member row) (:who row) (.. e -target -value))
                          (set! (.-value (.-target e)) ""))))
          (.appendChild r glyph)
          (.appendChild r who)
          (.appendChild r inp)
          (.appendChild root r)
          (when-let [q (:question row)]
            (let [^js qd (js/document.createElement "div")]
              (set! (.-className qd) "roster-log")
              (set! (.. qd -style -opacity) "1")
              (set! (.-textContent qd) q)
              (.appendChild root qd)))
          (when (:expanded? row)
            (let [^js sub (js/document.createElement "div")]
              (set! (.-className sub) "roster-log")
              (doseq [x (:log row)]
                (let [^js p (js/document.createElement "div")]
                  (set! (.-textContent p) (talk/log-label x (offset-of (:at x)) jum))
                  (.appendChild sub p)))
              (.appendChild root sub)))))
      (when focused-m
        (when-let [^js refocus (.querySelector root (str "input[data-member=\"" focused-m "\"]"))]
          (when focused-value (set! (.-value refocus) focused-value))
          (.focus refocus))))))

(defn render-hold-switch!
  "Draws the 'I'll answer' switch from (talk/hold-switch ...)'s result."
  [sw]
  (when-let [^js cb (el "hold-toggle")]
    (set! (.-checked cb) (boolean (:checked? sw)))
    (set! (.-disabled cb) (boolean (:disabled? sw))))
  (when-let [^js hint (el "hold-hint")]
    (set! (.-textContent hint) (or (:hint sw) ""))))

(defn focus-say-input!
  "Focuses the roster's say… input for member (the speech bubble from
   clicking that agent's avatar). No-op if the row isn't rendered."
  [member]
  (when-let [^js root (el "crew-roster")]
    (when-let [^js inp (.querySelector root (str "input[data-member=\"" (name member) "\"]"))]
      (.focus inp))))

(defn set-panel-open! [open?]
  (when-let [^js panel (el "talk-panel")]
    (if open?
      (.removeAttribute panel "hidden")
      (.setAttribute panel "hidden" ""))))

(defn set-voice-button! [on?]
  (when-let [^js btn (el "voice-toggle")]
    (set! (.-textContent btn) (if on? "🔊 voice on" "🔇 voice off"))
    (if on?
      (.add (.-classList btn) "on")
      (.remove (.-classList btn) "on"))))

(defn hide-voice-button! []
  (when-let [^js btn (el "voice-toggle")]
    (set! (.. btn -style -display) "none")))

(defn show-brain-ui!
  "Fills #brain-age with one <option> per age (text = the age), selects
   `age`, un-hides #brain-ui, and wires the select/button by property (not
   addEventListener) so calling this again doesn't double-wire."
  [ages age on-age on-drop]
  (when-let [^js sel (el "brain-age")]
    (set! (.-innerHTML sel) "")
    (doseq [a ages]
      (let [^js opt (js/document.createElement "option")]
        (set! (.-value opt) (str a))
        (set! (.-textContent opt) (str a))
        (.appendChild sel opt)))
    (set! (.-value sel) (str age))
    (set! (.-onchange sel) (fn [^js e] (on-age (js/parseInt (.. e -target -value) 10)))))
  (when-let [^js btn (el "brain-drop")]
    (set! (.-onclick btn) (fn [_] (on-drop))))
  (when-let [^js box (el "brain-ui")]
    (.removeAttribute box "hidden")))

(defn render-sources!
  "The SAMPLE/REAL switch in the ⓘ panel (GDD §4.6): one button per mirror
   from `rows` (pm.overlay/source-rows); a click calls (on-toggle id). A
   mirror with no real data behind it stays SAMPLE and says so."
  [rows on-toggle]
  (when-let [^js admin (el "admin")]
    (let [^js box (or (el "sources")
                      (let [^js d (js/document.createElement "div")]
                        (set! (.-id d) "sources")
                        (set! (.. d -style -cssText) "padding:6px 12px;font:14px system-ui,sans-serif;display:flex;gap:6px;flex-wrap:wrap;align-items:center")
                        (.appendChild admin d)
                        d))]
      (set! (.-innerHTML box) "")
      (doseq [{:keys [id name real? can-real?]} rows]
        (let [^js b (js/document.createElement "button")]
          (set! (.-textContent b) (str name ": " (if real? "REAL" "SAMPLE") (when-not can-real? " (no real data)")))
          (set! (.-disabled b) (not can-real?))
          (set! (.-title b) (if real? "Real: changes go to the twin's file through the bridge. Click for SAMPLE."
                                "Sample: changes stay in this browser. Click for REAL."))
          (.addEventListener b "click" (fn [^js e] (.stopPropagation e) (on-toggle id)))
          (.appendChild box b))))))

