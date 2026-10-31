(ns pm.brook
  "Sound you hear *in* the world: each sound in the estate's `:sounds` is a
   recorded loop played from positional emitters pinned to declared points
   (for the creek: along its centerline at the water surface, loudest at the
   cascades). The listener rides the camera, so a sound gets louder and pans as
   you walk toward it. Pitch follows flow (rate = 0.3 + 0.9 * flow).
   Uses the kit's own THREE (from its handle), so the audio objects live in the
   kit's scene graph. With no `:sounds` declared, falls back to spreading
   emitters along any mesh whose name says water."
  (:require [clojure.string :as str]))

(defonce !state (atom {:listener nil :emitters [] :buffers {} :flow 0.33 :playing? false}))

(def fallback-loop "/assets/audio/brook.mp3")

(defn- rate [flow] (+ 0.3 (* 0.9 flow)))

(defn- water-meshes [^js scene]
  (let [out #js []]
    (.traverse scene
               (fn [^js o]
                 (let [mat (.-material o)
                       n   (.toLowerCase (str (.-name o) " " (when mat (.-name mat))))]
                   (when (and (.-isMesh o)
                              (re-find #"water|creek|stream|river|pool|brook" n)
                              (not (re-find #"masonry|soil|gravel|stone|roof|bank" n)))
                     (.push out o)))))
    (vec out)))

(defn- fallback-points
  "No declared sounds: 1-5 points along each water mesh's long axis."
  [^js h]
  (let [T (.-THREE h) Box3 (.-Box3 T) Vector3 (.-Vector3 T)]
    (for [^js m (water-meshes (.-scene h))
          :let [^js box (.setFromObject (Box3.) m)]
          :when (not (.isEmpty box))
          :let [^js size (.getSize box (Vector3.)) ^js c (.getCenter box (Vector3.))
                along-x (>= (.-x size) (.-z size))
                span (if along-x (.-x size) (.-z size))
                n (max 1 (min 5 (js/Math.round (/ span 6))))]
          i (range n)
          :let [t (if (= n 1) 0 (- (/ i (dec n)) 0.5))]]
      [(+ (.-x c) (if along-x (* t span 0.8) 0)) (.-y c)
       (+ (.-z c) (if along-x 0 (* t span 0.8))) 1.0])))

(defn- setup!
  "Listener on the camera; one positional emitter per declared point."
  [^js h sounds]
  (let [T (.-THREE h) ^js scene (.-scene h) ^js camera (.-camera h)
        AudioListener (.-AudioListener T) PositionalAudio (.-PositionalAudio T)
        ^js listener (AudioListener.)
        sounds (if (seq sounds)
                 sounds
                 [{:id :water :loop fallback-loop :points (vec (fallback-points h))}])
        emitters (vec
                  (for [{:keys [id loop points ref-distance rolloff]
                         :or {ref-distance 2.5 rolloff 1.6}} sounds
                        [x y z gain] points
                        :let [^js a (PositionalAudio. listener)]]
                    (do (.setRefDistance a ref-distance) (.setRolloffFactor a rolloff)
                        (.setDistanceModel a "inverse") (.setMaxDistance a 80)
                        (.set (.-position a) x y z)
                        (.add scene a)
                        {:audio a :sound id :loop loop :gain (or gain 1)})))]
    (.add camera listener)
    (swap! !state assoc :listener listener :emitters emitters
           :sounds (mapv :id sounds))
    (js/console.log "Prime Meridian: sound emitters" (count emitters) "for" (pr-str (mapv :id sounds)))))

(defn- with-buffer! [url f]
  (if-let [b (get-in @!state [:buffers url])]
    (f b)
    (let [^js ctx (.-context ^js (:listener @!state))]
      (-> (js/fetch url)
          (.then #(.arrayBuffer ^js %))
          (.then #(.decodeAudioData ctx %))
          (.then (fn [b] (swap! !state assoc-in [:buffers url] b) (f b)))))))

(defn play!
  "Start every declared sound (call from a click: browsers need a gesture).
   `sounds` is the estate's `:sounds` vector."
  [h sounds]
  (when-not (:listener @!state) (setup! h sounds))
  (let [^js ctx (.-context ^js (:listener @!state))]
    (when (= "suspended" (.-state ctx)) (.resume ctx)))
  (let [es (:emitters @!state)
        n  (count es)]
    (doseq [[i {:keys [^js audio loop gain]}] (map-indexed vector es)
            :when (not (.-isPlaying audio))]
      (with-buffer! loop
        (fn [^js b]
          (.setBuffer audio b) (.setLoop audio true)
          (.setPlaybackRate audio (rate (:flow @!state)))
          ;; spread the level so many emitters don't sum to a roar
          (.setVolume audio (/ gain (js/Math.sqrt (max 1 (/ n 3)))))
          (set! (.-offset audio) (mod (* i 7.3) (.-duration b)))
          (.play audio))))
    (swap! !state assoc :playing? true)))

(defn stop! []
  (doseq [{:keys [^js audio]} (:emitters @!state) :when (.-isPlaying audio)] (.stop audio))
  (swap! !state assoc :playing? false))

(defn playing? [] (:playing? @!state))

(defn set-flow!
  "0..1: faster, brighter water as flow rises (pitch follows speed)."
  [flow]
  (swap! !state assoc :flow flow)
  (doseq [{:keys [^js audio]} (:emitters @!state)] (.setPlaybackRate audio (rate flow))))

(defn status
  "One line for the HUD: emitters, sounds, and the nearest emitter's distance
   from the camera (what you should hear). nil before the first play."
  [^js h]
  (let [{:keys [emitters sounds playing?]} @!state]
    (when (seq emitters)
      (let [^js cam (.-camera h)
            Vector3 (.. h -THREE -Vector3)
            ^js cp  (.getWorldPosition cam (Vector3.))
            near    (apply min (map (fn [{:keys [^js audio]}] (.distanceTo (.-position audio) cp)) emitters))]
        (str "sound " (if playing? "on" "off") ": " (count emitters) " emitters ("
             (str/join ", " (map name sounds)) ") · nearest " (.toFixed near 1) " m")))))
