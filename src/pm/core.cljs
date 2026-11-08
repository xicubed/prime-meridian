(ns pm.core
  "Entry point. The world is the purchased Hiroshi's Island kit (see `pm.kit`):
   the kit renders the estate, and the game layers its HUD, keys, rules and
   spatial brook on top, using the kit's own scene and THREE. Without the kit (a
   public clone), the week-1 scaffold runs instead: a cube over a ground plane.
   World state lives in one atom; the render loop reads it."
  (:require [pm.three :as t]
            [pm.audio :as audio]
            [pm.bricks :as bricks]
            [pm.brook :as brook]
            [pm.chores :as chores]
            [pm.content :as content]
            [pm.crew :as crew]
            [pm.crew-log :as cl]
            [pm.kit :as kit]
            [pm.motion :as m]
            [pm.overlay :as overlay]
            [pm.pick :as pick]
            [pm.scene :as scene]
            [pm.talk :as talk]
            [pm.ui :as ui]
            [pm.world :as world]
            [clojure.string]))

(defonce !world (atom (merge {:t 0 :spin 0.6} (world/load-estate world/estate-edn))))

(defonce !gfx (atom nil)) ; {:renderer :scene :camera :cube}

(def state-url "/state/mirrors.edn")
(def state-poll-ms 5000)

(defonce ^:private !state-poll (atom {:timer nil :busy? false}))

(defn- fetch-state! []
  (when-not (:busy? @!state-poll)
    (swap! !state-poll assoc :busy? true)
    (-> (js/fetch (str state-url "?t=" (js/Date.now)) #js {:cache "no-store"})
        (.then (fn [^js resp] (if (.-ok resp) (.text resp) nil)))
        (.then (fn [text]
                 (when-let [f (overlay/parse-file text)]
                   (let [before (:state-as-of @!world)]
                     (swap! !world overlay/apply-file f)
                     (when (not= before (:state-as-of @!world))
                       (js/console.info "Prime Meridian: state from Hal as of" (:state-as-of @!world)))))))
        (.catch (fn [_]))
        (.finally (fn [] (swap! !state-poll assoc :busy? false))))))

(defn- start-state-poll! []
  (when (nil? (:timer @!state-poll))
    (fetch-state!)
    (swap! !state-poll assoc :timer (js/setInterval fetch-state! state-poll-ms))))

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

(defn- fetch-bridge! []
  (when-not (:busy? @!bridge-poll)
    (swap! !bridge-poll assoc :busy? true)
    (-> (js/fetch (str bridge-url-file "?t=" (js/Date.now)) #js {:cache "no-store"})
        (.then (fn [^js resp] (if (.-ok resp) (.text resp) nil)))
        (.then (fn [text]
                 (if (nil? text)
                   (swap! !world assoc :bridge nil)
                   (let [b (crew/parse-bridge text)]
                     (when (not= b (:bridge @!world))
                       (swap! !world assoc :bridge b))))))
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
      (swap! !world overlay/apply-cache c))))

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

(def talk-key "pm.talk.v1")
(defonce ^:private !talk-text (atom nil))

(defn- read-talk []
  (try (.getItem js/localStorage talk-key) (catch :default _ nil)))

(defn- write-talk! [text]
  (try (.setItem js/localStorage talk-key text) (catch :default _ nil)))

(defn- fresh-start!
  "Testing aid: `?fresh` in the URL forgets the talk history and what was done
   to sample creatures in this browser. Hal's state (and its cache) and the
   voice setting are kept."
  []
  (when (.has (js/URLSearchParams. (.-search js/location)) "fresh")
    (try (.removeItem js/localStorage talk-key)
         (.removeItem js/localStorage play-key)
         (catch :default _ nil))))

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
  (when-let [el (js/document.getElementById "hud")]
    (set! (.-textContent el) text)))

(defonce !backend (atom "init…"))
(defonce !probe (atom nil)) ; ?probe=x,z;x,z → ground heights in the HUD
(defonce !mode (atom "loading")) ; "estate" (kit) or "scaffold"

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
      (when-let [s (crew/hud-text (:crew w) (mapv :agent (m/walkers w)) now)] (str " · " s))
      (when-let [p @!probe] (str " · " p))
      (when-let [h (kit/handle)]
        (when-let [b (brook/status h)] (str " · " b))))))

(defonce ^:private !talk-ui (atom {:shown nil :badge nil :alert nil
                                    :listed nil :listed-n nil :listed-bridge nil :roster nil
                                    :expanded #{} :hold-sw nil}))

(defonce ^:private !hold-ui (atom {:sending? false :error nil}))

(defn- tz-offset-min []
  (- (.getTimezoneOffset (js/Date.))))

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

(defn- on-say [member who text]
  (let [before-n (:n (:talk @!world))]
    (swap! !world update :talk talk/crew-say member who text (js/Date.now))
    (let [t (:talk @!world) n (:n t)]
      (when (> n before-n)
        (post-crew! member :say nil (:text (talk/entry t n)) n)))))

(defn- on-expand [member]
  (swap! !talk-ui update :expanded
         (fn [s] (let [s (or s #{})] (if (contains? s member) (disj s member) (conj s member))))))

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
  (let [t  (:talk @!world)
        t' (-> t
               (talk/observe @!world now)
               (talk/observe-crew (:crew @!world) now (talk/policy (:crew-talk @!world) (:hold @!world)))
               (talk/observe-log (:crew-log @!world) (:crew @!world) now))]
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
                               {:bridge-on? bridge-on :on-allow on-allow :on-deny on-deny} gone-fn)
            (swap! !talk-ui assoc :listed (:entries t') :listed-n (:n t') :listed-bridge bridge-on)))
        (swap! !talk-ui assoc :listed nil :listed-n nil :listed-bridge nil)))
    (let [rows (talk/crew-roster (:crew @!world) now (bridge-on?) (:crew-log @!world) (:expanded @!talk-ui))]
      (when (not= rows (:roster @!talk-ui))
        (ui/render-roster! rows on-say on-expand offset-of)
        (swap! !talk-ui assoc :roster rows)))
    (let [sw (talk/hold-switch (:hold @!world) (bridge-on?) @!hold-ui)]
      (when (not= sw (:hold-sw @!talk-ui))
        (ui/render-hold-switch! sw)
        (swap! !talk-ui assoc :hold-sw sw)))))

(defn frame [now]
  (let [dt (if-let [l @!last] (/ (- now l) 1000) 0)]
    (reset! !last now)
    (tick! (min dt 0.1))
    (when-let [h (kit/handle)] (bricks/tick! h (min dt 0.1)))
    (bricks/set-dots! (:crew @!world) (js/Date.now))
    (scene/update! @!world (js/Date.now))
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
          (do (brook/stop!) (set! (.-textContent btn) "▶ brook"))
          (do (brook/play! h (:sounds @!world)) (set! (.-textContent btn) "■ brook"))))))
  (when-let [el (js/document.getElementById "brook-flow")]
    (set! (.-value el) "0.33")
    (.addEventListener el "input"
      (fn [^js e] (brook/set-flow! (js/parseFloat (.. e -target -value)))))))

(defn- wire-pointer!
  "Click a creature to do its first due chore (through pm.chores); never
   interferes with the kit's own camera drag."
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
                          (pick/pick-mirror (scene/current-plan)
                                             (t/pick-names (scene/mounted-root) (.-camera h) xy)))))]
    (.addEventListener canvas "pointerdown"
      (fn [^js e]
        (when (and (.-isPrimary e) (zero? (.-button e)))
          (reset! !press {:x (.-clientX e) :y (.-clientY e) :t (.-timeStamp e)})))
      #js {:passive true})
    (.addEventListener js/window "pointerup"
      (fn [^js e]
        (when-let [down @!press]
          (reset! !press nil)
          (let [up {:x (.-clientX e) :y (.-clientY e) :t (.-timeStamp e)}]
            (when (pick/click? down up)
              (let [m (pick-at e)]
                (when (pick/clickable? @!world m)
                  (swap! !world chores/click content/hal-lines m (js/Date.now) (tz-offset-min))
                  (js/console.log (pr-str (peek (:log @!world))))))))))
      #js {:capture true :passive true})
    (.addEventListener canvas "pointermove"
      (fn [^js e]
        (when (zero? (.-buttons e))
          (let [now (js/Date.now)]
            (when (>= (- now @!last-hover) 50)
              (reset! !last-hover now)
              (let [m (pick-at e)]
                (if (pick/clickable? @!world m)
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
      (fn [^js e] (on-hold-toggle! (.. e -target -checked))))))

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
  (bricks/start! h @!world)
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
  (start-state-poll!)
  (start-crew-poll!)
  (start-bridge-poll!)
  (start-crew-log-poll!)
  (start-hold-poll!)
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
