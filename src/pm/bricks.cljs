(ns pm.bricks
  "Interop for the crew walkers: default kinematic figures, or (with
   `?bricks`) SOMA mannequins driven by one shared MotionBricks model. No
   rules live here — pm.motion decides, this namespace only moves three.js
   objects and the MotionBricks characters to match."
  (:require [pm.three :as t]
            [pm.motion :as m]
            [pm.plan :as p]
            [pm.scene :as scene]))

(defonce !state
  (atom {:status nil :walkers [] :views [] :ground-meshes [] :frame 0
         :cfg nil :root nil :started? false}))

(declare fallback! swap-in!)

(defn- part-color [kw]
  (get scene/palette kw))

(defn- build-figure! [heading]
  (let [^js fig (t/group {:rot [0 heading 0]})]
    (doseq [part (:parts m/figure)]
      (t/add! fig (t/stand-in {:shape (:shape part)
                                :size  (:size part)
                                :color (part-color (:color part))
                                :pos   (:at part)
                                :rot   (mapv p/radians (:rot part))})))
    fig))

(defn- build-view! [^js root w]
  (let [[x z] (:pos w)
        y     (:y w)
        ^js fig (build-figure! (:heading w))
        ^js lbl (t/label {:text (:name w) :height scene/label-height
                           :paper (part-color :sign-paper) :ink (part-color :sign-ink)
                           :pos [0 (:label-y m/figure) 0]})
        ^js anchor (t/group {:name (str "pm-walkers/" (name (:id w))) :pos [x (or y 0) z]})]
    (t/add! anchor fig lbl)
    (t/set-visible! anchor (some? y))
    (t/add! root anchor)
    {:anchor anchor :figure fig :char nil}))

(defn start!
  "No-op if already started (hot reload) or there are no walkers."
  [^js h world]
  (when (and (not (:started? @!state)) (seq (m/walkers world)))
    (let [cfg          (m/config world)
          ^js root     (t/group {:name "pm-walkers"})
          _            (t/add! (.-scene h) root)
          ground-meshes (t/find-meshes (.-scene h) #(m/ground-name? % (:ground cfg)))
          sample       (fn [w]
                         (let [[x z] (:pos w)
                               hits  (t/down-hits ground-meshes x z 40)]
                           (m/settle (m/sense-ground w hits (:ground cfg)) 0)))
          walkers      (mapv sample (m/walkers world))
          views        (mapv #(build-view! root %) walkers)]
      (reset! !state {:status {:mode :kinematic} :walkers walkers :views views
                       :ground-meshes ground-meshes :frame 0 :cfg cfg :root root :started? true})
      (when (and (m/bricks-flag? (.. js/window -location -search)) (:bricks cfg))
        (swap! !state assoc :status {:mode :loading :progress 0})
        (try
          (-> (js/Promise.resolve nil)
              (.then (fn [_] ((js/Function. "u" "return import(u)") (:lib (:bricks cfg)))))
              (.then
                (fn [^js mod]
                  (let [b         (:bricks cfg)
                        ^js C     (.-MotionBricksCharacter mod)
                        chars     (mapv (fn [_] (C. #js {:rig (name (:rig b)) :model (:model b)})) walkers)]
                    (doseq [^js c chars]
                      (set! (.-onError c) fallback!)
                      (.setMannequin c))
                    (swap! !state update :views
                           (fn [vs] (mapv (fn [v c] (assoc v :char c)) vs chars)))
                    (let [^js first-c (first chars)
                          on-progress (fn [stage l t]
                                        (when-let [f (m/load-fraction stage l t)]
                                          (swap! !state assoc-in [:status :progress] f)))]
                      (-> (.load first-c on-progress)
                          (.then (fn [_]
                                   (js/Promise.all
                                     (into-array (mapv (fn [^js c] (.loadFrom c first-c)) (rest chars))))))
                          (.then (fn [_] (swap-in! h chars))))))))
              (.catch (fn [e] (fallback! e))))
          (catch :default e (fallback! e)))))))

(defn- swap-in!
  [^js h chars]
  (let [{:keys [walkers views root]} @!state]
    (doseq [[w view ^js c] (map vector walkers views chars)]
      (let [[x z] (:pos w) y (:y w)
            ^js obj (.-object c)]
        (t/set-pos! obj [x (or y 0) z])
        (t/set-yaw! obj (:heading w))
        (t/add! root obj)
        (t/set-visible! (:figure view) false)
        (when (zero? (:pause w))
          (let [[tx tz] (m/target w)]
            (.moveTo c (t/vec3 [tx (or y 0) tz]) "walk")))))
    (let [views'  (mapv (fn [view c] (assoc view :char c)) views chars)
          ^js first-c (first chars)
          gen-ms  (.. first-c -stats -averageInferenceMs)]
      (swap! !state assoc :views views'
             :status {:mode :bricks :backend (.-backend first-c) :walkers (count chars)
                      :gen-ms (when (number? gen-ms) gen-ms)}))))

(defn- fallback!
  "Ignored when the status is already :failed or :kinematic."
  [e]
  (when-not (contains? #{:failed :kinematic} (:mode (:status @!state)))
    (js/console.warn "Prime Meridian: motion bricks failed" e)
    (let [{:keys [views root]} @!state]
      (doseq [view views]
        (when-let [^js c (:char view)]
          (try (.dispose c) (catch :default _ nil))
          (when-let [^js obj (.-object c)]
            (t/remove! root obj)))
        (t/set-visible! (:figure view) true))
      (swap! !state assoc
             :views (mapv #(dissoc % :char) views)
             :status {:mode :failed :reason (m/short-reason (or (.-message e) e))}))))

(defn- tick-kinematic! [dt]
  (let [{:keys [cfg walkers views]} @!state
        speed    (:speed cfg)
        walkers' (mapv (fn [w] (m/settle (m/step w speed dt) dt)) walkers)]
    (doseq [[w view] (map vector walkers' views)]
      (let [[x z] (:pos w) y (:y w)
            ^js anchor (:anchor view) ^js fig (:figure view)]
        (t/set-pos! anchor [x (or y 0) z])
        (t/set-yaw! fig (:heading w))
        (t/set-visible! anchor (some? y))))
    (swap! !state assoc :walkers walkers')))

(defn- tick-bricks! [dt]
  (try
    (let [{:keys [walkers views]} @!state
          walkers' (mapv
                     (fn [w view]
                       (let [^js c (:char view)
                             ^js v (.getPosition c)
                             pos   [(.-x v) (.-z v)]
                             w'    (m/settle (m/follow w pos (.-idle c) dt) dt)]
                         (when (m/new-target? w w')
                           (let [[tx tz] (m/target w')]
                             (.moveTo c (t/vec3 [tx (:y w') tz]) "walk")))
                         (.update c dt)
                         (let [[x z] (:pos w') y (:y w')
                               ^js obj (.-object c)]
                           (set! (.-y (.-position obj)) y)
                           (t/set-pos! (:anchor view) [x (or y 0) z]))
                         w'))
                     walkers views)]
      (swap! !state assoc :walkers walkers')
      (when-let [^js c0 (:char (first views))]
        (let [gen-ms (.. c0 -stats -averageInferenceMs)]
          (when (number? gen-ms)
            (swap! !state update :status assoc :gen-ms gen-ms)))))
    (catch :default e (fallback! e))))

(defn tick!
  "Call every frame with dt capped at 0.1. No-op before start!."
  [^js h dt]
  (when (:started? @!state)
    (let [{:keys [frame cfg ground-meshes]} @!state
          ground-meshes (if (and (empty? ground-meshes) (zero? (mod frame 60)))
                          (let [gm (t/find-meshes (.-scene h) #(m/ground-name? % (:ground cfg)))]
                            (swap! !state assoc :ground-meshes gm)
                            gm)
                          ground-meshes)]
      (when (seq ground-meshes)
        (swap! !state update :walkers
               (fn [walkers]
                 (vec (map-indexed
                        (fn [i w]
                          (if (= (mod frame 4) (mod i 4))
                            (let [[x z] (:pos w)]
                              (m/sense-ground w (t/down-hits ground-meshes x z 40) (:ground cfg)))
                            w))
                        walkers)))))
      (if (= :bricks (:mode (:status @!state)))
        (tick-bricks! dt)
        (tick-kinematic! dt))
      (swap! !state update :frame inc))))

(defn hud-segment []
  (m/hud-text (:status @!state)))

(defn status []
  (:status @!state))
