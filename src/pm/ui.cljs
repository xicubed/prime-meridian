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
              ^js b (js/document.createElement "button")
              ^js l (js/document.createElement "button")]
          (set! (.-id q) "stay-late-text")
          (set! (.-id b) "stay-late-button")
          (set! (.-id l) "close-up-button")
          (.appendChild box q)
          (.appendChild box b)
          (.appendChild box l))
        (.appendChild js/document.body box)
        box)))

(defn wire-stay-late!
  "Calls `on-stay` or `on-close-up` when the player clicks the ask's Stay or
   Close up button. Creates the prompt on first call if it doesn't exist yet."
  [on-stay on-close-up]
  (stay-late-el)
  (doseq [[id f] [["stay-late-button" on-stay] ["close-up-button" on-close-up]]]
    (when-let [^js btn (el id)]
      (.addEventListener btn "click"
        (fn [^js e] (.stopPropagation e) (f))))))

(defn set-stay-late!
  "Shows or hides the ask, worded from `words` ({:ask :button}, the
   :stay-late-ask entry in estate.edn). Safe to call every frame: only text
   and the display style change, no listeners are touched."
  [asking? words]
  (when-let [^js box (stay-late-el)]
    (when-let [^js q (el "stay-late-text")] (set! (.-textContent q) (or (:ask words) "")))
    (when-let [^js b (el "stay-late-button")] (set! (.-textContent b) (or (:button words) "")))
    (when-let [^js b (el "close-up-button")] (set! (.-textContent b) (or (:leave words) "")))
    (set! (.. box -style -display) (if (and asking? (:button words)) "flex" "none"))))

(defonce ^:private !speaker (atom nil))

(defn set-speaker!
  "Who speaks the Construct's lines, shown above each one (e.g. the twin's name,
   then \"your twin\"), so a new player knows who's talking."
  [label]
  (reset! !speaker label))

(defn- construct-css!
  "The Construct's shared styles, added once: the speaker label, the line,
   the click hint as a button, the rock, the worker and the phone booth."
  []
  (when-not (el "pm-construct-style")
    (let [^js st (js/document.createElement "style")]
      (set! (.-id st) "pm-construct-style")
      (set! (.-textContent st)
            (str ".pm-who{font-size:14px;letter-spacing:.14em;text-transform:uppercase;color:#6b7280;margin-bottom:6px}"
                 ".pm-say{font-size:30px;line-height:1.4;max-width:900px;color:#1b1f2a}"
                 ".pm-row{display:flex;gap:14px;justify-content:center;align-items:center;margin-top:22px}"
                 ".pm-btn{display:inline-block;padding:12px 28px;border-radius:999px;border:0;"
                 "background:#1b1f2a;color:#fff;font:600 18px system-ui,sans-serif;cursor:pointer;animation:pm-pulse 1.6s ease-in-out infinite}"
                 ".pm-btn:hover{background:#4a7bd0}"
                 ".pm-voice{padding:10px 18px;border-radius:999px;border:2px solid #c7ccd4;background:#fff;"
                 "color:#1b1f2a;font:15px system-ui,sans-serif;cursor:pointer}"
                 "@keyframes pm-pulse{50%{box-shadow:0 0 0 8px rgba(27,31,42,.08)}}"
                 ".pm-stage{display:flex;align-items:flex-end;gap:28px;justify-content:center;height:150px;margin-bottom:30px}"
                 ".pm-rock{width:130px;height:96px;background:#8a8f99;transition:clip-path .9s ease}"
                 ".pm-worker{position:relative;width:34px;height:84px}"
                 ".pm-fig{display:flex;flex-direction:column;align-items:center;gap:8px}"
                 ".pm-arrow{font-size:44px;color:#4a7bd0;align-self:center;margin-bottom:22px}"
                 ".pm-cap{font-size:12px;letter-spacing:.12em;text-transform:uppercase;color:#6b7280}"
                 ".pm-ghost{background:#4a7bd0;position:relative;opacity:.9;animation:pm-ghost 1.4s ease-in-out infinite}"
                 ".pm-ghost-in{position:absolute;left:3px;top:3px;width:124px;height:90px;background:#eef3fb}"
                 "@keyframes pm-ghost{50%{opacity:.45}}"
                 ".pm-worker:before{content:'';position:absolute;left:5px;top:0;width:24px;height:24px;border-radius:50%;background:#4a7bd0}"
                 ".pm-worker:after{content:'';position:absolute;left:0;top:28px;width:34px;height:56px;border-radius:14px 14px 6px 6px;background:#4a7bd0}"
                 ".pm-bang{position:absolute;left:4px;top:-52px;font:700 40px system-ui;color:#e5a50a;animation:pm-bob 1s ease-in-out infinite}"
                 "@keyframes pm-bob{50%{transform:translateY(-6px)}}"
                 ".pm-tag{position:absolute;left:-14px;top:90px;font-size:11px;letter-spacing:.1em;color:#6b7280}"
                 ".pm-booth{width:60px;height:130px;background:#c0392b;border-radius:6px 6px 2px 2px;position:relative}"
                 ".pm-booth:before{content:'';position:absolute;left:10px;top:18px;width:40px;height:60px;background:#f6e7c8;border-radius:2px}"
                 ".pm-booth.ring{animation:pm-ring .18s linear infinite}"
                 ".pm-booth.out{opacity:.35}"
                 "@keyframes pm-ring{25%{transform:rotate(-3deg)}75%{transform:rotate(3deg)}}"))
      (.appendChild (.-head js/document) st))))

(defn- append-say!
  "One spoken line: who's speaking (default the twin), then the words."
  ([^js box text] (append-say! box text @!speaker))
  ([^js box text who]
   (construct-css!)
   (let [^js w (js/document.createElement "div")
         ^js p (js/document.createElement "div")]
     (when who
       (set! (.-className w) "pm-who")
       (set! (.-textContent w) who)
       (.appendChild box w))
     (set! (.-className p) "pm-say")
     (set! (.-textContent p) (or text ""))
     (.appendChild box p))))

(declare append-hint! button-click?)

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
        waiting? (= :waiting (:phase state))
        key      (str (:phase state) (:line state) (:voice state))]
    ;; rebuilt only when it changes: this runs every frame
    (when (not= key (.. box -dataset -key))
      (set! (.. box -dataset -key) key)
      (set! (.-innerHTML box) "")
      (set! (.. box -style -flexDirection) "column")
      (cond
        waiting?      (do (append-hint! box (:prompt state))
                          ;; the voice switch, right where you start: it's the same
                          ;; setting as the 🔊 button, remembered per browser
                          (when-let [label (:voice-label state)]
                            (let [^js row (.-lastChild box)
                                  ^js v   (js/document.createElement "button")]
                              (set! (.-className v) "pm-voice")
                              (set! (.-textContent v) label)
                              (.appendChild row v))))
        (:line state) (append-say! box (:line state))))
    (set! (.. box -style -display) (if state "flex" "none"))))

(defn wire-white-room!
  "Calls `on-click` when the player clicks Jack in, `on-voice` when they
   click the voice switch beside it."
  [on-click on-voice]
  (.addEventListener (white-room-el) "click"
    (fn [^js e]
      (.stopPropagation e)
      (cond
        (some-> (.-target e) (.closest ".pm-voice")) (on-voice)
        (button-click? e) (on-click)))))

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
        :loaded (do (set! (.. box -style -flexDirection) "column")
                    (append-say! box (:line state)))
        nil))
    (set! (.. box -style -display) (if state "flex" "none"))))

(defn- sparring-el
  "The Construct's sparring match, beat 3 (GDD §2.1, §2.7, §3.1, §4.5): the
   whole loop in miniature. Shares the earlier beats' look, created on
   first use, same self-contained pattern."
  []
  (or (el "construct-sparring")
      (let [^js box (js/document.createElement "div")]
        (set! (.-id box) "construct-sparring")
        (set! (.. box -style -cssText)
              (str "position:fixed;inset:0;z-index:500;background:#fff;"
                   "display:none;flex-direction:column;align-items:center;justify-content:center;"
                   "gap:16px;padding:10vh 10vw;box-sizing:border-box;text-align:center;"
                   "font:24px/1.5 system-ui, sans-serif;color:#1b1f2a;"))
        (.appendChild js/document.body box)
        box)))

(def ^:private jagged
  ;; a spiky rock: twelve points alternating long and short, the same twelve
  ;; the rounded one uses, so approving it morphs point by point
  "polygon(1.0% 52.0%,26.7% 39.4%,25.5% 12.2%,50.0% 26.7%,74.5% 12.2%,73.3% 39.4%,99.0% 52.0%,73.3% 64.6%,74.5% 91.8%,50.0% 77.3%,25.5% 91.8%,26.7% 64.6%)")

(def ^:private rounded
  ;; the same twelve points on an ellipse, so the change animates point by point
  (str "polygon("
       (->> (range 12)
            (map (fn [i] (let [a (+ Math/PI (* i (/ (* 2 Math/PI) 12)))]
                           (str (.toFixed (+ 50 (* 49 (Math/cos a))) 1) "% "
                                (.toFixed (+ 52 (* 46 (Math/sin a))) 1) "%"))))
            (interpose ",")
            (apply str))
       ")"))

(def ^:private oval "ellipse(49% 46% at 50% 52%)")

(defn- append-rock!
  "A plain CSS rock beside the SAMPLE worker, the done-when's \"the rock
   changes in front of you\": jagged before the blueprint's approved,
   rounding off once it is, visible without the 3D scene the white room
   sits above. `asking?` puts the worker's '!' over its head."
  [^js box rounder? asking? tag & [labels]]
  (construct-css!)
  (let [^js stage (js/document.createElement "div")
        ^js wk    (js/document.createElement "div")
        ^js r     (js/document.createElement "div")]
    (set! (.-className stage) "pm-stage")
    (set! (.-className wk) "pm-worker")
    (let [^js t (js/document.createElement "div")]
      (set! (.-className t) "pm-tag") (set! (.-textContent t) tag) (.appendChild wk t))
    (when asking?
      (let [^js b (js/document.createElement "div")]
        (set! (.-className b) "pm-bang") (set! (.-textContent b) "!") (.appendChild wk b)))
    (set! (.-className r) "pm-rock")
    (set! (.. r -style -clipPath) jagged)
    (.appendChild stage wk)
    (if-let [[now-label ghost-label] labels]
      ;; the blueprint: today's rock beside a ghost of the proposed one, so
      ;; you see what you're approving before you approve it
      (let [^js g  (js/document.createElement "div")
            ^js gi (js/document.createElement "div")
            fig    (fn [^js el label]
                     (let [^js f (js/document.createElement "div")
                           ^js c (js/document.createElement "div")]
                       (set! (.-className f) "pm-fig")
                       (.appendChild f el)
                       (set! (.-className c) "pm-cap")
                       (set! (.-textContent c) label)
                       (.appendChild f c)
                       f))]
        (set! (.-className g) "pm-rock pm-ghost")
        (set! (.. g -style -clipPath) oval)
        (set! (.-className gi) "pm-rock pm-ghost-in")
        (set! (.. gi -style -clipPath) oval)
        (.appendChild g gi)
        (.appendChild stage (fig r now-label))
        (let [^js arrow (js/document.createElement "div")]
          (set! (.-className arrow) "pm-arrow")
          (set! (.-textContent arrow) "→")
          (.appendChild stage arrow))
        (.appendChild stage (fig g ghost-label)))
      (.appendChild stage r))
    (.appendChild box stage)
    (when rounder?
      ;; next frame, so the jagged-to-round change animates in view
      (do
        ;; morph point by point, then settle into a true oval (a polygon
        ;; can't animate into an ellipse directly)
        (.addEventListener r "transitionend" (fn [_] (set! (.. r -style -clipPath) oval)) #js {:once true})
        (js/requestAnimationFrame (fn [] (js/requestAnimationFrame (fn [] (set! (.. r -style -clipPath) rounded)))))))))

(defn- append-booth!
  "The phone booth for the jump program (red, like the estate's), ringing
   (shaking) or dimmed while you're jacked out."
  [^js box mode]
  (construct-css!)
  (let [^js b (js/document.createElement "div")]
    (set! (.-className b) (str "pm-booth " (name mode)))
    (.appendChild box b)))

(defn- append-hint!
  "The one thing to do now, as a real button (only buttons take clicks in
   the Construct, so a stray click can't skip a step)."
  [^js box text]
  (construct-css!)
  (let [^js row (js/document.createElement "div")
        ^js b   (js/document.createElement "button")]
    (set! (.-className row) "pm-row")
    (set! (.-className b) "pm-btn")
    (set! (.-textContent b) (or text ""))
    (.appendChild row b)
    (.appendChild box row)))

(defn- button-click?
  "True when a click landed on one of the Construct's action buttons."
  [^js e]
  (some? (some-> (.-target e) (.closest ".pm-btn"))))

(defn set-sparring!
  "Shows or hides the sparring match (GDD §2.1, §2.7, §3.1, §4.5): the
   twin's job line while `state` is {:phase :job}, the sample-badged
   worker working the rock at {:phase :working}, its '!' question waiting
   on an answer at {:phase :ask}, the rounder rock's blueprint waiting on
   approval at {:phase :blueprint}, the rock changed at {:phase :done},
   gone once `state` is nil."
  [state]
  (let [^js box (sparring-el)
        phase   (name (or (:phase state) :none))]
    (when (not= phase (.. box -dataset -phase))
      (set! (.. box -dataset -phase) phase)
      (set! (.-innerHTML box) "")
      (case (:phase state)
        :job       (do (append-rock! box false false (:tag state))
                       (append-say! box (:line state)))
        :working   (do (append-rock! box false false (:tag state))
                       (append-say! box (:text state) (str (:worker state) " · " (:tag state))))
        :ask       (do (append-rock! box false true (:tag state))
                       (append-say! box (:prompt state) (str (:worker state) " · " (:tag state)))
                       (append-hint! box (:hint state)))
        :blueprint (do (append-rock! box false false (:tag state) (:rock-labels state))
                       (append-say! box (:summary state) (:blueprint-who state))
                       (append-hint! box (:hint state)))
        :done      (do (append-rock! box true false (:tag state))
                       (append-say! box (:line state)))
        nil))
    (set! (.. box -style -display) (if state "flex" "none"))))

(defn wire-sparring!
  "Calls `on-answer` when the player clicks during the worker's '!'
   ({:phase :ask}), `on-approve` when they click during the blueprint
   ({:phase :blueprint}); a no-op click any other time."
  [on-answer on-approve]
  (let [^js box (sparring-el)]
    (.addEventListener box "click"
      (fn [^js e]
        (.stopPropagation e)
        (when (button-click? e)
          (case (.. box -dataset -phase)
            "ask"       (on-answer)
            "blueprint" (on-approve)
            nil))))))

(defn- jump-program-el
  "The Construct's jump program, beat 4 (GDD §2.3, §2.7, §4.8): the first
   pretend jack-out. Shares the earlier beats' look, created on first use,
   same self-contained pattern."
  []
  (or (el "construct-jump")
      (let [^js box (js/document.createElement "div")]
        (set! (.-id box) "construct-jump")
        (set! (.. box -style -cssText)
              (str "position:fixed;inset:0;z-index:500;background:#fff;"
                   "display:none;flex-direction:column;align-items:center;justify-content:center;"
                   "gap:16px;padding:10vh 10vw;box-sizing:border-box;text-align:center;"
                   "font:24px/1.5 system-ui, sans-serif;color:#1b1f2a;"))
        (.appendChild js/document.body box)
        box)))

(defn set-jump-program!
  "Shows or hides the jump program (GDD §2.3, §2.7, §4.8): the twin's line
   showing the hardline while `state` is {:phase :show}, the phone ringing
   with the pretend chore at {:phase :ring} (waiting on the jack-out
   click), the estate held frozen at {:phase :out} (waiting on the
   jack-back-in click), the twin's line once you're back at {:phase
   :back}, gone once `state` is nil."
  [state]
  (let [^js box (jump-program-el)
        phase   (name (or (:phase state) :none))]
    (when (not= phase (.. box -dataset -phase))
      (set! (.. box -dataset -phase) phase)
      (set! (.-innerHTML box) "")
      (case (:phase state)
        :show (do (append-booth! box :still) (append-say! box (:line state)))
        :ring (do (append-booth! box :ring) (append-say! box (:prompt state))
                  (append-hint! box (:hint state)))
        :out  (do (append-booth! box :out) (append-say! box (:prompt state))
                  (append-hint! box (:hint state)))
        :back (do (append-booth! box :still) (append-say! box (:line state)))
        nil))
    (set! (.. box -style -display) (if state "flex" "none"))))

(defn wire-jump-program!
  "Calls `on-jump-out` when the player clicks during the ringing phone
   ({:phase :ring}), `on-jump-in` when they click while jacked out
   ({:phase :out}); a no-op click any other time."
  [on-jump-out on-jump-in]
  (let [^js box (jump-program-el)]
    (.addEventListener box "click"
      (fn [^js e]
        (.stopPropagation e)
        (when (button-click? e)
          (case (.. box -dataset -phase)
            "ring" (on-jump-out)
            "out"  (on-jump-in)
            nil))))))

(defn- free-mind-el
  "The Construct's fifth and final beat, free your mind (GDD §2.7, §4.8):
   the white room dissolving away. Shares the earlier beats' white-room
   look but fades its own opacity (rather than just toggling display), so
   the estate underneath -- already showing only the buildings for the
   programs loaded so far -- comes through as it dissolves. Created on
   first use, same self-contained pattern, so it needs no
   public/index.html change."
  []
  (or (el "construct-free")
      (let [^js box (js/document.createElement "div")]
        (set! (.-id box) "construct-free")
        (set! (.. box -style -cssText)
              (str "position:fixed;inset:0;z-index:500;background:#fff;"
                   "display:none;align-items:center;justify-content:center;"
                   "padding:10vh 10vw;box-sizing:border-box;text-align:center;"
                   "font:24px/1.5 system-ui, sans-serif;color:#1b1f2a;"
                   "transition:opacity 1.5s linear;"))
        (.appendChild js/document.body box)
        box)))

(defn set-free-your-mind!
  "Shows or hides the Construct's last beat (GDD §2.7, §4.8): opaque white
   at {:phase :dissolve}'s start, fading to transparent over that phase --
   the white room dissolving into the estate -- then the twin's step-back
   line, on a card over the now-clear estate, at {:phase :line :line ...},
   gone once `state` is nil."
  [state]
  (let [^js box (free-mind-el)
        phase (:phase state)
        key   (str phase (:line state))]
    (when (not= key (.. box -dataset -key))
      (set! (.. box -dataset -key) key)
      (set! (.-innerHTML box) "")
      (set! (.. box -style -flexDirection) "column")
      (case phase
        ;; start white, then fade out over the transition, next frame so it animates
        :dissolve (do (set! (.. box -style -transition) "none")
                      (set! (.. box -style -background) "#fff")
                      (set! (.. box -style -opacity) "1")
                      (js/requestAnimationFrame
                        (fn [] (js/requestAnimationFrame
                                 (fn [] (set! (.. box -style -transition) "opacity 1.5s linear")
                                        (set! (.. box -style -opacity) "0"))))))
        :line     (let [^js card (js/document.createElement "div")]
                    ;; over the estate now: a card so it reads on any background
                    (set! (.. box -style -transition) "none")
                    (set! (.. box -style -background) "transparent")
                    (set! (.. box -style -opacity) "1")
                    (set! (.. card -style -cssText)
                          "background:rgba(255,255,255,.94);border-radius:14px;padding:18px 28px;box-shadow:0 6px 24px rgba(0,0,0,.18)")
                    (append-say! card (:line state))
                    (.appendChild box card))
        nil))
    ;; the estate stays clickable under the card; only the white blocks clicks
    (set! (.. box -style -pointerEvents) (if (= :line phase) "none" "auto"))
    (set! (.. box -style -display) (if state "flex" "none"))))

(defn- card-el
  "A centred card over the estate (the Construct's look), created on first
   use: `id` names it; clicks on the backdrop close nothing by themselves."
  [id z backdrop]
  (or (el id)
      (let [^js box (js/document.createElement "div")]
        (set! (.-id box) id)
        (set! (.. box -style -cssText)
              (str "position:fixed;inset:0;z-index:" z ";display:none;align-items:center;justify-content:center;"
                   "background:" backdrop ";font:16px/1.45 system-ui,sans-serif;"))
        (.appendChild js/document.body box)
        box)))

(defn show-jack-out!
  "The jack-out (GDD §2.3): while the estate is frozen, it dims and a card
   says why, with a Jack back in button. `state` nil hides it;
   {:who :line :button} shows it. Rebuilt only when it changes."
  [state on-back]
  (let [^js box (card-el "jack-out-veil" 450 "rgba(12,14,22,.45)")
        key     (pr-str state)]
    (when (not= key (.. box -dataset -key))
      (set! (.. box -dataset -key) key)
      (set! (.-innerHTML box) "")
      (set! (.. box -style -backdropFilter) "grayscale(.7)")
      (when state
        (construct-css!)
        (let [^js card (js/document.createElement "div")]
          (set! (.. card -style -cssText) "background:#fff;border-radius:16px;padding:26px 34px;max-width:620px;text-align:center;box-shadow:0 10px 40px rgba(0,0,0,.3)")
          (append-say! card (:line state) (:who state))
          (when-let [n (:note state)]
            (let [^js p (js/document.createElement "div")]
              (set! (.. p -style -cssText) "margin-top:10px;font-size:15px;color:#6b7280")
              (set! (.-textContent p) n)
              (.appendChild card p)))
          (append-hint! card (:button state))
          (.addEventListener card "click" (fn [^js e] (.stopPropagation e) (when (button-click? e) (on-back))))
          (.appendChild box card))))
    (set! (.. box -style -display) (if state "flex" "none"))))

(defn show-mail!
  "The mailbox's inbox (GDD §2.4, §4.6): `rows` from pm.content/inbox-rows,
   newest first, SAMPLE-badged; click a row to read it, Close to go back.
   nil hides it."
  [{:keys [title rows close sample]}]
  (let [^js box (card-el "mail-panel" 440 "rgba(12,14,22,.25)")]
    (set! (.-innerHTML box) "")
    (when rows
      (construct-css!)
      (let [^js card (js/document.createElement "div")
            ^js h    (js/document.createElement "div")
            ^js list (js/document.createElement "div")]
        (set! (.. card -style -cssText) "background:#fff;border-radius:16px;padding:22px 26px;width:min(640px,90vw);max-height:80vh;overflow:auto;box-shadow:0 10px 40px rgba(0,0,0,.3);color:#1b1f2a")
        (set! (.-className h) "pm-who")
        (set! (.-textContent h) title)
        (.appendChild card h)
        (doseq [r rows]
          (let [^js row  (js/document.createElement "div")
                ^js top  (js/document.createElement "div")
                ^js body (js/document.createElement "div")]
            (set! (.. row -style -cssText) "border-top:1px solid #e5e7eb;padding:10px 4px;cursor:pointer")
            (set! (.. top -style -cssText) "display:flex;gap:10px;align-items:baseline")
            (set! (.-innerHTML top)
                  (str "<b style='min-width:120px'></b><span style='flex:1'></span>"
                       "<span style='font-size:12px;color:#6b7280'></span>"))
            (set! (.-textContent (aget (.-children top) 0)) (:who r))
            (set! (.-textContent (aget (.-children top) 1))
                  (str (when (:held? r) "⚠ held back · ") (:subject r)))
            (set! (.-textContent (aget (.-children top) 2))
                  (str (:ago r) (when (:sample? r) (str " · " sample))))
            (set! (.. body -style -cssText) "display:none;margin-top:8px;color:#374151;white-space:pre-wrap")
            (set! (.-textContent body) (:body r))
            (.addEventListener row "click"
              (fn [_] (set! (.. body -style -display)
                            (if (= "none" (.. body -style -display)) "block" "none"))))
            (.appendChild row top)
            (.appendChild row body)
            (.appendChild list row)))
        (.appendChild card list)
        (let [^js row (js/document.createElement "div")
              ^js b   (js/document.createElement "button")]
          (set! (.-className row) "pm-row")
          (set! (.-className b) "pm-btn")
          (set! (.. b -style -animation) "none")
          (set! (.-textContent b) close)
          (.addEventListener b "click" (fn [_] (show-mail! nil)))
          (.appendChild row b)
          (.appendChild card row))
        (.addEventListener card "click" (fn [^js e] (.stopPropagation e)))
        (.appendChild box card)))
    (set! (.. box -style -display) (if rows "flex" "none"))))

(defn- hex-css [n] (str "#" (.padStart (.toString (or n 0) 16) 6 "0")))

(defn figure-svg
  "The little figure used as an icon: a walker is head and body in its own
   colours with the joints as a ring; a creature (:kind :creature) is a low
   round body with a tail in its :color. `size` px tall."
  [{:keys [kind color body joints]} size]
  (let [w (js/Math.round (* size 0.75))]
    (str "<svg width='" w "' height='" size "' viewBox='0 0 18 24'>"
         (if (= :creature kind)
           (str "<ellipse cx='8' cy='15' rx='7' ry='5' fill='" (hex-css color) "' stroke='rgba(0,0,0,.45)' stroke-width='1.2'/>"
                "<circle cx='13' cy='12' r='3.5' fill='" (hex-css color) "' stroke='rgba(0,0,0,.45)' stroke-width='1.2'/>"
                "<path d='M1 15 q-2 4 3 6' fill='none' stroke='" (hex-css color) "' stroke-width='2'/>"
                "<circle cx='14' cy='11' r='.9' fill='#222'/>")
           (str "<circle cx='9' cy='5' r='4' fill='" (hex-css (or body 0xf3efe2)) "' stroke='" (hex-css (or joints 0x333333)) "' stroke-width='1.5'/>"
                "<rect x='4' y='10' width='10' height='13' rx='4' fill='" (hex-css (or body 0xf3efe2)) "' stroke='" (hex-css (or joints 0x333333)) "' stroke-width='1.5'/>"))
         "</svg>")))

(defn render-crew-key!
  "The crew key (who's on the estate): one row per walker -- a little figure
   in its own colours, its status dot and its name. Click a row to fly the
   camera to them. `rows` [{:id :name :body :joints :dot}] (colours as
   ints), or for a creature {:id :name :kind :creature :color :dot :real?
   :can-real?}; rebuilt only when they change. `on-pick` gets the row's id;
   a creature row ends in its SAMPLE/REAL checkbox (GDD §4.6: checked =
   REAL, her numbers from the twin's record; greyed when there's no real
   data), and `on-toggle` gets its id."
  [title rows on-toggle on-pick]
  (let [^js box (or (el "crew-key")
                    (let [^js b (js/document.createElement "details")]
                      (set! (.-id b) "crew-key")
                      (set! (.-open b) true)
                      (set! (.. b -style -cssText)
                            (str "position:fixed;right:12px;top:50%;transform:translateY(-50%);z-index:60;"
                                 "background:rgba(20,24,32,.72);color:#f3efe2;border-radius:12px;"
                                 "padding:8px 10px;font:13px system-ui,sans-serif;min-width:150px;"
                                 "backdrop-filter:blur(4px)"))
                      (.appendChild js/document.body b)
                      b))
        key (pr-str [title rows])]
    (when (not= key (.. box -dataset -key))
      (set! (.. box -dataset -key) key)
      (let [open? (.-open box)]
        (set! (.-innerHTML box) "")
        (let [^js s (js/document.createElement "summary")]
          (set! (.. s -style -cssText) "cursor:pointer;font-size:11px;letter-spacing:.12em;text-transform:uppercase;opacity:.8;margin-bottom:4px")
          (set! (.-textContent s) title)
          (.appendChild box s))
        (doseq [{:keys [id name body joints dot glyph kind color real? can-real?]} rows]
          (let [^js r (js/document.createElement "div")]
            (set! (.. r -style -cssText) "display:flex;align-items:center;gap:8px;padding:4px 4px;border-radius:8px;cursor:pointer")
            (.addEventListener r "mouseenter" (fn [_] (set! (.. r -style -background) "rgba(255,255,255,.12)")))
            (.addEventListener r "mouseleave" (fn [_] (set! (.. r -style -background) "transparent")))
            ;; the icon: a tiny figure, head and body in the walker's colour, joints as a
            ;; ring; a creature is a low round body with a tail, in its own colour
            (set! (.-innerHTML r)
                  (str (figure-svg {:kind kind :color color :body body :joints joints} 24)
                       "<span style='width:12px;text-align:center;font-weight:700;color:" (hex-css dot) "'>" (if (= "!" glyph) "!" "●") "</span>"
                       "<span></span>"))
            (set! (.-textContent (aget (.-children r) 2)) name)
            (.addEventListener r "click" (fn [^js e] (.preventDefault e) (.stopPropagation e) (on-pick id)))
            ;; the SAMPLE/REAL box: on creatures, and on crew members with a shipped
            ;; sample question (REAL for them is nothing until a real agent asks)
            (when (or (= :creature kind) can-real?)
              (let [^js lab (js/document.createElement "label")
                    ^js cb  (js/document.createElement "input")]
                (set! (.-type cb) "checkbox")
                (set! (.-checked cb) (boolean real?))
                (set! (.-disabled cb) (not can-real?))
                (set! (.. lab -style -cssText) "margin-left:auto;display:flex;align-items:center;gap:4px;font-size:10px;letter-spacing:.08em;opacity:.85;cursor:pointer")
                (set! (.-title lab) (cond (not can-real?) "SAMPLE: no real data behind this one yet"
                                          real? "REAL: the numbers are your twin's record; a feed here goes through the bridge. Untick for SAMPLE."
                                          :else "SAMPLE: pretend numbers, changes stay in this browser. Tick for REAL."))
                (.appendChild lab cb)
                (.appendChild lab (js/document.createTextNode (if real? "REAL" "SAMPLE")))
                (.addEventListener lab "click" (fn [^js e] (.stopPropagation e)))
                (.addEventListener cb "change" (fn [^js e] (.stopPropagation e) (on-toggle id)))
                (.appendChild r lab)))
            (.appendChild box r)))
        (set! (.-open box) open?)))))

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

;; -- The phone (GDD §2.1; Michael: "keep the interface familiar, like their phones") --
;; The 💭 panel is a phone: a contacts list, and one thread per contact with the
;; composer at the bottom. :thread is nil (contacts), :all (everything, the old
;; list), or a contact: a crew member's :agent, the twin's, or a mirror id.

(defonce !phone (atom {:thread nil}))
(defonce ^:private !phone-changed (atom nil))

(defn on-phone-change!
  "Registers the one callback run after the thread changes (core drops its
   render caches so the list and the roster redraw on the next tick)."
  [f] (reset! !phone-changed f))

(defn thread [] (:thread @!phone))

(defn set-thread! [t]
  (swap! !phone assoc :thread t)
  (when-let [f @!phone-changed] (f)))

(defn entry-contact
  "Which thread an entry belongs to: a crew line to its :member, the twin's
   lines (:kind :hal) to `twin` (his :agent), a creature's lines and chores
   to its :mirror; nil for the rest (they show only under :all)."
  [e twin]
  (cond
    (contains? talk/crew-kinds (:kind e)) (:member e)
    (= :hal (:kind e))                     twin
    (keyword? (:mirror e))                 (:mirror e)
    :else nil))

(defn- mine?
  "A line the player wrote (right-hand bubble)."
  [e]
  (or (= :crew-say (:kind e)) (= talk/you (:who e)) (= :player (:by e))))

(defn render-panel! [entries offset-of label-of on-pick on-check keep-scroll? crew gone?]
  (when-let [^js list-el (el "talk-list")]
    (let [^js panel      (el "talk-panel")
          t              (thread)
          entries        (cond (nil? t) []
                               (= :all t) entries
                               :else (filterv #(= t (entry-contact % (:twin crew))) entries))
          focused-n      (focused-checkbox-n)
          focused-value  (let [^js active (.-activeElement js/document)]
                           (when (and active (= "INPUT" (.-tagName active)) (= "text" (.-type active)))
                             (not-empty (.-value active))))
          ;; the history is its own scroll box now (the roster stays pinned below it)
          prev-scroll    (.-scrollTop list-el)]
      (set! (.-innerHTML list-el) "")
      (doseq [entry entries]
        (let [^js li (js/document.createElement "li")]
          (cond
            (talk/box? entry) (render-chore-row! li entry offset-of label-of on-check)
            (contains? talk/crew-kinds (:kind entry)) (render-crew-row! li entry offset-of crew)
            :else (render-say-row! li entry offset-of on-pick))
          (when (and (fn? gone?) (gone? entry))
            (.add (.-classList li) "gone"))
          (.add (.-classList li) (if (mine? entry) "mine" "theirs"))
          (.appendChild list-el li)))
      (set! (.-hidden list-el) (nil? t))
      (when focused-n
        (when-let [^js refocus (.querySelector list-el (str "input[data-n=\"" focused-n "\"]"))]
          (when (and focused-value (= "text" (.-type refocus)))
            (set! (.-value refocus) focused-value))
          (.focus refocus)))
      (when panel
        (if keep-scroll?
          (set! (.-scrollTop list-el) prev-scroll)
          (set! (.-scrollTop list-el) (.-scrollHeight list-el)))))))

(defn- contact-row!
  "One contact in the phone's list: dot or !, name, a one-line preview (the
   question if one's pending, else the last line in the thread), a › arrow.
   Tapping opens the thread."
  [^js root row preview unread]
  (let [^js r     (js/document.createElement "div")
        ^js glyph (js/document.createElement "span")
        ^js who   (js/document.createElement "span")
        ^js sub   (js/document.createElement "span")
        ^js arrow (js/document.createElement "span")]
    (set! (.-className r) "row contact")
    ;; the avatar: their figure in a round frame, the status (or !) as a small badge on
    ;; its corner, the way a phone shows a contact
    (set! (.-className glyph) (str "avatar" (when (:question row) " asking")))
    (set! (.-innerHTML glyph)
          (str (if (= :all (:member row))
                 "<span class='all'>∗</span>"
                 (figure-svg row 26))
               "<i class='badge' style='background:" (hex-css (or (:dot row) 0x9aa0a6)) "'>" (when (:question row) "!") "</i>"))
    (when (= :unknown (:status row)) (.add (.-classList glyph) "hollow"))
    (set! (.-className who) "who")
    (set! (.-textContent who) (:who row))
    (set! (.-className sub) "preview")
    (set! (.-textContent sub) (or (:question row) preview ""))
    (set! (.-className arrow) "arrow")
    (set! (.-textContent arrow) "›")
    (.addEventListener r "click" (fn [_] (set-thread! (:member row))))
    (.appendChild r glyph)
    (.appendChild r who)
    (.appendChild r sub)
    ;; unread count, the blue number a phone shows (Michael: "a number by each bot")
    (when (and (number? unread) (pos? unread))
      (let [^js b (js/document.createElement "span")]
        (set! (.-className b) "unread")
        (set! (.-textContent b) (if (> unread 99) "99+" (str unread)))
        (.appendChild r b)))
    (.appendChild r arrow)
    (.appendChild root r)))

(defn render-roster!
  "The phone's bottom part. With no thread open it's the contacts list: one
   row per `rows` entry (crew members, the twin, the creatures), plus
   Everything; `previews` is contact -> last line text. With a thread open
   it's that contact's header (‹ back, name, their pending question) and the
   composer: the say… input (enabled when the row is :say?), Enter calls
   (on-say member who value); a creature's thread has no composer (its
   chores are the checkboxes in the thread). A re-render keeps a typed value."
  [rows on-say _on-expand _offset-of _jum previews]
  (when-let [^js root (el "crew-roster")]
    (let [^js active    (.-activeElement js/document)
          focused-value (when (and active (= "INPUT" (.-tagName active)) (= "text" (.-type active)))
                          (not-empty (.-value active)))
          t             (thread)]
      (set! (.-innerHTML root) "")
      (if (nil? t)
        (let [unread (get previews ::unread {})]
          (doseq [row rows] (contact-row! root row (get previews (:member row)) (get unread (:member row))))
          (contact-row! root {:member :all :who "Everything" :status :idle} "the whole day, in order" nil))
        (let [row     (first (filter #(= t (:member %)) rows))
              ^js hd  (js/document.createElement "div")
              ^js bk  (js/document.createElement "button")
              ^js nm  (js/document.createElement "span")]
          (set! (.-className hd) "thread-head")
          (set! (.-className bk) "pm-btn back")
          (set! (.-textContent bk) "‹ Contacts")
          (.addEventListener bk "click" (fn [_] (set-thread! nil)))
          (set! (.-className nm) "who")
          (set! (.-textContent nm) (if (= :all t) "Everything" (or (:who row) (name t))))
          (.appendChild hd bk)
          (.appendChild hd nm)
          (.appendChild root hd)
          (when-let [q (:question row)]
            (let [^js qd (js/document.createElement "div")]
              (set! (.-className qd) "roster-log")
              (set! (.. qd -style -opacity) "1")
              (set! (.-textContent qd) (str "! " q))
              (.appendChild root qd)))
          (when (and row (not= :creature (:kind row)))
            (let [^js r   (js/document.createElement "div")
                  ^js inp (js/document.createElement "input")]
              (set! (.-className r) "row composer")
              (set! (.-type inp) "text")
              (.setAttribute inp "placeholder" (if (:question row) "answer…" (str "Text " (:who row) "…")))
              (.setAttribute inp "data-member" (name (:member row)))
              (set! (.-disabled inp) (not (:say? row)))
              (when focused-value (set! (.-value inp) focused-value))
              (.addEventListener inp "keydown"
                (fn [^js e] (when (= "Enter" (.-key e))
                              (on-say (:member row) (:who row) (.. e -target -value))
                              (set! (.-value (.-target e)) ""))))
              (.appendChild r inp)
              (.appendChild root r)
              (when (and active (= "INPUT" (.-tagName active)) (= "text" (.-type active)))
                (.focus inp))))
          (when (= :creature (:kind row))
            (let [^js p (js/document.createElement "div")]
              (set! (.-className p) "roster-log")
              (set! (.-textContent p) "Tick a chore above to do it; untick to undo.")
              (.appendChild root p))))))))

(defn show-hold-row!
  "The 'I'll answer' switch is the twin's setting: it shows only in his
   thread (Michael: it was 'taking up valuable real estate' everywhere)."
  [show?]
  (when-let [^js row (el "hold-row")]
    (set! (.-hidden row) (not show?))))

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

(defn remove-sources!
  "The SAMPLE/REAL switch used to be a row of buttons in the ⓘ panel; it
   lives on the creatures' crew-key rows now (render-crew-key!). Drops the
   old box if a stale page still has one."
  []
  (when-let [^js box (el "sources")]
    (.remove box)))

