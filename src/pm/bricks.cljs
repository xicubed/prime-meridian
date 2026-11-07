(ns pm.bricks
  "Interop for the crew walkers: default kinematic figures, or (with
   `?bricks`) SOMA mannequins — retargeted to a glTF character when a walker
   names one — driven by one shared MotionBricks model, both steered clear of
   statics and each other by pm.motion's planner (pm.avoid's geometry). No
   rules live here — pm.motion decides, this namespace only moves three.js
   objects and the MotionBricks characters to match."
  (:require [pm.three :as t]
            [pm.motion :as m]
            [pm.avoid :as a]
            [pm.plan :as p]
            [pm.scene :as scene]
            [pm.feet :as f]))

(defonce !state
  (atom {:status nil :walkers [] :views [] :ground-meshes [] :frame 0
         :cfg nil :statics [] :pcfg nil :plan-acc 0 :paths nil :ds 1.0
         :root nil :started? false}))

(defonce !gltf-loader (atom nil))

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

(defn- build-view! [^js root ds w]
  (let [[x z] (:pos w)
        y     (:y w)
        ^js fig (build-figure! (:heading w))
        _       (t/set-scale! fig ds)
        ^js lbl (t/label {:text (:name w) :height scene/label-height
                           :paper (part-color :sign-paper) :ink (part-color :sign-ink)
                           :pos [0 (m/label-height nil ds) 0]})
        ^js anchor (t/group {:name (str "pm-walkers/" (name (:id w))) :pos [x (or y 0) z]})]
    (t/add! anchor fig lbl)
    (t/set-visible! anchor (some? y))
    (t/add! root anchor)
    {:anchor anchor :figure fig :char nil :sent nil :label lbl :top nil}))

;; -- ?paths debug overlay ----------------------------------------------------

(defn- static-path-line! [^js ground-meshes cfg c]
  (let [[cx cz] (:at c)
        hits (t/down-hits ground-meshes cx cz 40)
        gy   (or (m/ground-height hits (:ground cfg)) 0)
        y    (+ gy 0.05)
        pts  (mapv (fn [[x z]] [x y z]) (a/circle-points (:at c) (:r c) 24))]
    (t/polyline pts (part-color :path-static))))

(defn- walker-path-lines! [w]
  (let [[x z] (:pos w) y (+ (or (:y w) 0) 0.05)
        gp    (m/goal-point w)
        aim   (or (:aim w) (:pos w))]
    {:circle (t/polyline (mapv (fn [[cx cz]] [cx y cz]) (a/circle-points [x z] (:radius w) 24))
                          (part-color :path-walker))
     :goal   (t/polyline [[x y z] [(first gp) y (second gp)]] (part-color :path-walker))
     :aim    (t/polyline [[x y z] [(first aim) y (second aim)]] (part-color :path-aim))}))

(defn- build-paths! [^js root ground-meshes cfg statics walkers]
  (let [^js paths-root (t/group {:name "pm-paths"})
        static-lines (mapv (fn [c]
                              (let [^js l (static-path-line! ground-meshes cfg c)]
                                (t/add! paths-root l)
                                l))
                            statics)
        walker-lines (mapv (fn [w]
                              (let [{:keys [circle goal aim]} (walker-path-lines! w)]
                                (t/add! paths-root circle goal aim)
                                {:circle circle :goal goal :aim aim}))
                            walkers)]
    (t/add! root paths-root)
    {:root paths-root :statics static-lines :walkers walker-lines}))

(defn- update-paths! [paths walkers]
  (when paths
    (doseq [[w lines] (map vector walkers (:walkers paths))]
      (let [[x z] (:pos w) y (+ (or (:y w) 0) 0.05)
            gp    (m/goal-point w)
            aim   (or (:aim w) (:pos w))]
        (t/set-polyline! (:circle lines) (mapv (fn [[cx cz]] [cx y cz]) (a/circle-points [x z] (:radius w) 24)))
        (t/set-polyline! (:goal lines) [[x y z] [(first gp) y (second gp)]])
        (t/set-polyline! (:aim lines) [[x y z] [(first aim) y (second aim)]])))))

;; -- glTF body loading ---------------------------------------------------------

(defn- gltf-loader!
  "One memoized GLTFLoader promise per page, created on first use."
  []
  (or @!gltf-loader
      (let [p (-> ((js/Function. "u" "return import(u)") "three/addons/loaders/GLTFLoader.js")
                  (.then (fn [^js mod] (new (.-GLTFLoader mod)))))]
        (reset! !gltf-loader p)
        p)))

(defn- load-body!
  "A promise that never rejects: #js {:scene obj}, #js {:error reason}, or
   nil when the walker names no :character."
  [w]
  (let [ch (:character w)]
    (if (nil? ch)
      (js/Promise.resolve nil)
      (-> (gltf-loader!)
          (.then (fn [^js loader] (.loadAsync loader (:file ch))))
          (.then (fn [^js gltf] #js {:scene (.-scene gltf)}))
          (.catch (fn [e] #js {:error (m/short-reason (or (.-message e) e))}))))))

;; -- start! -------------------------------------------------------------------

(defn start!
  "No-op if already started (hot reload) or there are no walkers."
  [^js h world]
  (when (and (not (:started? @!state)) (seq (m/walkers world)))
    (let [cfg          (m/config world)
          acfg         (m/avoid-config world)
          fcfg         (f/feet-config world)
          legs         (f/legs world)
          search       (.. js/window -location -search)
          feet?        (m/url-flag? search "feet")
          pcfg         {:speed (:speed cfg) :horizon-s (:horizon-s acfg)
                        :step-max (:step-max fcfg)
                        :ground-at (fn [x z]
                                     (let [gm (:ground-meshes @!state)]
                                       (when (seq gm) (m/ground-height (t/down-hits gm x z 40) (:ground cfg)))))}
          ds           (m/display-scale world)
          statics      (a/statics world)
          ^js root     (t/group {:name "pm-walkers"})
          _            (t/add! (.-scene h) root)
          ground-meshes (t/find-meshes (.-scene h) #(m/ground-name? % (:ground cfg)))
          sample       (fn [w]
                         (let [[x z] (:pos w)
                               hits  (t/down-hits ground-meshes x z 40)]
                           (m/settle (m/sense-ground w hits (:ground cfg)) 0)))
          walkers      (mapv sample (m/walkers world))
          views        (mapv #(build-view! root ds %) walkers)
          paths        (when (m/paths-flag? search)
                         (build-paths! root ground-meshes cfg statics walkers))]
      (reset! !state {:status {:mode :kinematic} :walkers walkers :views views
                       :ground-meshes ground-meshes :frame 0 :cfg cfg
                       :statics statics :pcfg pcfg :plan-acc 0 :paths paths :ds ds
                       :legs legs :fcfg fcfg :feet? feet?
                       :root root :started? true})
      (when (and (m/bricks-flag? (.. js/window -location -search)) (:bricks cfg))
        (swap! !state assoc :status {:mode :loading :progress 0})
        (try
          (-> (js/Promise.resolve nil)
              (.then (fn [_] ((js/Function. "u" "return import(u)") (:lib (:bricks cfg)))))
              (.then
                (fn [^js mod]
                  (let [b         (:bricks cfg)
                        ^js C     (.-MotionBricksCharacter mod)
                        chars     (mapv (fn [_] (C. (cond-> #js {:rig (name (:rig b)) :model (:model b) :shadows true}
                                                                         (keyword? (:backend b)) (doto (aset "backend" (name (:backend b))))))) walkers)]
                    (doseq [^js c chars]
                      (set! (.-onError c) fallback!))
                    (swap! !state update :views
                           (fn [vs] (mapv (fn [v c] (assoc v :char c)) vs chars)))
                    (let [^js first-c (first chars)
                          on-progress (fn [stage l t]
                                        (when-let [f (m/load-fraction stage l t)]
                                          (swap! !state assoc-in [:status :progress] f)))
                          model-promise (-> (.load first-c on-progress)
                                             (.then (fn [_]
                                                      (js/Promise.all
                                                        (into-array (mapv (fn [^js c] (.loadFrom c first-c)) (rest chars)))))))
                          bodies-promise (js/Promise.all (into-array (mapv load-body! walkers)))]
                      (-> (js/Promise.all #js [model-promise bodies-promise])
                          (.then
                            (fn [^js results]
                              (let [bodies   (vec (aget results 1))
                                    outcomes (mapv
                                               (fn [w ^js c r]
                                                 (let [ch (:character w)]
                                                   (cond
                                                     (nil? ch)
                                                     (do (.setMannequin c)
                                                         {:top (t/top-y (.-object c)) :error nil :rig :soma})

                                                     (and r (.-scene r))
                                                     (let [key (m/rig-key (:rig ch))
                                                           rig (aget (.-CHARACTER_RIGS mod) key)]
                                                       (if (nil? rig)
                                                         (do (.setMannequin c)
                                                             {:top (t/top-y (.-object c))
                                                              :error {:name (:name w) :reason (str "no rig " key)}
                                                              :rig :soma})
                                                         (try
                                                           (.setCharacter c (.-scene r) rig)
                                                           {:top (t/top-y (.-object c)) :error nil :rig (:rig ch)}
                                                           (catch :default e
                                                             (.setMannequin c)
                                                             {:top (t/top-y (.-object c))
                                                              :error {:name (:name w) :reason (m/short-reason (or (.-message e) e))}
                                                              :rig :soma}))))

                                                     :else
                                                     (do (.setMannequin c)
                                                         {:top (t/top-y (.-object c))
                                                          :error {:name (:name w) :reason (.-error r)}
                                                          :rig :soma}))))
                                               walkers chars bodies)
                                    tops   (mapv :top outcomes)
                                    errors (into [] (keep :error) outcomes)
                                    rigs   (mapv :rig outcomes)]
                                (swap-in! h chars tops errors rigs))))
                          (.catch (fn [e] (fallback! e))))))))
              (.catch (fn [e] (fallback! e))))
          (catch :default e (fallback! e)))))))

(defn- apply-command! [^js c w cmd]
  (when cmd
    (case (first cmd)
      :move (let [[tx tz] (second cmd)] (.moveTo c (t/vec3 [tx (:y w) tz]) "walk"))
      :stop (.stop c))))

(defn- v3- [[ax ay az] [bx by bz]] [(- ax bx) (- ay by) (- az bz)])

(defn- resolve-side [^js obj side-chain names]
  (when side-chain
    (when-let [bones (f/leg-bones side-chain names)]
      (let [^js hip   (t/by-name obj (:hip bones))
            ^js knee  (t/by-name obj (:knee bones))
            ^js ankle (t/by-name obj (:ankle bones))
            ^js toe   (when (:toe bones) (t/by-name obj (:toe bones)))]
        (when (and hip knee ankle)
          {:hip hip :knee knee :ankle ankle :toe toe})))))

(defn- build-feet-markers! [^js root]
  (let [mk (fn [color] (let [^js s (t/stand-in {:shape :sphere :size [0.06 0.06 0.06]
                                                 :color color :pos [0 0 0]})]
                          (t/add! root s)
                          s))]
    {:left  {:ground (mk (part-color :feet-ground)) :target (mk (part-color :feet-target))}
     :right {:ground (mk (part-color :feet-ground)) :target (mk (part-color :feet-target))}}))

(defn- foot-ray [gm [ax _ az] ray prefixes move]
  (if (f/needs-ray? (:xz ray) [ax az] move)
    (let [[g n] (or (f/ground-hit (t/down-hits-n gm ax az 40) prefixes) [nil nil])]
      {:xz [ax az] :g g :n n})
    ray))

(defn- solve-leg! [bones target tilt]
  (let [^js hip (:hip bones) ^js knee (:knee bones) ^js ankle (:ankle bones)
        q (t/world-quat ankle)
        H (t/world-pos hip) K (t/world-pos knee) A (t/world-pos ankle)
        pole (when (:toe bones) (v3- (t/world-pos (:toe bones)) A))
        s (f/two-bone H K A target pole)]
    (t/aim-bone! hip (v3- K H) (v3- (:knee s) H))
    (let [K2 (t/world-pos knee) A2 (t/world-pos ankle)]
      (t/aim-bone! knee (v3- A2 K2) (v3- (:ankle s) K2)))
    (t/set-world-quat! ankle q)
    (let [{:keys [axis angle]} tilt]
      (when (and (number? angle) (> angle 0) (some? axis))
        (t/tilt-bone! ankle axis angle)))))

(defn- foot-ik! [w view dt fcfg ds prefixes gm]
  (let [^js c (:char view)
        ^js obj (.-object c)
        {:keys [left right]} (:legs view)]
    (t/update-world! obj)
    (let [oy (.-y (.-position obj))
          A-left  (t/world-pos (:ankle left))
          A-right (t/world-pos (:ankle right))
          [ray0-left ray0-right] (:rays view)
          ray-left  (foot-ray gm A-left ray0-left prefixes (:ray-move fcfg))
          ray-right (foot-ray gm A-right ray0-right prefixes (:ray-move fcfg))
          input {:obj-y oy :base (:y w)
                 :feet [{:ankle A-left  :g (:g ray-left)  :n (:n ray-left)  :a0 (:a0 left)}
                        {:ankle A-right :g (:g ray-right) :n (:n ray-right) :a0 (:a0 right)}]}
          r (f/foot-frame (:feet view) input fcfg ds dt)
          [target-left target-right] (:targets r)
          [tilt-left tilt-right] (:tilts r)]
      (set! (.-y (.-position obj)) (:obj-y r))
      (t/update-world! obj)
      (solve-leg! left target-left tilt-left)
      (solve-leg! right target-right tilt-right)
      (when-let [markers (:feet-markers view)]
        (let [[alx _ alz] A-left [arx _ arz] A-right]
          (when (number? (:g ray-left))
            (t/set-pos! (:ground (:left markers)) [alx (:g ray-left) alz]))
          (t/set-pos! (:target (:left markers)) target-left)
          (when (number? (:g ray-right))
            (t/set-pos! (:ground (:right markers)) [arx (:g ray-right) arz]))
          (t/set-pos! (:target (:right markers)) target-right)))
      {:ground (:ground r) :state (:state r) :rays [ray-left ray-right]})))

(defn- swap-in!
  [^js h chars tops char-errors rigs]
  (let [{:keys [walkers views root ds legs feet?]} @!state
        views' (mapv
                 (fn [w view ^js c top rig]
                   (let [[x z] (:pos w) y (:y w)
                         ^js obj (.-object c)]
                     (t/set-scale! obj ds)
                     (t/set-pos! obj [x (or y 0) z])
                     (t/set-yaw! obj (:heading w))
                     (t/add! root obj)
                     (t/update-world! obj)
                     (t/set-visible! (:figure view) false)
                     (t/set-pos! (:label view) [0 (m/label-height top ds) 0])
                     (let [cmd (m/steer-command nil w)
                           chain (get legs rig)
                           names (t/object-names obj)
                           obj-y (or y 0)
                           left  (resolve-side obj (:left chain) names)
                           right (resolve-side obj (:right chain) names)
                           walker-legs (when (and left right)
                                         {:left  (assoc left :a0 (- (second (t/world-pos (:ankle left))) obj-y))
                                          :right (assoc right :a0 (- (second (t/world-pos (:ankle right))) obj-y))})]
                       (when (nil? walker-legs)
                         (js/console.info "Prime Meridian: no leg bones for" (:name w) "- keeping root-ray height"))
                       (apply-command! c w cmd)
                       (-> view
                           (assoc :char c :sent cmd :top top)
                           (assoc :legs walker-legs :feet (f/feet-init) :rays [nil nil])
                           (cond-> (and feet? walker-legs) (assoc :feet-markers (build-feet-markers! root)))))))
                 walkers views chars tops rigs)
        ^js first-c (first chars)
        gen-ms (.. first-c -stats -averageInferenceMs)]
    (swap! !state assoc :views views'
           :status {:mode :bricks :backend (.-backend first-c) :walkers (count chars)
                    :gen-ms (when (number? gen-ms) gen-ms)
                    :char-errors (vec char-errors)})))

(defn- fallback!
  "Ignored when the status is already :failed or :kinematic."
  [e]
  (when-not (contains? #{:failed :kinematic} (:mode (:status @!state)))
    (js/console.warn "Prime Meridian: motion bricks failed" e)
    (let [{:keys [views root ds]} @!state]
      (doseq [view views]
        (when-let [^js c (:char view)]
          (try (.dispose c) (catch :default _ nil))
          (when-let [^js obj (.-object c)]
            (t/remove! root obj)))
        (t/set-visible! (:figure view) true)
        (t/set-pos! (:label view) [0 (m/label-height nil ds) 0]))
      (swap! !state assoc
             :views (mapv #(dissoc % :char :sent) views)
             :status {:mode :failed :reason (m/short-reason (or (.-message e) e))}))))

(defn- tick-kinematic! [dt]
  (let [{:keys [walkers views statics pcfg plan-acc]} @!state
        [ws acc] (m/advance walkers statics pcfg dt plan-acc)
        ws       (mapv #(m/settle % dt) ws)]
    (doseq [[w view] (map vector ws views)]
      (let [[x z] (:pos w) y (:y w)
            ^js anchor (:anchor view) ^js fig (:figure view)]
        (t/set-pos! anchor [x (or y 0) z])
        (t/set-yaw! fig (:heading w))
        (t/set-visible! anchor (some? y))))
    (swap! !state assoc :walkers ws :plan-acc acc)))

(defn- tick-bricks! [dt]
  (try
    (let [{:keys [walkers views statics pcfg plan-acc fcfg ds cfg ground-meshes]} @!state
          before walkers
          after  (mapv (fn [w view]
                          (let [^js c (:char view)
                                ^js v (.getPosition c)
                                pos   [(.-x v) (.-z v)]]
                            (m/follow w pos (.-idle c) dt)))
                        before views)
          [ws acc] (m/replan before after statics pcfg dt plan-acc)
          ws     (mapv #(m/settle % dt) ws)
          steps  (mapv
                   (fn [w view]
                     (let [^js c (:char view)
                           cmd (m/steer-command (:sent view) w)]
                       (apply-command! c w cmd)
                       (.update c dt)
                       (let [[x z] (:pos w) y (:y w)
                             ^js obj (.-object c)]
                         (if (:legs view)
                           (let [res (foot-ik! w view dt fcfg ds (:ground cfg) ground-meshes)]
                             (t/set-pos! (:anchor view) [x (or y 0) z])
                             {:ground (:ground res)
                              :view (-> view
                                        (assoc :feet (:state res) :rays (:rays res))
                                        (cond-> cmd (assoc :sent cmd)))})
                           (do
                             (set! (.-y (.-position obj)) y)
                             (t/set-pos! (:anchor view) [x (or y 0) z])
                             {:ground nil :view (cond-> view cmd (assoc :sent cmd))})))))
                   ws views)
          ws'    (mapv (fn [w step] (if-let [g (:ground step)] (assoc w :ground g) w)) ws steps)
          views' (mapv :view steps)]
      (swap! !state assoc :walkers ws' :views views' :plan-acc acc)
      (when-let [^js c0 (:char (first views'))]
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
        (let [{:keys [views status]} @!state
              bricks? (= :bricks (:mode status))]
          (swap! !state update :walkers
                 (fn [walkers]
                   (vec (map-indexed
                          (fn [i w]
                            (let [view (get views i)]
                              (if (and (= (mod frame 4) (mod i 4))
                                       (not (and bricks? (:legs view))))
                                (let [[x z] (:pos w)]
                                  (m/sense-ground w (t/down-hits ground-meshes x z 40) (:ground cfg)))
                                w)))
                          walkers))))))
      (if (= :bricks (:mode (:status @!state)))
        (tick-bricks! dt)
        (tick-kinematic! dt))
      (swap! !state update :status assoc :detours (m/detours (:walkers @!state)))
      (update-paths! (:paths @!state) (:walkers @!state))
      (swap! !state update :frame inc))))

(defn hud-segment []
  (m/hud-text (:status @!state)))

(defn status []
  (:status @!state))
