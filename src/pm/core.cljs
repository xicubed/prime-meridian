(ns pm.core
  "Entry point. The world is the purchased Hiroshi's Island kit (see `pm.kit`):
   the kit renders the estate, and the game layers its HUD, keys, rules and
   spatial brook on top, using the kit's own scene and THREE. Without the kit (a
   public clone), the week-1 scaffold runs instead: a cube over a ground plane.
   World state lives in one atom; the render loop reads it."
  (:require [pm.three :as t]
            [pm.audio :as audio]
            [pm.blueprint :as bp]
            [pm.bricks :as bricks]
            [pm.brook :as brook]
            [pm.music :as music]
            [pm.chores :as chores]
            [pm.closing :as closing]
            [pm.content :as content]
            [pm.crew :as crew]
            [pm.crew-log :as cl]
            [pm.camera :as camera]
            [pm.construct :as construct]
            [pm.creature-spike :as creature-spike]
            [pm.events :as events]
            [pm.juice :as juice]
            [pm.sky :as sky]
            [cljs.reader]
            [pm.jumeaux :as j]
            [pm.kit :as kit]
            [pm.motion :as m]
            [pm.overlay :as overlay]
            [pm.pick :as pick]
            [pm.scene :as scene]
            [pm.plan :as plan]
            [pm.talk :as talk]
            [pm.ui :as ui]
            [pm.world :as world]
            [shadow.resource :as rc]
            [clojure.string]))

(defonce !world (atom (merge {:t 0 :spin 0.6} (world/load-estate world/estate-edn))))

;; Demo-mode sample of the four bridge events (GDD §3.1, §4.8): the four
;; shapes plus one refused for having no :source. Shown on the HUD so the
;; schema's validation is something you can see, until the real bridge
;; sends events like these over the websocket.
(def ^:private sample-events (events/parse (rc/inline "content/sample-events.edn")))

(defonce !gfx (atom nil)) ; {:renderer :scene :camera :cube}

;; The demo build (GDD §4.8, §6; `bb demo-build`): pretend data only. It
;; never asks for the twin's file, the crew's status, the crew log, the hold
;; or the bridge, so nothing real can reach it even on a host that served
;; them; every mirror stays SAMPLE.
(goog-define DEMO false)

(def state-url "/state/mirrors.edn")
(def state-poll-ms 5000)

(defonce ^:private !state-poll (atom {:timer nil :busy? false}))

(def sources-key "pm.sources.v1")
(defonce ^:private !sample-only   ; mirrors the player pinned to SAMPLE (GDD §4.6)
  (atom (or (try (let [v (cljs.reader/read-string (or (.getItem js/localStorage sources-key) "#{}"))]
                   (when (set? v) v))
                 (catch :default _ nil))
            #{})))
(defonce ^:private !twin-has (atom #{}))   ; mirrors the twin's file has real state for

;; SAMPLE/REAL for the crew (Michael: Le Critique "keeps asking round or jagged").
;; A crew member's SAMPLE side is its shipped sample question; REAL is nothing
;; until a real agent asks. {agent {:real? bool :answered? bool}}: answered
;; stays answered (the sample question never comes back) until the switch is
;; flipped, which resets it. Persisted in this browser.
(def crew-sample-key "pm.crew-sample.v1")
(defonce ^:private !crew-sample
  (atom (or (try (let [v (cljs.reader/read-string (or (.getItem js/localStorage crew-sample-key) "{}"))]
                   (when (map? v) v))
                 (catch :default _ nil))
            {})))

(defn- save-crew-sample! []
  (try (.setItem js/localStorage crew-sample-key (pr-str @!crew-sample)) (catch :default _ nil)))

(defn- sample-questions-for
  "The shipped sample questions (resources/content/sample-events.edn) an
   agent raises, by agent name string."
  [agent]
  (filterv #(and (= :agent-event (:type %)) (= :question (:kind %)) (= agent (:agent %)) (:source %))
           sample-events))

(defn- sample-question?
  "True when `ev` is one of the shipped sample questions, as the bridge
   replays them verbatim on every connect."
  [ev]
  (boolean (some #(= % ev) (sample-questions-for (:agent ev)))))

(defn- crew-sample-muted?
  "Sample questions from `agent` are dropped while it's switched to REAL or
   its sample question has been answered."
  [agent]
  (let [{:keys [real? answered?]} (get @!crew-sample (keyword agent))]
    (boolean (or real? answered?))))

(defn- fetch-state! []
  (when-not (:busy? @!state-poll)
    (swap! !state-poll assoc :busy? true)
    (-> (js/fetch (str state-url "?t=" (js/Date.now)) #js {:cache "no-store"})
        (.then (fn [^js resp] (if (.-ok resp) (.text resp) nil)))
        (.then (fn [text]
                 (when-let [f (overlay/parse-file text)]
                   (reset! !twin-has (set (keys (:mirrors f))))
                   (let [before (:state-as-of @!world)]
                     (swap! !world overlay/apply-file (overlay/without-mirrors f @!sample-only))
                     (when (not= before (:state-as-of @!world))
                       (js/console.info "Prime Meridian: state from your twin as of" (:state-as-of @!world)))))))
        (.catch (fn [_]))
        (.finally (fn [] (swap! !state-poll assoc :busy? false))))))

(def builds-url "/state/builds.edn")   ; builds made real (the bridge writes it)

(defn- fetch-builds! []
  (-> (js/fetch (str builds-url "?t=" (js/Date.now)) #js {:cache "no-store"})
      (.then (fn [^js resp] (if (.-ok resp) (.text resp) nil)))
      (.then (fn [text]
               (when-let [b (overlay/parse-file text)]
                 (swap! !world bp/apply-real-builds b))))
      (.catch (fn [_]))))

(defn- start-state-poll! []
  (when (nil? (:timer @!state-poll))
    (fetch-state!)
    (fetch-builds!)
    (swap! !state-poll assoc :timer (js/setInterval (fn [] (fetch-state!) (fetch-builds!)) state-poll-ms))))

(def crew-url "/state/crew.edn")
(def crew-poll-ms 2000)

(defonce ^:private !crew-poll (atom {:timer nil :busy? false}))

(defn- fetch-crew! []
  (when-not (:busy? @!crew-poll)
    (swap! !crew-poll assoc :busy? true)
    (-> (js/fetch (str crew-url "?t=" (js/Date.now)) #js {:cache "no-store"})
        (.then (fn [^js resp] (if (.-ok resp) (.text resp) nil)))
        (.then (fn [text]
                 (when-let [c (crew/parse text)]
                   (when (not= c (:crew @!world))
                     (swap! !world assoc :crew c)))))
        (.catch (fn [_]))
        (.finally (fn [] (swap! !crew-poll assoc :busy? false))))))

(defn- start-crew-poll! []
  (when (nil? (:timer @!crew-poll))
    (fetch-crew!)
    (swap! !crew-poll assoc :timer (js/setInterval fetch-crew! crew-poll-ms))))

(def bridge-url-file "/state/bridge.edn")
(def bridge-poll-ms 5000)

(defonce ^:private !bridge-poll (atom {:timer nil :busy? false}))

;; The bridge's events websocket (GDD §4.1, §4.7, §3.1): the four events --
;; agent-event, answer, chore-done, jack-out -- as JSON, both ways, over
;; ws://127.0.0.1:<port>/events. Only reached once bridge.edn says a bb
;; crew-bridge is live; closed and reopened whenever its token changes.
(defonce !ws (atom {:socket nil :status :closed :url nil :sent 0 :received 0}))

(defn- ws-close! []
  (when-let [^js sock (:socket @!ws)]
    (set! (.-onopen sock) nil)
    (set! (.-onclose sock) nil)
    (set! (.-onerror sock) nil)
    (set! (.-onmessage sock) nil)
    (.close sock))
  (swap! !ws assoc :socket nil :status :closed :url nil))

(defn- ws-send! [ev]
  (when-let [^js sock (:socket @!ws)]
    (when-let [j (and (= 1 (.-readyState sock)) (events/event->json ev))]
      (.send sock (js/JSON.stringify (clj->js j)))
      (swap! !ws update :sent inc))))

(defn- ws-on-message [^js e]
  (when-let [m (try (js->clj (js/JSON.parse (.-data e))) (catch :default _ nil))]
    (when-let [ev (events/json->event m)]
      (when (and (events/valid-event? ev)
                 (not (and (sample-question? ev) (crew-sample-muted? (:agent ev)))))
        (swap! !ws update :received inc)
        ;; world/apply-event lands all four shapes: a chore-done/jack-out
        ;; changes the estate directly; an agent-event raises the asking
        ;; agent's "!" (ask_player, GDD §3.1) and an answer clears it --
        ;; the player's own reply (see on-say) is still theirs to give.
        (swap! !world world/apply-event ev (js/Date.now))))))

(defn- connect-ws! [bridge]
  (let [url (crew/events-ws-url bridge)]
    (when (not= url (:url @!ws))
      (ws-close!)
      (let [^js sock (js/WebSocket. url)]
        (swap! !ws assoc :socket sock :status :connecting :url url)
        (set! (.-onopen sock) (fn [_] (swap! !ws assoc :status :open)))
        (set! (.-onclose sock)
              (fn [_] (when (identical? sock (:socket @!ws)) (swap! !ws assoc :socket nil :status :closed))))
        (set! (.-onerror sock) (fn [_] (swap! !ws assoc :status :error)))
        (set! (.-onmessage sock) ws-on-message)))))

(defn- fetch-bridge! []
  (when-not (:busy? @!bridge-poll)
    (swap! !bridge-poll assoc :busy? true)
    (-> (js/fetch (str bridge-url-file "?t=" (js/Date.now)) #js {:cache "no-store"})
        (.then (fn [^js resp] (if (.-ok resp) (.text resp) nil)))
        (.then (fn [text]
                 (if (nil? text)
                   (do (swap! !world assoc :bridge nil) (ws-close!))
                   (let [b (crew/parse-bridge text)]
                     (when (not= b (:bridge @!world))
                       (swap! !world assoc :bridge b))
                     (if (crew/bridge-on? b (js/Date.now))
                       (connect-ws! b)
                       (ws-close!))))))
        (.catch (fn [_]))
        (.finally (fn [] (swap! !bridge-poll assoc :busy? false))))))

(defn- start-bridge-poll! []
  (when (nil? (:timer @!bridge-poll))
    (fetch-bridge!)
    (swap! !bridge-poll assoc :timer (js/setInterval fetch-bridge! bridge-poll-ms))))

(def crew-log-url "/state/crew-log.edn")
(def hold-file-url "/state/hold.edn")
(def crew-talk-poll-ms 2000)

(defonce ^:private !crew-log-poll (atom {:timer nil :busy? false}))

(defn- fetch-crew-log! []
  (when-not (:busy? @!crew-log-poll)
    (swap! !crew-log-poll assoc :busy? true)
    (-> (js/fetch (str crew-log-url "?t=" (js/Date.now)) #js {:cache "no-store"})
        (.then (fn [^js resp] (if (.-ok resp) (.text resp) nil)))
        (.then (fn [text]
                 (let [l (cl/parse text)]
                   (when (not= l (:crew-log @!world))
                     (swap! !world assoc :crew-log l)))))
        (.catch (fn [_]))
        (.finally (fn [] (swap! !crew-log-poll assoc :busy? false))))))

(defn- start-crew-log-poll! []
  (when (nil? (:timer @!crew-log-poll))
    (fetch-crew-log!)
    (swap! !crew-log-poll assoc :timer (js/setInterval fetch-crew-log! crew-talk-poll-ms))))

(defonce ^:private !hold-poll (atom {:timer nil :busy? false}))

(defn- fetch-hold! []
  (when-not (:busy? @!hold-poll)
    (swap! !hold-poll assoc :busy? true)
    (-> (js/fetch (str hold-file-url "?t=" (js/Date.now)) #js {:cache "no-store"})
        (.then (fn [^js resp] (if (.-ok resp) (.text resp) nil)))
        (.then (fn [text]
                 (let [h (cl/parse-hold text)]
                   (when (not= h (:hold @!world))
                     (swap! !world assoc :hold h)))))
        (.catch (fn [_]))
        (.finally (fn [] (swap! !hold-poll assoc :busy? false))))))

(defn- start-hold-poll! []
  (when (nil? (:timer @!hold-poll))
    (fetch-hold!)
    (swap! !hold-poll assoc :timer (js/setInterval fetch-hold! crew-talk-poll-ms))))

(def cache-key "pm.state.v1")
(defonce ^:private !cache-text (atom nil)) ; last text read or written

(defn- read-cache []
  (try (.getItem js/localStorage cache-key) (catch :default _ nil)))

(defn- write-cache! [text]
  (try (.setItem js/localStorage cache-key text) (catch :default _ nil)))

(defn- restore-cache! []
  (let [text (read-cache)]
    (reset! !cache-text text)
    (when-let [c (overlay/parse-file text)]
      (swap! !world overlay/apply-cache (overlay/without-mirrors c @!sample-only)))))

(def play-key "pm.play.v1")
(defonce ^:private !play-text (atom nil))
(def ^:private sample-estate (world/load-estate world/estate-edn))

(defn- restore-play! []
  (let [text (try (.getItem js/localStorage play-key) (catch :default _ nil))]
    (reset! !play-text text)
    (when-let [p (overlay/parse-file text)]
      (swap! !world overlay/apply-play p))))

(defn- on-play-change! [_ _ old new]
  (when-not (identical? (:mirrors old) (:mirrors new))
    (let [text (pr-str (overlay/play-of new sample-estate))]
      (when (not= text @!play-text)
        (reset! !play-text text)
        (try (.setItem js/localStorage play-key text) (catch :default _ nil))))))

(defn- on-world-change! [_ _ old new]
  (when-not (and (identical? (:mirrors old) (:mirrors new))
                 (= (:state-as-of old) (:state-as-of new)))
    (when-let [c (overlay/cache-of new)]
      (let [text (pr-str c)]
        (when (not= text @!cache-text)
          (reset! !cache-text text)
          (write-cache! text))))))

(defn- toggle-crew-sample!
  "Flip crew member `agent` between SAMPLE and REAL. Either way the answered
   mark resets; to SAMPLE its sample question is asked again right now; to
   REAL any standing sample question (and its walk) is withdrawn."
  [agent]
  (let [real? (not (get-in @!crew-sample [agent :real?]))
        now   (js/Date.now)]
    (swap! !crew-sample assoc agent {:real? real? :answered? false})
    (save-crew-sample!)
    (if real?
      (swap! !world #(-> % (update :asks-player dissoc (name agent)) (update :errands dissoc (name agent))))
      (doseq [q (sample-questions-for (name agent))]
        (swap! !world world/apply-event q now)))))

(defn- toggle-source!
  "Flip mirror `id` between SAMPLE and REAL (GDD §4.6). To SAMPLE: back on
   its sample state (plus anything played in this browser). To REAL: the
   twin's file again, re-read now. A crew member's id goes to
   toggle-crew-sample! instead."
  [id]
  (if-not (contains? (:mirrors @!world) id)
    (when-let [agent (some #(when (= id (:id %)) (:agent %)) (:walkers @!world))]
      (toggle-crew-sample! agent))
    (do
      (if (contains? @!sample-only id)
        (when-not DEMO   ; the demo has no real side to switch to
          (swap! !sample-only disj id)
          (swap! !world dissoc :state-as-of)
          (fetch-state!))
        (do (swap! !sample-only conj id)
            (swap! !world #(-> % (overlay/to-sample sample-estate id)
                               (overlay/apply-play (overlay/parse-file @!play-text))))))
      (try (.setItem js/localStorage sources-key (pr-str @!sample-only)) (catch :default _ nil)))))

(defonce ^:private !source-rows (atom nil))

(defn- sources-tick!
  "The SAMPLE/REAL rows now live on the creatures' crew-key rows (Michael:
   'a checkbox behind her icon'; the ⓘ must never move); this keeps the
   current rows for crew-key-tick! and drops any old ⓘ box."
  []
  (let [rows (overlay/source-rows @!world @!sample-only @!twin-has)]
    (when (not= rows @!source-rows)
      (reset! !source-rows rows)
      (ui/remove-sources!))))

(def talk-key "pm.talk.v1")
(defonce ^:private !talk-text (atom nil))

(defn- read-talk []
  (try (.getItem js/localStorage talk-key) (catch :default _ nil)))

(defn- write-talk! [text]
  (try (.setItem js/localStorage talk-key text) (catch :default _ nil)))

;; The Construct's first beat (GDD §2.7, §4.8): whether this player has
;; ever seen the white room lives outside `world` (localStorage), the same
;; way !sample-only mirrors its own key instead of living on `world`.
(def construct-key "pm.construct.v1")
(defonce ^:private !construct-seen?
  ;; ?construct replays it without touching anything else saved (?fresh resets everything)
  (atom (and (not (.has (js/URLSearchParams. (.. js/window -location -search)) "construct"))
             (try (= "1" (.getItem js/localStorage construct-key)) (catch :default _ false)))))

(defn- fresh-start!
  "Testing aid: `?fresh` in the URL forgets the talk history and what was done
   to sample creatures in this browser, and lets the Construct's white room
   (GDD §2.7) play again. The twin's state (and its cache) and the voice
   setting are kept."
  []
  (when (.has (js/URLSearchParams. (.-search js/location)) "fresh")
    (try (.removeItem js/localStorage talk-key)
         (.removeItem js/localStorage play-key)
         (.removeItem js/localStorage construct-key)
         (catch :default _ nil))
    (reset! !construct-seen? false)))

(defonce ^:private !construct-said (atom nil))

(defn- jack-in-construct!
  "The jack-in click (GDD §2.7): starts the Construct, and -- being the
   player's first click -- lets the browser play sound: the brook starts
   and the twin's lines are spoken (if voice is on)."
  []
  (swap! !world construct/enter @!construct-seen? (js/Date.now))
  (when-let [h (kit/handle)]
    (when-not (brook/playing?)
      (brook/play! h (:sounds @!world))
      (when-let [^js btn (js/document.getElementById "brook-toggle")] (set! (.-textContent btn) "■ brook")))))

(defn- answer-sparring!
  "The player's click answering the sample worker's '!' (GDD §2.1, §2.7, §3.1)."
  []
  (swap! !world construct/answer @!construct-seen? (js/Date.now)))

(defn- approve-sparring!
  "The player's click approving the rounder-rock blueprint (GDD §2.1, §2.7, §4.5)."
  []
  (swap! !world construct/approve-rock @!construct-seen? (js/Date.now)))

(defn- jump-out!
  "The player's click jacking out during the Construct's fourth beat (GDD
   §2.3, §2.7, §4.8): the first pretend jack-out."
  []
  (swap! !world construct/jump-out @!construct-seen? (js/Date.now)))

(defn- jump-in!
  "The player's click jacking back in, right after."
  []
  (swap! !world construct/jump-in @!construct-seen? (js/Date.now)))

(defn- construct-tick!
  "Drives the Construct's five beats (GDD §2.3, §2.6, §2.7, §3.1, §4.5,
   §4.8) every frame until a first-time player has been through them once:
   records when the white room was entered, shows beat 1 (silent, then the
   twin's opening line), beat 2 (the program rack, then your own agent
   loading as its sample version, its building dropping out of the white
   sky), beat 3 (the sparring match: a sample-badged worker's practice job,
   its '!', the rounder rock's blueprint, the rock changing once it's
   approved), beat 4 (the jump program: the twin shows the hardline, the
   phone rings with a pretend chore, you jack out, you jack back in) and
   beat 5 (free your mind: the white room dissolves into the half-barren
   estate, then the twin's step-back line) through pm.ui, and marks it seen
   -- in `!world`'s own localStorage cache is not enough, since
   `!construct-seen?` must survive a reload -- once all five beats are
   done."
  [now]
  (when-not @!construct-seen?
    (let [room (construct/white-room @!world @!construct-seen? now)
          rack (construct/program-rack @!world @!construct-seen? now)
          spar (construct/sparring @!world @!construct-seen? now)
          jump (construct/jump-program @!world @!construct-seen? now)
          free (construct/free-your-mind @!world @!construct-seen? now)
          line (or (:line room) (:line rack) (:line spar) (:line jump) (:line free))]
      ;; the twin says its Construct lines aloud too, once each
      (when (and line (not= line @!construct-said) (:voice? (:talk @!world)))
        (reset! !construct-said line)
        (ui/speak! line (talk/voice-for @!world {:kind :hal})))
      (ui/set-white-room!
        (when room
          (let [on? (boolean (:voice? (:talk @!world)))
                labels (get-in @!world [:construct-script :voice])]
            (assoc room :voice on?
                        :voice-label (when (ui/speech?) (if on? (:on labels) (:off labels)))))))
      (ui/set-program-rack! rack)
      ;; the click hints are data too (:construct-script :hints)
      (let [hints (get-in @!world [:construct-script :hints])]
        (ui/set-sparring! (when spar (assoc spar :hint (get hints (:phase spar))
                                            :tag (get-in @!world [:construct-script :sample])
                                            :blueprint-who (get-in @!world [:construct-script :blueprint-who])
                                            :rock-labels (get-in @!world [:construct-script :rock-labels]))))
        (ui/set-jump-program! (when jump (assoc jump :hint (get hints (:phase jump))))))
      (ui/set-free-your-mind! free)
      ;; beat 5: as the white dissolves, your agent's building lands, in view
      (when (and (= :dissolve (:phase free)) (nil? (get-in @!world [:construct :building-at])))
        (swap! !world construct/land-building now)
        (when-let [h (kit/handle)]
          (let [{:keys [target offset]} (get-in @!world [:construct-script :land-camera])]
            (camera/frame! h target offset))))
      (when (= :loaded (:phase rack))
        (swap! !world construct/load-program (:id (:program rack)) now))
      (when (and (nil? room) (nil? rack) (nil? spar) (nil? jump) (nil? free) (:entered-at (:construct @!world)))
        (reset! !construct-seen? true)
        (try (.setItem js/localStorage construct-key "1") (catch :default _ nil))))))

(defn- restore-talk! []
  (let [text (read-talk)]
    (reset! !talk-text text)
    (swap! !world assoc :talk (talk/restore text))))

(defn- on-talk-change! [_ _ old new]
  (when-not (identical? (:talk old) (:talk new))
    (let [text (talk/save-text (:talk new))]
      (when (not= text @!talk-text)
        (reset! !talk-text text)
        (write-talk! text)))))

(defn build-scene! []
  (let [scene (t/scene)
        cube  (t/box {:size [1.5 1.5 1.5] :color 0xe07a5f :pos [0 0.75 0]})]
    (t/add! scene (t/lights) (t/ground {}) cube)
    {:scene scene :cube cube}))

(defn tick! [dt]
  (swap! !world update :t + dt)
  (when-let [{:keys [renderer scene camera ^js cube]} @!gfx]
    (let [{:keys [t spin]} @!world
          hue (mod (* t 0.08) 1)]                       ; full rainbow every ~12 s
      (set! (.-y (.-rotation cube)) (* t spin))
      (set! (.-x (.-rotation cube)) (* t spin 0.4))
      (.setHSL (.-color (.-material cube)) hue 0.6 0.55)
      (t/render! renderer scene camera))))

(defonce !last (atom nil))
(defonce !frames (atom 0))

(defn hud! [text]
  (when-let [el (or (js/document.getElementById "hud-text") (js/document.getElementById "hud"))]
    (set! (.-textContent el) text)))

(defonce !backend (atom "init…"))
(defonce !probe (atom nil)) ; ?probe=x,z;x,z → ground heights in the HUD
(defonce !mode (atom "loading")) ; "estate" (kit) or "scaffold"

(defn- ws-hud-segment []
  (let [{:keys [status sent received]} @!ws]
    (str "ws " (name status) " " received "/" sent)))

(defn- hud-mirrors-suffix []
  (let [w   @!world
        now (js/Date.now)]
    (str
      (apply str
        (for [id (get-in w [:hud :mirrors])
              :let [line (overlay/hud-line w id now)]
              :when line]
          (str " · " line)))
      (when-let [m (content/mail-line content/sample-inbox)] (str " · " m))
      (when-let [s (scene/summary)] (str " · " s))
      (when-let [s (bricks/hud-segment)] (str " · " s))
      (when-let [s (creature-spike/hud-segment)] (str " · " s))
      (when-let [s (crew/hud-text (:crew w) (mapv :agent (m/walkers w)) now)] (str " · " s))
      (when-let [s (events/hud-segment sample-events)] (str " · " s))
      ;; The juice meter (GDD §2.1, §4.4, §4.8): each AI login's estimated
      ;; share of its usage window left and its refill time.
      (when-let [s (juice/hud-segment (:logins w))] (str " · " s))
      (str " · " (ws-hud-segment))
      (when-let [s (world/jack-out-text w)] (str " · jacked out: " s))
      ;; Demo mode (GDD §4.6, §4.8, §2.8): zero accounts means every
      ;; connection runs on its pretend version, each wearing a visible
      ;; sample badge on the HUD until a real one is swapped in.
      (when-let [s (overlay/demo-line w (crew/bridge-on? (:bridge w) now))] (str " · " s))
      (when-let [p @!probe] (str " · " p))
      (when-let [h (kit/handle)]
        (when-let [b (brook/status h)] (str " · " b))))))

(defonce ^:private !talk-ui (atom {:shown nil :badge nil :alert nil
                                    :listed nil :listed-n nil :listed-bridge nil :roster nil
                                    :expanded #{} :hold-sw nil}))

(defonce ^:private !hold-ui (atom {:sending? false :error nil}))

(defn- tz-offset-min []
  (- (.getTimezoneOffset (js/Date.))))

(defonce ^:private !sky (atom nil))   ; the kit hour pm.sky last set
(declare toggle-jack-out! crew-key-tick! twin-thinking? twin-dots)
(defonce ^:private !gesture? (atom false))   ; browsers play nothing before one

(defn- music-scene
  "What the soundtrack follows right now (pm.music/track-for's input)."
  [now off]
  (let [w @!world seen @!construct-seen?
        pinned (some-> (.get (js/URLSearchParams. (.. js/window -location -search)) "hour") js/parseFloat)
        minute (if (and pinned (not (js/isNaN pinned))) (js/Math.round (* 60 pinned)) (sky/local-minute now off))]
    {:sparring?  (some? (construct/sparring w seen now))
     :construct? (boolean (some some? [(let [r (construct/white-room w seen now)] (when (not= :waiting (:phase r)) r))
                                       (construct/program-rack w seen now)
                                       (construct/jump-program w seen now)
                                       (construct/free-your-mind w seen now)]))
     :sky        (or @!sky (sky/hour-at (sky/schedule w) minute))}))

(defn- music-tick!
  "Keeps the soundtrack on the track the scene wants (pm.music), once the
   player has clicked or pressed a key and unless they turned it off."
  [now off]
  (when (and @!gesture? (music/on?))
    (let [m (:music @!world)]
      (music/play! m (music/track-for m (music-scene now off))))))

(defn- sky-tick!
  "The sky follows the clock (pm.sky): sets the kit's lighting when the
   schedule's hour changes. A `?hour=` in the URL pins the kit's own choice;
   no kit (the scaffold) = nothing to light."
  [now off]
  (when-let [^js h (kit/handle)]
    (when-let [^js rig (.-rig h)]
      (when-not (.get (js/URLSearchParams. (.. js/window -location -search)) "hour")
        (let [want (sky/hour-at (sky/schedule @!world) (sky/local-minute now off))]
          (when-let [{:keys [set immediate?]} (sky/follow @!sky want)]
            (.setHour rig (name set) immediate?)
            (reset! !sky set)))))))

(defn- offset-of [at]
  (- (.getTimezoneOffset (js/Date. at))))

(defonce ^:private !spoken (atom nil)) ; :n of the last line the browser actually started saying

(defn- say-entry!
  "Speak a talk entry once: a line that already started playing isn't said again."
  [e]
  (when (and e (not= (:n e) @!spoken))
    (ui/speak! (:text e) (talk/voice-for @!world e) #(reset! !spoken (:n e)))))

(defn- label-of [e]
  (talk/box-label e (offset-of (or (:at (:chore e)) (:at e)))
                  (world/chore-past @!world (:mirror e) (talk/box-chore e))))

(defn- pick! [entry]
  (when (:voice? (:talk @!world))
    (ui/stop-speech!)
    (ui/speak! (:text entry) (talk/voice-for @!world entry))))

(defn- on-check [e checked?]
  (swap! !world (fn [w] (if checked?
                          (chores/tick w content/hal-lines (:n e) (js/Date.now) (tz-offset-min))
                          (chores/untick w content/hal-lines (:n e) (js/Date.now)))))
  (swap! !talk-ui assoc :listed nil))

(defn- bridge-on? []
  (crew/bridge-on? (:bridge @!world) (js/Date.now)))

(defn- post-crew!
  "POSTs action to the bridge for member, then resolves talk entry n with
   the outcome. Resolves as a network/bridge-off error when there is no
   bridge to post to."
  [member action prompt-id text n]
  (if-let [b (:bridge @!world)]
    (-> (js/fetch (crew/bridge-url b member)
                  #js {:method "POST" :headers #js {"Content-Type" "application/json"}
                       :body (js/JSON.stringify (clj->js (crew/request-body b action prompt-id text)))})
        (.then (fn [^js resp]
                 (-> (.json resp)
                     (.then (fn [^js json] [(.-status resp) (.-reason json)]))
                     (.catch (fn [_] [(.-status resp) nil])))))
        (.then (fn [[status reason]]
                 (swap! !world update :talk talk/crew-result n (crew/outcome status reason) (js/Date.now))))
        (.catch (fn [_]
                  (swap! !world update :talk talk/crew-result n (crew/outcome nil nil) (js/Date.now)))))
    (swap! !world update :talk talk/crew-result n (crew/outcome nil nil) (js/Date.now))))

(defn- on-allow [n]
  (swap! !world update :talk talk/crew-send n :allow nil)
  (let [e (talk/entry (:talk @!world) n)]
    (when (= :sending (:state e))
      (post-crew! (:member e) :allow (:id (:prompt e)) nil n))))

(defn- on-deny [n note]
  (swap! !world update :talk talk/crew-send n :deny note)
  (let [e (talk/entry (:talk @!world) n)]
    (when (= :sending (:state e))
      (post-crew! (:member e) :deny (:id (:prompt e)) (:note e) n))))

(defn- send-answer!
  "Answers a pending ask_player question (GDD §3.1, §2.1, §4.8): sends a
   real :answer event for `agent` out over the bridge's websocket --
   reaching wherever the agent's side of the bridge is -- and clears its
   \"!\" locally the same way any bridge event would, so the next tick's
   set-dots! shows its ordinary crew dot (working again) instead of the
   \"!\"."
  [agent text]
  (let [ev {:type :answer :agent agent :text text
            :source {:agent "player" :session "ui" :machine "browser"}}
        asked (get-in @!world [:asks-player agent :text])]
    ;; a sample question answered stays answered (until the SAMPLE/REAL switch flips)
    (when (some #(= asked (:text %)) (sample-questions-for agent))
      (swap! !crew-sample assoc-in [(keyword agent) :answered?] true)
      (save-crew-sample!))
    (ws-send! ev)
    (swap! !world world/apply-event ev (js/Date.now))))

(defn- on-say
  "Typing to a member with a pending ask_player question answers it via
   send-answer!. Everyone else still goes through the Herdr crew-say/bridge
   POST path."
  [member who text]
  (let [agent (name member)]
    (if (world/needs-you? @!world agent)
      (when-let [t (not-empty (clojure.string/trim (or text "")))]
        (send-answer! agent t))
      (let [before-n (:n (:talk @!world))
            now      (js/Date.now)]
        (swap! !world update :talk talk/crew-say member who text now)
        (let [t (:talk @!world) n (:n t)]
          (when (> n before-n)
            (post-crew! member :say nil (:text (talk/entry t n)) n)
            ;; the twin with no bridge to carry the words (the demo): he says so
            ;; himself, on the estate, instead of a silent failed line
            (when (and (not (bridge-on?)) (= member (:agent (world/twin-walker @!world))))
              (when-let [ev (world/twin-says @!world (world/no-bridge-line @!world))]
                (swap! !world world/apply-event ev (+ now 1))))))))))

(defonce ^:private !sample-say (atom {:timer nil :n 0}))

(def errand-stand-off-m 1.2)

(defn- errands-tick!
  "Errands (TASKS T-2): a :pending errand sends its walker to its :where
   (world/errand-target from the walkers' live spots) and becomes :going;
   an unknown :where is said from where the walker stands. When a walker
   arrives (bricks/errand-arrivals!), its errand's words become its :say
   there, and the errand is done."
  [now]
  (let [w     @!world
        spots (bricks/spots)]   ; [] until the walkers are up: errands wait
    (doseq [[agent {:keys [where state]}] (:errands w)
            :when (and (= :pending state) (seq spots))]
      (let [from  (some (fn [s] (when (= (keyword agent) (:agent s)) (let [[x _ z] (:pos s)] [x z]))) spots)
            to    (world/errand-target w where spots from errand-stand-off-m)]
        (if (and to (bricks/send-on-errand! (keyword agent) to))
          (swap! !world assoc-in [:errands agent :state] :going)
          ;; nowhere to go (unknown place, or across the creek): say it here
          (swap! !world (fn [w] (let [{:keys [text]} (get-in w [:errands agent])]
                                  (-> (update w :errands dissoc agent)
                                      (world/apply-event {:type :agent-event :agent agent :kind :say :text text
                                                          :source {:agent "game" :session "errand" :machine "local"}} now))))))))
    (doseq [agent (bricks/errand-arrivals!)]
      (let [a (name agent)]
        (when-let [{:keys [text]} (get-in @!world [:errands a])]
          (swap! !world (fn [w] (-> (update w :errands dissoc a)
                                    (world/apply-event {:type :agent-event :agent a :kind :say :text text
                                                        :source {:agent "game" :session "errand" :machine "local"}} now)))))))))

(defn- sample-say-tick!
  "The demo build's twin (TASKS T-4): one SAMPLE line every :sample-every-s,
   only once the Construct is behind the player and the estate isn't frozen,
   and never over a line he's already showing."
  []
  (let [w @!world now (js/Date.now)]
    (when (and @!construct-seen? (not (world/jacked-out? w))
               (empty? (world/says-now w now)))
      (when-let [ev (world/twin-says w (world/sample-say w (:n @!sample-say)))]
        (swap! !sample-say update :n inc)
        (swap! !world world/apply-event ev now)))))

(defn- start-sample-says! []
  (when (nil? (:timer @!sample-say))
    (let [every (* 1000 (max 15 (or (get-in @!world [:twin-talk :sample-every-s]) 45)))]
      (swap! !sample-say assoc :timer (js/setInterval sample-say-tick! every)))))

(defn- on-expand [member]
  (swap! !talk-ui update :expanded
         (fn [s] (let [s (or s #{})] (if (contains? s member) (disj s member) (conj s member))))))

(defn- open-agent-talk!
  "Click an agent to talk (GDD §2.1, §4.8): opens the talk panel on that
   agent's row in the crew roster, with its say… input ready to type into —
   the speech bubble. The world (render loop, other walkers) keeps running;
   nothing here blocks on the bridge."
  [member]
  (when (keyword? member)
    (when-not (:open? (:talk @!world))
      (swap! !world update :talk talk/open-panel)
      (ui/set-panel-open! true))
    (swap! !talk-ui update :expanded (fn [s] (conj (or s #{}) member)))
    (js/setTimeout #(ui/focus-say-input! member) 0)))

(defn- on-hold-toggle! [checked?]
  (if-let [b (:bridge @!world)]
    (do
      (reset! !hold-ui {:sending? true :error nil})
      (-> (js/fetch (crew/hold-url b)
                    #js {:method "POST" :headers #js {"Content-Type" "application/json"}
                         :body (js/JSON.stringify (clj->js (crew/hold-body b checked?)))})
          (.then (fn [^js resp]
                   (if (.-ok resp)
                     (do (reset! !hold-ui {:sending? false :error nil})
                         (fetch-hold!))
                     (-> (.json resp)
                         (.then (fn [^js json] (reset! !hold-ui {:sending? false :error (or (.-reason json) "refused")})))
                         (.catch (fn [_] (reset! !hold-ui {:sending? false :error "refused"})))))))
          (.catch (fn [_] (reset! !hold-ui {:sending? false :error "network error"})))))
    (reset! !hold-ui {:sending? false :error "bridge off"})))

(defn- gone-fn [entry]
  (talk/gone? entry (:crew @!world) (js/Date.now)))

(defn- talk-tick! [now]
  (let [jum (j/names @!world)
        t  (:talk @!world)
        t' (-> t
               (talk/observe @!world now)
               (talk/observe-crew (:crew @!world) now (talk/policy (:crew-talk @!world) (:hold @!world)))
               (talk/observe-log (:crew-log @!world) (:crew @!world) now jum))]
    (when-not (identical? t' t)
      (swap! !world assoc :talk t'))
    (let [e (talk/caption t' now)]
      (cond
        (and (some? e) (not= (:n e) (:shown @!talk-ui)))
        (do
          (ui/show-caption! e)
          (when (:voice? t')
            (say-entry! e))
          (swap! !talk-ui assoc :shown (:n e)))

        (and (nil? e) (some? (:shown @!talk-ui)))
        (do
          (ui/hide-caption!)
          (swap! !talk-ui assoc :shown nil))))
    (let [badge  (talk/badge-text t')
          alert? (talk/prompt-waiting? t')]
      (when (or (not= badge (:badge @!talk-ui)) (not= alert? (:alert @!talk-ui)))
        (ui/set-badge! badge alert?)
        (swap! !talk-ui assoc :badge badge :alert alert?)))
    (let [bridge-on (bridge-on?)]
      (if (:open? t')
        (when-not (and (identical? (:entries t') (:listed @!talk-ui)) (= bridge-on (:listed-bridge @!talk-ui)))
          (let [keep-scroll? (= (:n t') (:listed-n @!talk-ui))]
            (ui/render-panel! (:entries t') offset-of label-of pick! on-check keep-scroll?
                               {:bridge-on? bridge-on :on-allow on-allow :on-deny on-deny :jumeaux jum} gone-fn)
            (swap! !talk-ui assoc :listed (:entries t') :listed-n (:n t') :listed-bridge bridge-on)))
        (swap! !talk-ui assoc :listed nil :listed-n nil :listed-bridge nil)))
    (let [asked (set (map keyword (keys (:asks-player @!world))))
          twin  (world/twin-walker @!world)
          rows  (-> (talk/crew-roster (:crew @!world) now (bridge-on?) (:crew-log @!world) (:expanded @!talk-ui) asked)
                    (talk/with-questions (:asks-player @!world))
                    (talk/with-twin twin (bridge-on?) (twin-thinking? @!world now)))]
      (when (not= rows (:roster @!talk-ui))
        (ui/render-roster! rows on-say on-expand offset-of jum)
        (swap! !talk-ui assoc :roster rows)))
    (let [sw (talk/hold-switch (:hold @!world) (bridge-on?) @!hold-ui jum)]
      (when (not= sw (:hold-sw @!talk-ui))
        (ui/render-hold-switch! sw)
        (swap! !talk-ui assoc :hold-sw sw)))))

(defn frame [now]
  (let [dt (if-let [l @!last] (/ (- now l) 1000) 0)
        ;; Jacked out (GDD §2.3, §4.8): the estate freezes gently -- no dt
        ;; reaches the things that move or tick time forward -- while the
        ;; render loop, labels and talk UI keep running.
        dt' (if (world/jacked-out? @!world) 0 (min dt 0.1))]
    (reset! !last now)
    (tick! dt')
    (when-let [h (kit/handle)] (bricks/tick! h dt'))
    (when (kit/handle) (creature-spike/tick! dt' now))
    (when-let [h (kit/handle)] (camera/save-tick! h now))
    (bricks/set-dots! (:crew @!world) (:asks-player @!world) (js/Date.now)
                      (twin-dots @!world (js/Date.now) (crew/colors @!world)))
    (bricks/set-says! (world/says-now @!world (js/Date.now)))
    (errands-tick! (js/Date.now))
    (crew-key-tick! (js/Date.now))
    (scene/update! @!world (js/Date.now))
    ;; The Construct's first beat (GDD §2.7, §4.8): a first-time player
    ;; lands in a silent white room before the twin steps in.
    (construct-tick! (js/Date.now))
    ;; Closing time (GDD §2.3, §4.8): last call, then goodnight, by the
    ;; player's own bedtime (declared data, pm.closing). The twin's banner
    ;; rides the same :hal path as jack-in. Night itself is the sky's
    ;; (pm.sky, following the clock), not a dimming layer.
    (let [closing-now (js/Date.now)
          off         (tz-offset-min)]
      (swap! !world closing/tick closing-now off)
      (sky-tick! closing-now off)
      ;; jacked out at the booth: the estate dims and says why (not the
      ;; Construct's practice jump, which has its own screen)
      (ui/show-jack-out!
        (when (and (world/jacked-out? @!world) (not (construct/jump-program @!world @!construct-seen? closing-now)))
          {:who    (get-in @!world [:jack-card :who])
           :line   (world/jack-out-text @!world)
           :note   (get-in @!world [:jack-card :note])
           :button (get-in @!world [:construct-script :hints :out])})
        (fn [] (toggle-jack-out!)))
      (music-tick! closing-now off)
      (sources-tick!)
      (ui/set-stay-late! (closing/asking-to-stay? @!world) (:stay-late-ask @!world)))
    ;; The juice meter's warning (GDD §2.1, §4.4, §4.8): the twin says a
    ;; one-line warning the first time a login's usage estimate crosses
    ;; 80%, same :hal banner path as closing time and jack-in.
    (swap! !world juice/tick (js/Date.now))
    (talk-tick! (js/Date.now))
    (when (zero? (mod (swap! !frames inc) 30))
      (hud! (str "Prime Meridian · " @!mode " · " @!backend " · frame " @!frames
                 " · t=" (.toFixed (:t @!world) 1) "s" (hud-mirrors-suffix))))
    (js/requestAnimationFrame frame)))

(defonce !brook-on (atom false))

(defn- brook-source []
  (some-> (js/document.getElementById "brook-source") .-value keyword))

(defn- brook-start! []
  (case (brook-source)
    :synth (audio/start!)
    :file  (audio/start-file!)
    :both  (do (audio/start!) (audio/start-file!))))

(defn- brook-stop! [] (audio/stop!) (audio/stop-file!))

(defn wire-brook-ui! []
  ;; default the source picker to "file" when the loop exists
  (audio/file-available?
    (fn [ok?]
      (when-let [sel (js/document.getElementById "brook-source")]
        (set! (.-value sel) (if ok? "file" "synth"))
        (set! (.-title sel) (if ok? "brook.mp3 found" "no brook.mp3 in public/assets/audio/")))))
  (when-let [btn (js/document.getElementById "brook-toggle")]
    (.addEventListener btn "click"
      (fn []
        (if @!brook-on
          (do (brook-stop!) (reset! !brook-on false) (set! (.-textContent btn) "▶ brook"))
          (do (brook-start!) (reset! !brook-on true) (set! (.-textContent btn) "■ brook"))))))
  (when-let [sel (js/document.getElementById "brook-source")]
    (.addEventListener sel "change" (fn [] (when @!brook-on (brook-stop!) (brook-start!)))))
  (doseq [[id k] [["brook-flow" :flow] ["brook-depth" :depth]]]
    (when-let [el (js/document.getElementById id)]
      (.addEventListener el "input"
        (fn [e] (let [v (js/parseFloat (.. e -target -value))]
                  (audio/set-param! k v)
                  (when (= k :flow) (audio/set-file-volume! v))))))))

(def ^:private brook-key "pm.brook")
(defn- brook-choice! [on?]
  (try (.setItem js/localStorage brook-key (if on? "1" "0")) (catch :default _ nil)))
(defn- brook-on? []
  (try (not= "0" (.getItem js/localStorage brook-key)) (catch :default _ true)))

(defn wire-kit-brook-ui!
  "With the kit: the brook is the recorded loop placed on the kit's water
   (pm.brook). Flow sets the pitch; synth/depth only apply to the scaffold."
  [h]
  (doseq [id ["brook-source" "brook-depth-label"]]
    (when-let [^js el (js/document.getElementById id)]
      (set! (.. el -style -display) "none")))
  (when-let [btn (js/document.getElementById "brook-toggle")]
    (.addEventListener btn "click"
      (fn [^js e]
        (.stopPropagation e)
        (if (brook/playing?)
          (do (brook/stop!) (set! (.-textContent btn) "▶ brook") (brook-choice! false))
          (do (brook/play! h (:sounds @!world)) (set! (.-textContent btn) "■ brook") (brook-choice! true))))))
  ;; Browsers play nothing before the player's first click or key, so the
  ;; brook starts on that first gesture anywhere -- unless the player turned
  ;; it off last time (remembered per browser).
  (let [start (fn start [_]
                (reset! !gesture? true)
                (js/removeEventListener "pointerdown" start true)
                (js/removeEventListener "keydown" start true)
                (when (and (brook-on?) (not (brook/playing?)))
                  (brook/play! h (:sounds @!world))
                  (when-let [^js b (js/document.getElementById "brook-toggle")] (set! (.-textContent b) "■ brook"))))]
    (js/addEventListener "pointerdown" start true)
    (js/addEventListener "keydown" start true))
  (when-let [^js mb (js/document.getElementById "music-toggle")]
    (set! (.-textContent mb) (if (music/on?) "♪ music" "♪ off"))
    (.addEventListener mb "click"
      (fn [^js e]
        (.stopPropagation e)
        (reset! !gesture? true)
        (let [m  (:music @!world)
              on (music/toggle! m (music/track-for m (music-scene (js/Date.now) (tz-offset-min))))]
          (set! (.-textContent mb) (if on "♪ music" "♪ off"))))))
  (when-let [el (js/document.getElementById "brook-flow")]
    (set! (.-value el) "0.33")
    (.addEventListener el "input"
      (fn [^js e] (brook/set-flow! (js/parseFloat (.. e -target -value)))))))

(declare open-agent-talk!)
(defonce ^:private !follow (atom nil))   ; walker id the camera is following

(defn- walker-colours
  "A walker's colours for the crew key: :body and :joints. A tint colours
   the mannequin's body and darkens its joints; :key-color overrides (a
   glTF character's own look); untinted is the mannequin's white."
  [entry]
  (let [white 0xf4f2ee dark 0x16171a]
    (cond
      (:colors entry)    {:body (get-in entry [:colors :body]) :joints (get-in entry [:colors :joints])}
      (:key-color entry) {:body (:key-color entry) :joints dark}
      (:tint entry)      {:body (m/tint-hex white (:tint entry)) :joints dark}
      :else              {:body white :joints dark})))

(defn- twin-thinking?
  "The twin's own status (TASKS T-3): :working from a say to him until his
   reply lands as a :say, else idle. false with no twin walker."
  [w now]
  (if-let [twin (world/twin-walker w)]
    (talk/twin-thinking? (:talk w) (:says w) (:agent twin) now)
    false))

(defn- twin-dots
  "{twin-agent dot} while the twin is thinking, for set-dots! and the key."
  [w now colors]
  (if-let [twin (world/twin-walker w)]
    (if-let [d (crew/think-dot (twin-thinking? w now) colors)] {(:agent twin) d} {})
    {}))

(defn- crew-key-tick!
  "The crew key: one row per walker; clicking flies the camera to them and
   follows them until the player takes the camera back (any press on the
   estate)."
  [now]
  (let [w @!world
        colors (crew/colors w)
        tdots  (twin-dots w now colors)
        rows (mapv (fn [entry]
                     (let [agent  (or (:agent entry) (:id entry))
                           asked? (contains? (:asks-player w) (name agent))
                           d      (or (crew/ask-dot asked? colors)
                                      (get tdots agent)
                                      (crew/dot (:crew w) agent now colors))]
                       ;; the same dot as over their head, so the key shows who needs you
                       (merge {:id (:id entry) :agent agent :name (:name entry)
                               :dot (:color d) :glyph (:glyph d)
                               ;; SAMPLE/REAL for the crew: only members with a shipped sample question get the box
                               :can-real? (boolean (seq (sample-questions-for (name agent))))
                               :real? (boolean (get-in @!crew-sample [agent :real?]))
                               :needs-you? (or asked? (and (crew/fresh? (:crew w) now)
                                                   (= :blocked (crew/effective-status (get-in w [:crew :agents agent]) now))))}
                              (walker-colours entry))))
                   (:walkers w))
        ;; the creatures too (Michael: "icons for Flora to zoom to her fast"):
        ;; a row each, the "!" when a chore of yours on them is due
        creatures (mapv (fn [c] (let [src (first (filter #(= (:id c) (:id %)) @!source-rows))]
                                  (assoc c :dot (if (plan/needs-you? w (:id c) now) (:needs-you colors) (:color c))
                                           :glyph (if (plan/needs-you? w (:id c) now) "!" "●")
                                           :needs-you? (plan/needs-you? w (:id c) now)
                                           ;; the SAMPLE/REAL switch (GDD §4.6) rides on the row
                                           :real? (boolean (:real? src)) :can-real? (boolean (:can-real? src)))))
                        (plan/creature-rows w scene/palette))
        rows (into rows creatures)
        fly! (fn [h [x y z] lift]
               (let [[ox oy oz] (get-in w [:crew-key :offset])
                     ;; come in from the creek's side (the open middle of the
                     ;; estate), not out of the woods behind them
                     ox (if (pos? x) (- (js/Math.abs ox)) (js/Math.abs ox))]
                 (camera/frame! h [x (+ y lift) z] [ox oy oz])))]
    (ui/render-crew-key! (get-in w [:crew-key :title]) rows toggle-source!
      (fn [id]
        (when-let [h (kit/handle)]
          (if-let [{:keys [pos]} (first (filter #(= id (:id %)) (bricks/spots)))]
            (do (fly! h pos 1.0) (reset! !follow id))
            (when-let [c (first (filter #(= id (:id %)) creatures))]
              (fly! h (:at c) 0.3) (reset! !follow nil))))
        ;; someone waiting on you: their entry in 💭 opens too, ready to answer
        (when-let [row (first (filter #(= id (:id %)) rows))]
          (when (and (:agent row)
                     (or (:needs-you? row) (= (:agent row) (:agent (world/twin-walker w)))))   ; the twin is always there to talk to
            (open-agent-talk! (:agent row))))))
    (when-let [id @!follow]
      (when-let [h (kit/handle)]
        (when-let [{:keys [pos]} (first (filter #(= id (:id %)) (bricks/spots)))]
          (let [[x y z] pos] (camera/follow! h [x (+ y 1.0) z])))))))

(defn- toggle-jack-out!
  "The phone booth (GDD §2.3, §4.8): with no real bridge sending `:jack-out`
   events yet, clicking the booth rings it with the sample jack-out event
   (resources/content/sample-events.edn) and clicking it again jacks back
   in, so the freeze and the resume are both something you can see."
  []
  (let [now (js/Date.now)]
    (if (world/jacked-out? @!world)
      (swap! !world world/jack-in now)
      (when-let [ev (first (filter #(= :jack-out (:event %)) sample-events))]
        (swap! !world world/apply-event ev now)))))

(declare apply-approved!)

(defn- say!
  "The twin says `text` in the caption (and voice, if on)."
  [event text]
  (when (and (string? text) (seq text))
    (let [now (js/Date.now)]
      (swap! !world assoc :hal {:event event :text text :until (+ now content/hal-ms)}))))

(defn- post-real!
  "POSTs `body` to the bridge's /real (GDD §4.6); calls (done ok? reason)."
  [body done]
  (if-let [b (:bridge @!world)]
    (-> (js/fetch (crew/real-url b)
                  #js {:method "POST" :headers #js {"Content-Type" "application/json"}
                       :body (js/JSON.stringify (clj->js (body b)))})
        (.then (fn [^js resp] (-> (.json resp) (.then (fn [^js j] (done (.-ok resp) (.-reason j)))))))
        (.catch (fn [_] (done false "bridge off"))))
    (done false "bridge off")))

(defn- make-real!
  "Click a trial build: record it as real through the bridge (GDD §2.1,
   §4.6), so it's in the estate every session and can't be dismissed."
  [id]
  (let [words (:blueprint-text @!world)]
    (if-not (bridge-on?)
      (say! :blueprint-real (:no-bridge words))
      (post-real! #(crew/real-build-body % id)
                  (fn [ok? reason]
                    (if ok?
                      (do (swap! !world bp/make-real id)
                          (say! :blueprint-real (:real words)))
                      (say! :blueprint-real (str (:no-bridge words) " (" reason ")"))))))))

(defn- real-mirror? [m]
  (= :hal (get-in @!world [:mirrors m :state :source])))

(defn- chore-click!
  "A chore clicked in the world. On a SAMPLE mirror it stays in this
   browser; on a REAL one it's recorded in the twin's file through the
   bridge (GDD §4.6), or, with no bridge, doesn't happen and the twin says
   why (a real thing must not change only on screen)."
  [m]
  (let [before @!world]
    (swap! !world chores/click content/hal-lines m (js/Date.now) (tz-offset-min))
    (let [s0 (get-in before [:mirrors m :state])
          s1 (get-in @!world [:mirrors m :state])]
      (when (and (real-mirror? m) (not= s0 s1))
        (if (bridge-on?)
          (post-real! #(crew/real-mirror-body % m s1)
                      (fn [ok? reason]
                        (when-not ok? (say! :real-chore (str (get-in before [:real-text :not-recorded]) " (" reason ")")))))
          (do (reset! !world before)
              (say! :real-chore (get-in before [:real-text :no-bridge]))))))))

(defn- on-blueprint-click!
  "A pending ghost builds (a trial); a trial build is made real; a real one
   stays as it is."
  [id]
  (case (bp/status @!world id)
    :pending  (apply-approved! id)
    :approved (make-real! id)
    nil))

(defn- blueprint-id-of [k]
  (when (and (vector? k) (= :blueprint (first k))) (second k)))

;; The last `!world` that was actually showing -- not a change still being
;; tried. `revert-to-last-good!` restores this when an approval would break
;; the build (GDD §4.8).
(defonce ^:private !last-good (atom nil))

(defn revert-to-last-good!
  "A bad change can never take down the running game (GDD §2.1, §4.8): put
   `!world` back to the last build that was actually showing, so a bad
   blueprint can never leave the estate broken."
  []
  (when-let [last-world @!last-good]
    (reset! !world last-world)
    (js/console.warn "Prime Meridian: blueprint reverted -- kept the last working build")))

(defn apply-approved!
  "Approve blueprint `id` (GDD §2.1, §4.5, §4.8): a change that can become
   real geometry goes straight into `!world` -- the running scene redraws
   it next frame, no page reload. A change that can't (`pm.blueprint/
   breaks-build?`) never reaches `!world` at all: revert-to-last-good!
   puts the estate back to the build that was already working."
  [id]
  (let [before    @!world
        blueprint (first (filter #(= id (:id %)) (bp/all before)))]
    (reset! !last-good before)
    (if (and blueprint (bp/breaks-build? blueprint))
      (do (revert-to-last-good!)
          ;; say so on its sign: a silent no-op reads as a broken click
          (swap! !world bp/refuse id (js/Date.now)))
      (swap! !world bp/approve-blueprint id))))

(defn- wire-pointer!
  "Click a creature to do its first due chore (through pm.chores); never
   interferes with the kit's own camera drag. A blueprint's ghost (GDD
   §2.1, §4.8): click it to approve the change (it goes solid in place),
   right-click it to reject (it vanishes)."
  [^js h]
  (let [^js canvas  (.. h -renderer -domElement)
        !press      (atom nil)
        !cursor     (atom nil)
        !last-hover (atom 0)
        pick-at     (fn [^js e]
                      (let [^js rect (.getBoundingClientRect canvas)
                            rect-m   {:left (.-left rect) :top (.-top rect)
                                      :width (.-width rect) :height (.-height rect)}
                            xy       (pick/ndc (.-clientX e) (.-clientY e) rect-m)]
                        (when xy
                          (let [hits  (t/pick-names (scene/mounted-root) (.-camera h) xy)
                                ;; walkers hang under their own root beside the
                                ;; estate's, so they need their own raycast
                                whits (when-let [^js wr (bricks/walkers-root)]
                                        (t/pick-names wr (.-camera h) xy))]
                            {:mirror (pick/pick-mirror (scene/current-plan) hits)
                             :key    (pick/pick-key hits)
                             :agent  (pick/pick-agent (m/walkers @!world) whits)}))))]
    (.addEventListener canvas "pointerdown"
      (fn [^js e]
        (reset! !follow nil)   ; the player takes the camera back
        (when (and (.-isPrimary e) (zero? (.-button e)))
          (reset! !press {:x (.-clientX e) :y (.-clientY e) :t (.-timeStamp e)})))
      #js {:passive true})
    (.addEventListener js/window "pointerup"
      (fn [^js e]
        (when-let [down @!press]
          (reset! !press nil)
          (let [up {:x (.-clientX e) :y (.-clientY e) :t (.-timeStamp e)}]
            (when (pick/click? down up)
              (let [{m :mirror k :key a :agent} (pick-at e)]
                (cond
                  (= k pick/booth-key) (toggle-jack-out!)
                  (= k pick/mailbox-key)
                  (ui/show-mail! (assoc (:mail-panel @!world)
                                        :rows (content/inbox-rows content/sample-inbox)))
                  (blueprint-id-of k) (on-blueprint-click! (blueprint-id-of k))
                  (some? a) (open-agent-talk! a)
                  (pick/clickable? @!world m)
                  (chore-click! m)))))))
      #js {:capture true :passive true})
    ;; Reject = a right-button *click* on a ghost (press and release in
    ;; place, like pm.pick/click?), not the contextmenu event: on macOS that
    ;; fires on press, so starting a camera pan over a ghost would reject it.
    (let [!rpress (atom nil)]
      (.addEventListener canvas "pointerdown"
        (fn [^js e]
          (when (and (.-isPrimary e) (= 2 (.-button e)))
            (reset! !rpress {:x (.-clientX e) :y (.-clientY e) :t (.-timeStamp e)})))
        #js {:passive true})
      (.addEventListener js/window "pointerup"
        (fn [^js e]
          (when-let [down @!rpress]
            (reset! !rpress nil)
            (when (pick/click? down {:x (.-clientX e) :y (.-clientY e) :t (.-timeStamp e)})
              (when-let [id (blueprint-id-of (:key (pick-at e)))]
                (swap! !world bp/reject-blueprint id)))))
        #js {:capture true :passive true})
      (.addEventListener canvas "contextmenu"
        (fn [^js e]
          (when (blueprint-id-of (:key (pick-at e))) (.preventDefault e)))))
    (.addEventListener canvas "pointermove"
      (fn [^js e]
        (when (zero? (.-buttons e))
          (let [now (js/Date.now)]
            (when (>= (- now @!last-hover) 50)
              (reset! !last-hover now)
              (let [{m :mirror k :key a :agent} (pick-at e)]
                (if (or (= k pick/booth-key) (blueprint-id-of k) (some? a) (pick/clickable? @!world m))
                  (do
                    (when (nil? @!cursor) (reset! !cursor (.. canvas -style -cursor)))
                    (set! (.. canvas -style -cursor) "pointer"))
                  (when-let [prev @!cursor]
                    (set! (.. canvas -style -cursor) prev)
                    (reset! !cursor nil)))))))))))

(def ^:private voice-key "pm.voice")

(defn- save-voice! [on?]
  (try (.setItem js/localStorage voice-key (if on? "1" "0")) (catch :default _ nil)))

(defn- saved-voice? []
  (try (= "1" (.getItem js/localStorage voice-key)) (catch :default _ false)))

(defn- arm-voice!
  "Voice was left on last time, but browsers won't speak before the player's
   first click or key on the page: at that moment, say the caption on screen
   if it never got to play. Never repeats a line that was already heard."
  []
  (let [armed (atom false)
        go    (fn [^js e]
                (when-not (or @armed
                              (= "voice-toggle" (some-> (.-target e) .-id)))
                  (reset! armed true)
                  (when (:voice? (:talk @!world))
                    (js/setTimeout
                      #(say-entry! (talk/caption (:talk @!world) (js/Date.now)))
                      50))))]
    (.addEventListener js/window "pointerdown" go #js {:capture true :once true})
    (.addEventListener js/window "keydown" go #js {:capture true :once true})))

(defn- wire-talk-ui! []
  (if-not (ui/speech?)
    (ui/hide-voice-button!)
    (when-let [^js btn (js/document.getElementById "voice-toggle")]
      (when (saved-voice?)
        (swap! !world update :talk (fn [t] (assoc (or t talk/empty-talk) :voice? true)))
        (ui/set-voice-button! true)
        (arm-voice!))
      (.addEventListener btn "click"
        (fn [^js e]
          (.stopPropagation e)
          (swap! !world update :talk talk/toggle-voice)
          (let [on? (:voice? (:talk @!world))]
            (ui/set-voice-button! on?)
            (save-voice! on?)
            (if on?
              (ui/speak! "Voice on." (talk/voice-for @!world {:kind :hal}))
              (ui/stop-speech!)))))))
  (when-let [^js btn (js/document.getElementById "talk-toggle")]
    (.addEventListener btn "click"
      (fn [^js e]
        (.stopPropagation e)
        (swap! !world update :talk talk/toggle-panel)
        (ui/set-panel-open! (:open? (:talk @!world))))))
  (when-let [^js cb (js/document.getElementById "hold-toggle")]
    (.addEventListener cb "change"
      (fn [^js e] (on-hold-toggle! (.. e -target -checked)))))
  ;; Staying past closing (GDD §2.3, §4.8): the twin asks once; Stay keeps
  ;; the estate open, Close up jacks out for the night (the booth brings you
  ;; back); either way the ask doesn't come back tonight.
  (ui/wire-stay-late! (fn [] (swap! !world closing/stay-late (js/Date.now)))
                      (fn [] (swap! !world closing/close-up
                                    (get-in @!world [:stay-late-ask :sign]) (js/Date.now))))
  ;; the voice switch on the jack-in screen works the 🔊 button itself, so
  ;; the two can't disagree
  (ui/wire-white-room! jack-in-construct!
                       (fn [] (some-> (js/document.getElementById "voice-toggle") (.click))))
  ;; who's talking in the Construct: the twin's name, then its role
  (ui/set-speaker! (str (get-in @!world [:jumeaux :twin :name]) " · "
                        (get-in @!world [:construct-script :speaker])))
  (ui/wire-sparring! answer-sparring! approve-sparring!)
  (ui/wire-jump-program! jump-out! jump-in!))

(defn- jack-in! []
  (swap! !world content/hal-say content/hal-lines :jack-in {} (js/Date.now)))

(defn- probe!
  "Dev aid: `?probe=x,z;x,z` prints what's under each point (first 5 hits,
   top down from 40 m) in the HUD, so placements can be checked as text."
  [^js h]
  (when-let [q (.get (js/URLSearchParams. (.. js/window -location -search)) "probe")]
    (reset! !probe
            (str "probe "
                 (clojure.string/join
                  " | "
                  (for [pt (clojure.string/split q #";")
                        :let [[x z] (map js/parseFloat (clojure.string/split pt #","))]]
                    (str x "," z ": "
                         (clojure.string/join
                          ", " (for [[y n] (t/drop-ray (.-scene h) (.-camera h) x z 40 5)]
                                 (str y " " n))))))))))

(defn start-kit!
  "The kit's scene is live: show the game's layer on top of it."
  [^js h]
  (reset! !mode "estate")
  (reset! !backend (kit/backend-name h))
  (js/console.log "Prime Meridian: running on the kit's estate," @!backend)
  (wire-kit-brook-ui! h)
  (t/use-three! (.-THREE h))
  (scene/mount! (.-scene h) @!world (js/Date.now))
  (when (camera/restore! h) (js/console.log "Prime Meridian: camera restored"))
  (bricks/start! h @!world)
  (creature-spike/start! h @!world)
  (wire-pointer! h)
  (probe! h)
  (jack-in!)
  (js/requestAnimationFrame frame))

(defn start-scaffold! []
  (reset! !mode "scaffold")
  (wire-brook-ui!)
  (let [renderer (t/renderer)
        camera   (t/camera)
        {:keys [scene cube]} (build-scene!)]
    (t/on-resize! camera renderer)
    (reset! !gfx {:renderer renderer :scene scene :camera camera :cube cube})
    (t/init-renderer! renderer
                      (fn [backend]
                        (reset! !backend backend)
                        (js/console.log "Prime Meridian: renderer ready on" backend)
                        (jack-in!)
                        (js/requestAnimationFrame frame)))))

(defn init! []
  ;; Capture phase: the kit's own controls handle keys too and may stop them.
  (.addEventListener js/window "keydown"
    (fn [^js e]
      (when (talk/close-key? (.-key e) (:open? (:talk @!world)) (some-> (.-target e) .-tagName))
        (.preventDefault e) (.stopPropagation e)
        (swap! !world update :talk talk/close-panel)
        (ui/set-panel-open! false)))
    true)
  (fresh-start!)
  (restore-talk!)
  (wire-talk-ui!)
  (add-watch !world ::talk-cache on-talk-change!)
  (restore-play!)
  (restore-cache!)
  (add-watch !world ::state-cache on-world-change!)
  (add-watch !world ::play-cache on-play-change!)
  (if DEMO
    (start-sample-says!)   ; the demo's twin has a voice with nothing real behind it
    (do (start-state-poll!)
        (start-crew-poll!)
        (start-bridge-poll!)
        (start-crew-log-poll!)
        (start-hold-poll!)))
  (kit/load! start-kit! start-scaffold!))

(defn reload!
  "shadow-cljs after-load hook: rebuild the scaffold scene, keep renderer/camera/world.
   (With the kit there's nothing to rebuild: the kit owns its scene.)"
  []
  (when-let [{:keys [renderer camera]} @!gfx]
    (let [{:keys [scene cube]} (build-scene!)]
      (swap! !gfx assoc :scene scene :cube cube)
      (js/console.log "Prime Meridian: reloaded")
      (t/render! renderer scene camera))))
