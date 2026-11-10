(ns pm.creature-spike
  "Interop for the flag-gated creature spike: MuJoCo WASM load/step/
   delete, onnxruntime-web inference, and the body's three.js parts
   through pm.three. No rules live here — pm.creature, pm.creature-motion
   and pm.creature-brain decide; this namespace only builds and moves
   objects to match MjData, and drives the ONNX session."
  (:require [pm.three :as t]
            [pm.plan :as p]
            [pm.creature :as c]
            [pm.creature-motion :as cm]
            [pm.creature-brain :as cb]
            [pm.species :as species]
            [pm.ui :as ui]))

(defonce !state (atom {:status nil :started? false}))

(declare run-brain! drop-again! set-age!)

(defn- release-session! [^js s]
  (when s (try (.release s) (catch :default _ nil))))

(defn fail! [e]
  (let [{:keys [data model root status jacs session]} @!state]
    (doseq [[^js jp ^js jr] jacs]
      (try (.delete jp) (catch :default _ nil))
      (try (.delete jr) (catch :default _ nil)))
    (when data  (try (.delete data)  (catch :default _ nil)))
    (when model (try (.delete model) (catch :default _ nil)))
    (release-session! session)
    (when root
      (when-let [^js parent (.-parent root)]
        (.remove parent root)))
    (js/console.warn "Prime Meridian: creature spike failed" e)
    (swap! !state
           (fn [s]
             (-> s
                 (dissoc :model :data :jacs :session)
                 (assoc :status {:mode :failed :flag (:flag status) :kind (:kind status)
                                  :reason (or (.-message e) (str e))}))))))

(defn default-loader
  "The browser's runtime import of the \"mujoco\" alias (public/index.html's
   import map). A promise of the module, same shape `.default` expects."
  []
  ((js/Function. "u" "return import(u)") "mujoco"))

(defn default-ort-loader
  "The browser's runtime import of the \"onnxruntime-web\" alias. A promise
   of the ort module itself (no `.default`)."
  []
  ((js/Function. "u" "return import(u)") "onnxruntime-web"))

(defn- create-session! [^js ort br age]
  (let [files (cb/age-files br age)]
    (.create (.-InferenceSession ort) (:model files)
              #js{:executionProviders #js["wasm"]
                  :externalData #js[#js{:path (:data-path files) :data (:data files)}]})))

(defn- place-groups! [groups xpos xquat]
  (dotimes [i (count groups)]
    (let [{:keys [pos quat]} (cm/body-pose xpos xquat (inc i))]
      (t/set-pos! (nth groups i) pos)
      (t/set-quat! (nth groups i) quat))))

(defn- init-groups!
  "Builds the body's three.js groups and places them from `data`'s
   just-forward-kinematic'd pose before the root ever enters the scene:
   xpos/xquat read as all zeros right after `new MjData`, so without this
   the creature would flash at the world origin for a frame."
  [^js h sp ^js data]
  (let [^js root (t/group {:name "pm-creature"})
        groups   (mapv (fn [{bname :name part :part}]
                          (let [^js g (t/group {:name (str "pm-creature/" (name bname))})]
                            (t/add! g (t/stand-in {:shape (:shape part) :size (:size part)
                                                    :color (:color part) :pos (:at part)
                                                    :rot (mapv p/radians (:rot part))}))
                            (t/add! root g)
                            g))
                        (c/body-parts sp))
        ^js xpos0  (.-xpos data)
        ^js xquat0 (.-xquat data)]
    (place-groups! groups xpos0 xquat0)
    (t/add! (.-scene h) root)
    [root groups]))

(defn- start-twitch! [^js h req sp br flag kind load-mujoco]
  (let [xml      (c/mjcf sp req)
        ranges   (c/ctrl-ranges sp)
        sw       (c/spawn sp req)
        ground-z (nth (:ground sw) 2)]
    (-> (load-mujoco)
        (.then (fn [^js mod] (.default mod)))
        (.then (fn [^js mj]
                 (let [^js model (.from_xml_string (.-MjModel mj) xml)
                       ^js data  (new (.-MjData mj) model)
                       _         (.mj_forward mj model data)
                       [root groups] (init-groups! h sp data)
                       rx   (c/reflexes sp)
                       nv   (.-nv model)
                       tw   (cm/controller :twitch (count ranges) (:seed req))
                       jacs (mapv (fn [_]
                                    [(new (.-DoubleBuffer mj) (* 3 nv))
                                     (new (.-DoubleBuffer mj) (* 3 nv))])
                                  rx)]
                   (swap! !state assoc
                          :mj mj :model model :data data :sp sp :ranges ranges
                          :ground-z ground-z
                          :rx rx :nv nv :jacs jacs
                          :rest-frac (c/rest-fraction sp) :kick (c/kick-above sp)
                          :ctrl (if (seq rx) (cm/reflex-controller tw rx (:seed req)) tw)
                          :acc 0 :stats cm/empty-stats :root root :groups groups
                          :status {:mode :running :flag flag :kind kind
                                    :stats cm/empty-stats :torso nil :brain br}))))
        (.catch (fn [e] (fail! e))))))

(defn- start-policy! [^js h req sp br flag kind load-mujoco load-ort]
  (-> (js/Promise.all
        #js[(-> (load-mujoco) (.then (fn [^js mod] (.default mod))))
            (-> (js/fetch (:meta-url br)) (.then (fn [^js resp] (.text resp))))
            (load-ort)])
      (.then (fn [^js results]
               (let [mj          (aget results 0)
                     text        (aget results 1)
                     ^js ort     (aget results 2)
                     m           (cb/parse-meta text)
                     joint-names (mapv (comp name :name) (c/joints sp))
                     pol         (cb/meta->policy m joint-names)]
                 (set! (.. ort -env -wasm -numThreads) 1)
                 (set! (.. ort -env -wasm -wasmPaths) "/vendor/onnxruntime/")
                 (-> (create-session! ort br (:age br))
                     (.then (fn [^js session]
                              (swap! !state assoc :session session :ort ort)
                              [mj pol session]))))))
      (.then (fn [[^js mj pol session]]
               (let [xml       (c/mjcf sp req)
                     ranges    (c/ctrl-ranges sp)
                     sw        (c/spawn sp req)
                     ground-z  (nth (:ground sw) 2)
                     ^js model (.from_xml_string (.-MjModel mj) xml)
                     ^js data  (new (.-MjData mj) model)
                     probs     (cb/address-problems (:n pol) (.-jnt_qposadr model)
                                                     (.-jnt_dofadr model) (.-actuator_trnid model))]
                 (when (seq probs) (throw (js/Error. (first probs))))
                 (let [^js qpos (.-qpos data)
                       pose     (cb/drop-pose (cb/drop-seed (:seed req) 0) ranges (:drop-m br) (:ground sw))]
                   (dotimes [i (count pose)] (aset qpos i (nth pose i))))
                 (.mj_forward mj model data)
                 (let [[root groups] (init-groups! h sp data)]
                   (swap! !state assoc
                          :mj mj :model model :data data :sp sp :ranges ranges
                          :ground-z ground-z :ground-xyz (:ground sw)
                          :br br :policy pol
                          :sched (cb/schedule-init (:decim pol) (:n pol))
                          :try 0 :seed (:seed req) :age (:age br) :loading-age nil
                          :acc 0 :stats cm/empty-stats :root root :groups groups
                          :status {:mode :running :flag flag :kind kind
                                    :stats cm/empty-stats :torso nil
                                    :brain {:kind :policy :age (:age br) :up? false}})
                   (ui/show-brain-ui! (:ages br) (:age br) set-age! drop-again!)))))
      (.catch (fn [e] (fail! e)))))

(defn start!
  "`load-mujoco` is a no-arg fn returning a promise of the mujoco module
   (`.default` on it inits the WASM and resolves `mj`); defaults to the
   browser's dynamic import. `load-ort` is a no-arg fn returning a promise
   of the onnxruntime-web module; defaults to the browser's dynamic import.
   A test harness without one (Node's `import()` needs a dynamic-import
   callback the test runner's vm context lacks) can pass its own, e.g.
   `#(js/Promise.resolve (js/require \"@mujoco/mujoco\"))`."
  ([h world] (start! h world default-loader))
  ([h world load-mujoco] (start! h world load-mujoco default-ort-loader))
  ([^js h world load-mujoco load-ort]
   (when-not (:started? @!state)
     (when-let [req (cm/spike-request (.. js/window -location -search) (cm/spike-config world))]
       (let [kind (:kind req) flag (:flag req)]
         (swap! !state assoc :started? true :status {:mode :loading :flag flag :kind kind})
         (if-let [text (get species/texts kind)]
           (try
             (let [sp (c/parse text)
                   br (cb/brain-request sp (.. js/window -location -search))]
               (if (= :policy (:kind br))
                 (start-policy! h req sp br flag kind load-mujoco load-ort)
                 (start-twitch! h req sp br flag kind load-mujoco)))
             (catch :default e (fail! e)))
           (fail! (js/Error. (str "no species " kind)))))))))

(defn- run-brain! []
  (let [{:keys [sched ^js mj ^js model ^js data policy status]} @!state]
    (when (= :running (:mode status))
      (let [{:keys [st ops action]} (cb/plan sched)
            ^js dctrl (.-ctrl data)]
        (doseq [op ops]
          (case op
            :apply (let [ctrl-vals (cb/action->ctrl policy action)]
                     (dotimes [i (count ctrl-vals)] (aset dctrl i (nth ctrl-vals i))))
            :step  (.mj_step mj model data)
            :ask   nil))
        (swap! !state assoc :sched st)
        (when (= :ask (last ops))
          (let [gen       (:gen st)
                ^js qpos  (.-qpos data)
                ^js qvel  (.-qvel data)
                ^js xmat  (.-xmat data)
                obs       (cb/observation (:n policy) qpos qvel xmat (:last-action st))
                ^js ort   (:ort @!state)
                tensor    (new (.-Tensor ort) "float32" (js/Float32Array.from (clj->js obs)) #js[1 (count obs)])
                ^js session (:session @!state)]
            (-> (.run session (js-obj (:input policy) tensor))
                (.then (fn [^js out]
                         (when (= :running (:mode (:status @!state)))
                           (let [action' (vec (.-data (aget out (:output policy))))]
                             (swap! !state update :sched cb/receive gen action')
                             (run-brain!)))))
                (.catch (fn [e] (fail! e))))))))))

(defn drop-again! []
  (let [{:keys [^js mj ^js model ^js data ranges br ground-xyz seed try]} @!state
        try'  (inc (or try 0))
        seed' (or seed 1)]
    (.mj_resetData mj model data)
    (let [^js qpos (.-qpos data)
          pose     (cb/drop-pose (cb/drop-seed seed' try') ranges (:drop-m br) ground-xyz)]
      (dotimes [i (count pose)] (aset qpos i (nth pose i))))
    (.mj_forward mj model data)
    (swap! !state assoc :try try' :sched (cb/restart (:sched @!state)))))

(defn set-age! [age]
  (swap! !state assoc :loading-age age)
  (let [{:keys [ort br session]} @!state]
    (-> (create-session! ort br age)
        (.then (fn [^js new-session]
                 (release-session! session)
                 (swap! !state assoc :session new-session :age age :loading-age nil)
                 (drop-again!)))
        (.catch (fn [e]
                  (swap! !state assoc :loading-age nil)
                  (js/console.warn "Prime Meridian: brain age load failed" e))))))

(defn- tick-twitch! [dt now-ms]
  (let [{:keys [ranges ctrl ground-z groups stats status rx jacs nv rest-frac kick]} @!state
        ^js mj    (:mj @!state)
        ^js model (:model @!state)
        ^js data  (:data @!state)
        {:keys [steps dropped acc]} (cm/fixed-steps (:acc @!state) dt)
        t0    (js/performance.now)
        [ctrl' s0]
        (loop [c' ctrl i 0 s0 nil]
          (if (>= i steps)
            [c' s0]
            (let [obs (when (seq jacs)
                        (doseq [[r [^js jp ^js jr]] (map vector rx jacs)]
                          (.mj_jacBody mj model data jp jr (:body-id r)))
                        {:nv nv :qpos (.-qpos data) :forces (.-actuator_force data)
                         :senses (mapv (fn [r [^js jp ^js jr]]
                                         (cm/sense r (.-xpos data) (.-xmat data)
                                                   (.GetView jp) (.GetView jr) ground-z))
                                       rx jacs)})
                  [c2 actions] (cm/act c' obs c/timestep)
                  tg           (cm/actions->ctrl actions ranges)
                  ctrl-vals    (cm/limp tg (.-qpos data) (cm/stiffness c2 rest-frac kick))
                  ^js dctrl    (.-ctrl data)]
              (doseq [[idx v] (map-indexed vector ctrl-vals)] (aset dctrl idx v))
              (.mj_step mj model data)
              (recur c2 (inc i) (when obs (first (:senses obs)))))))
        t1     (js/performance.now)
        ^js xpos  (.-xpos data)
        ^js xquat (.-xquat data)
        torso  (- (aget xpos 5) ground-z)
        stats' (cm/stats-tick stats now-ms (- t1 t0) steps dropped)
        head   (when s0 {:clearance (:clearance s0) :tilt (cm/tilt-deg (:up s0))})
        st0    (first (:efforts ctrl'))
        effort (when (and st0 s0) {:energy (:energy st0) :label (cm/effort-label st0 (first rx) s0)})]
    (place-groups! groups xpos xquat)
    (swap! !state assoc :ctrl ctrl' :acc acc :stats stats'
           :status {:mode :running :flag (:flag status) :kind (:kind status)
                    :stats stats' :torso torso :head head :effort effort :brain (:brain status)})))

(defn- tick-policy! [dt now-ms]
  (let [{:keys [sched ground-z groups stats status br]} @!state
        t0    (js/performance.now)
        {:keys [steps dropped acc]} (cm/fixed-steps (:acc @!state) dt)
        [sched2 d2] (cb/add-owed sched steps cm/max-steps)]
    (swap! !state assoc :sched sched2 :acc acc)
    (run-brain!)
    (let [^js data  (:data @!state)
          t1        (js/performance.now)
          ^js xpos  (.-xpos data)
          ^js xquat (.-xquat data)
          ^js xmat  (.-xmat data)
          torso     (- (aget xpos 5) ground-z)
          stats'    (cm/stats-tick stats now-ms (- t1 t0) steps (+ dropped d2))
          up?       (cb/up? (:stand br) ground-z xpos xmat)]
      (place-groups! groups xpos xquat)
      (swap! !state assoc :stats stats'
             :status {:mode :running :flag (:flag status) :kind (:kind status)
                      :stats stats' :torso torso
                      :brain (if-let [la (:loading-age @!state)]
                               {:kind :policy :age la :loading? true}
                               {:kind :policy :age (:age @!state) :up? up?})}))))

(defn tick! [dt now-ms]
  (when (= :running (:mode (:status @!state)))
    (try
      (if (:policy @!state)
        (tick-policy! dt now-ms)
        (tick-twitch! dt now-ms))
      (catch :default e (fail! e)))))

(defn hud-segment []
  (let [s (:status @!state)
        t (cm/hud-text s)]
    (if (and t (= :running (:mode s)))
      (str t (cb/hud-segment (:brain s)))
      t)))
