(ns pm.creature-spike
  "Interop for the flag-gated creature spike: MuJoCo WASM load/step/
   delete, and the body's three.js parts through pm.three. No rules live
   here — pm.creature and pm.creature-motion decide; this namespace only
   builds and moves objects to match MjData."
  (:require [pm.three :as t]
            [pm.plan :as p]
            [pm.creature :as c]
            [pm.creature-motion :as cm]
            [pm.species :as species]))

(defonce !state (atom {:status nil :started? false}))

(defn fail! [e]
  (let [{:keys [data model root status]} @!state]
    (when data  (try (.delete data)  (catch :default _ nil)))
    (when model (try (.delete model) (catch :default _ nil)))
    (when root
      (when-let [^js parent (.-parent root)]
        (.remove parent root)))
    (js/console.warn "Prime Meridian: creature spike failed" e)
    (swap! !state
           (fn [s]
             (-> s
                 (dissoc :model :data)
                 (assoc :status {:mode :failed :flag (:flag status) :kind (:kind status)
                                  :reason (or (.-message e) (str e))}))))))

(defn default-loader
  "The browser's runtime import of the \"mujoco\" alias (public/index.html's
   import map). A promise of the module, same shape `.default` expects."
  []
  ((js/Function. "u" "return import(u)") "mujoco"))

(defn start!
  "`load-mujoco` is a no-arg fn returning a promise of the mujoco module
   (`.default` on it inits the WASM and resolves `mj`); defaults to the
   browser's dynamic import. A test harness without one (Node's `import()`
   needs a dynamic-import callback the test runner's vm context lacks) can
   pass its own, e.g. `#(js/Promise.resolve (js/require \"@mujoco/mujoco\"))`."
  ([h world] (start! h world default-loader))
  ([^js h world load-mujoco]
   (when-not (:started? @!state)
     (when-let [req (cm/spike-request (.. js/window -location -search) (cm/spike-config world))]
       (let [kind (:kind req) flag (:flag req)]
         (swap! !state assoc :started? true :status {:mode :loading :flag flag :kind kind})
         (if-let [text (get species/texts kind)]
           (try
             (let [sp       (c/parse text)
                   xml      (c/mjcf sp req)
                   ranges   (c/ctrl-ranges sp)
                   sw       (c/spawn sp req)
                   ground-z (nth (:ground sw) 2)]
               (-> (load-mujoco)
                   (.then (fn [^js mod] (.default mod)))
                   (.then (fn [^js mj]
                            (let [^js model (.from_xml_string (.-MjModel mj) xml)
                                  ^js data  (new (.-MjData mj) model)
                                  ^js root  (t/group {:name "pm-creature"})
                                  groups    (mapv (fn [{bname :name part :part}]
                                                     (let [^js g (t/group {:name (str "pm-creature/" (name bname))})]
                                                       (t/add! g (t/stand-in {:shape (:shape part) :size (:size part)
                                                                               :color (:color part) :pos (:at part)
                                                                               :rot (mapv p/radians (:rot part))}))
                                                       (t/add! root g)
                                                       g))
                                                   (c/body-parts sp))]
                              (t/add! (.-scene h) root)
                              (swap! !state assoc
                                     :mj mj :model model :data data :sp sp :ranges ranges
                                     :ground-z ground-z
                                     :ctrl (cm/controller :twitch (count ranges) (:seed req))
                                     :acc 0 :stats cm/empty-stats :root root :groups groups
                                     :status {:mode :running :flag flag :kind kind
                                               :stats cm/empty-stats :torso nil}))))
                   (.catch (fn [e] (fail! e)))))
             (catch :default e (fail! e)))
           (fail! (js/Error. (str "no species " kind)))))))))

(defn tick! [dt now-ms]
  (when (= :running (:mode (:status @!state)))
    (try
      (let [{:keys [ranges ctrl ground-z groups stats status]} @!state
            ^js mj    (:mj @!state)
            ^js model (:model @!state)
            ^js data  (:data @!state)
            {:keys [steps dropped acc]} (cm/fixed-steps (:acc @!state) dt)
            t0    (js/performance.now)
            ctrl' (loop [c' ctrl i 0]
                    (if (>= i steps)
                      c'
                      (let [[c2 actions] (cm/act c' nil c/timestep)
                            ctrl-vals    (cm/actions->ctrl actions ranges)
                            ^js dctrl    (.-ctrl data)]
                        (doseq [[idx v] (map-indexed vector ctrl-vals)] (aset dctrl idx v))
                        (.mj_step mj model data)
                        (recur c2 (inc i)))))
            t1     (js/performance.now)
            ^js xpos  (.-xpos data)
            ^js xquat (.-xquat data)
            torso  (- (aget xpos 5) ground-z)
            stats' (cm/stats-tick stats now-ms (- t1 t0) steps dropped)]
        (dotimes [i (count groups)]
          (let [{:keys [pos quat]} (cm/body-pose xpos xquat (inc i))]
            (t/set-pos! (nth groups i) pos)
            (t/set-quat! (nth groups i) quat)))
        (swap! !state assoc :ctrl ctrl' :acc acc :stats stats'
               :status {:mode :running :flag (:flag status) :kind (:kind status)
                        :stats stats' :torso torso}))
      (catch :default e (fail! e)))))

(defn hud-segment []
  (cm/hud-text (:status @!state)))
