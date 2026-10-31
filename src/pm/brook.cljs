(ns pm.brook
  "The brook you hear *in* the world: a recorded creek loop played from several
   positional emitters spread along the kit's water meshes, with the listener on
   the camera, so it gets louder and pans as you move toward the water. Pitch
   follows flow (rate = 0.3 + 0.9 * flow). Uses the kit's own THREE (from its
   handle), so the audio objects live in the kit's scene graph.

   Ported from the inline script that used to sit in the kit's private index.")

(defonce !state (atom {:listener nil :emitters [] :buffer nil :flow 0.33 :playing? false}))

(def loop-url "/assets/audio/brook.mp3")

(defn- rate [flow] (+ 0.3 (* 0.9 flow)))

(defn- water-meshes [^js scene]
  (let [out #js []]
    (.traverse scene
               (fn [^js o]
                 (let [mat (.-material o)
                       n   (.toLowerCase (str (.-name o) " " (when mat (.-name mat))))]
                   (when (and (.-isMesh o) (re-find #"water|creek|stream|river|pool|canal|brook" n))
                     (.push out o)))))
    (vec out)))

(defn- setup!
  "Listener on the camera; 1-5 emitters along each water mesh's long axis."
  [^js h]
  (let [T (.-THREE h) ^js scene (.-scene h) ^js camera (.-camera h)
        AudioListener (.-AudioListener T) PositionalAudio (.-PositionalAudio T)
        Box3 (.-Box3 T) Vector3 (.-Vector3 T)
        ^js listener (AudioListener.)
        meshes (let [m (water-meshes scene)] (if (seq m) m [scene]))
        emitters (vec
                  (for [^js m meshes
                        :let [^js box (.setFromObject (Box3.) m)]
                        :when (not (.isEmpty box))
                        :let [^js size (.getSize box (Vector3.)) ^js c (.getCenter box (Vector3.))
                              along-x (>= (.-x size) (.-z size))
                              span (if along-x (.-x size) (.-z size))
                              n (max 1 (min 5 (js/Math.round (/ span 6))))]
                        i (range n)
                        :let [t (if (= n 1) 0 (- (/ i (dec n)) 0.5))
                              ^js pos (.clone c)
                              ^js a (PositionalAudio. listener)]]
                    (do (if along-x
                          (set! (.-x pos) (+ (.-x pos) (* t span 0.8)))
                          (set! (.-z pos) (+ (.-z pos) (* t span 0.8))))
                        (.setRefDistance a 2.5) (.setRolloffFactor a 1.6)
                        (.setDistanceModel a "inverse") (.setMaxDistance a 80)
                        (.copy (.-position a) pos)
                        (.add scene a)
                        a)))]
    (.add camera listener)
    (swap! !state assoc :listener listener :emitters emitters)
    (js/console.log "Prime Meridian: brook emitters" (count emitters) "on" (count meshes) "water mesh(es)")))

(defn- with-buffer! [f]
  (if-let [b (:buffer @!state)]
    (f b)
    (let [^js ctx (.-context ^js (:listener @!state))]
      (-> (js/fetch loop-url)
          (.then #(.arrayBuffer ^js %))
          (.then #(.decodeAudioData ctx %))
          (.then (fn [b] (swap! !state assoc :buffer b) (f b)))))))

(defn play!
  "Start the brook (call from a click: browsers need a gesture for audio)."
  [h]
  (when-not (:listener @!state) (setup! h))
  (let [^js ctx (.-context ^js (:listener @!state))]
    (when (= "suspended" (.-state ctx)) (.resume ctx)))
  (with-buffer!
    (fn [^js b]
      (let [es (:emitters @!state) r (rate (:flow @!state))]
        (doseq [[i ^js a] (map-indexed vector es)
                :when (not (.-isPlaying a))]
          (.setBuffer a b) (.setLoop a true) (.setPlaybackRate a r)
          (.setVolume a (/ 1 (js/Math.sqrt (count es))))
          (set! (.-offset a) (mod (* i 7.3) (.-duration b)))
          (.play a)))
      (swap! !state assoc :playing? true))))

(defn stop! []
  (doseq [^js a (:emitters @!state) :when (.-isPlaying a)] (.stop a))
  (swap! !state assoc :playing? false))

(defn playing? [] (:playing? @!state))

(defn set-flow!
  "0..1: louder-sounding, faster water as flow rises (pitch follows speed)."
  [flow]
  (swap! !state assoc :flow flow)
  (doseq [^js a (:emitters @!state)] (.setPlaybackRate a (rate flow))))
