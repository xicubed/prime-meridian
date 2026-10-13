(ns pm.audio
  "Procedural brook via Web Audio. What a creek actually sounds like from the bank:
   mostly broadband *hiss* (water over stones, 1-6 kHz), a quieter mid 'body', both
   slowly and randomly drifting in level; on top, sparse irregular *plips* — short
   filtered-noise bursts with a tiny rising chirp. No low rumble, no periodic LFOs,
   no clean sines (those read as engines and drum machines).
   Browsers only start audio after a user gesture, so call `start!` from a click.

   Knobs: :flow 0..1 (level + plip density), :depth 0..1 (0 = bright shallow trickle,
   1 = darker, fuller rush)."
  )

(defonce !ctx (atom nil))
(defonce !nodes (atom nil))
(defonce !params (atom {:flow 0.6 :depth 0.4}))
(defonce !timers (atom []))

(defn- white-noise-buffer [^js ctx seconds]
  (let [sr (.-sampleRate ctx) n (* sr seconds)
        buf (.createBuffer ctx 1 n sr) data (.getChannelData buf 0)]
    (dotimes [i n] (aset data i (- (* 2 (js/Math.random)) 1)))
    buf))

(defn- noise-source [^js ctx buf]
  (let [src (.createBufferSource ctx)]
    (set! (.-buffer src) buf) (set! (.-loop src) true)
    (set! (.-value (.-playbackRate src)) (+ 0.9 (* 0.2 (js/Math.random))))
    src))

(defn- band [^js ctx freq q]
  (let [f (.createBiquadFilter ctx)]
    (set! (.-type f) "bandpass")
    (set! (.-value (.-frequency f)) freq)
    (set! (.-value (.-Q f)) q)
    f))

(defn- gain [^js ctx v]
  (let [g (.createGain ctx)] (set! (.-value (.-gain g)) v) g))

(defn- later! [ms f]
  (swap! !timers conj (js/setTimeout f ms)))

(defn- drift!
  "Random-walk a param: every 120-400 ms pick a new nearby target and glide to it.
   This is what makes water sound alive without sounding cyclic."
  [^js ctx ^js param base spread]
  (letfn [(step []
            (let [now (.-currentTime ctx)
                  target (* base (+ 1 (* spread (- (* 2 (js/Math.random)) 1))))]
              (.setTargetAtTime param (max 0.0001 target) now 0.18)
              (later! (+ 120 (* 280 (js/Math.random))) step)))]
    (step)))

(defn- plip!
  "One droplet heard from above the surface: a 15-40 ms noise burst through a narrow
   band at 1.5-4 kHz, plus a very quiet rising chirp. Level varies a lot — most are tiny."
  [^js ctx ^js noise-buf ^js out]
  (let [t0   (.-currentTime ctx)
        dur  (+ 0.015 (* 0.025 (js/Math.random)))
        lvl  (* 0.5 (js/Math.pow (js/Math.random) 2.2))     ; mostly quiet, occasionally a pop
        src  (noise-source ctx noise-buf)
        bp   (band ctx (+ 1500 (* 2500 (js/Math.random))) 6)
        g    (gain ctx 0.0001)
        osc  (.createOscillator ctx)
        og   (gain ctx 0.0001)
        f0   (+ 900 (* 900 (js/Math.random)))]
    (.connect src bp) (.connect bp g) (.connect g out)
    (.setValueAtTime (.-gain g) 0.0001 t0)
    (.exponentialRampToValueAtTime (.-gain g) (max 0.001 lvl) (+ t0 0.004))
    (.exponentialRampToValueAtTime (.-gain g) 0.0001 (+ t0 dur))
    (.start src t0) (.stop src (+ t0 dur 0.01))
    ;; chirp: rising, buried under the burst
    (set! (.-type osc) "triangle")
    (.setValueAtTime (.-frequency osc) f0 t0)
    (.exponentialRampToValueAtTime (.-frequency osc) (* f0 1.6) (+ t0 0.03))
    (.connect osc og) (.connect og out)
    (.setValueAtTime (.-gain og) 0.0001 t0)
    (.exponentialRampToValueAtTime (.-gain og) (max 0.001 (* lvl 0.12)) (+ t0 0.005))
    (.exponentialRampToValueAtTime (.-gain og) 0.0001 (+ t0 0.035))
    (.start osc t0) (.stop osc (+ t0 0.05))))

(defn- schedule-plips!
  "Poisson-ish timing: exponential gaps, with occasional short clusters."
  [^js ctx noise-buf out]
  (letfn [(tick []
            (let [{:keys [flow]} @!params
                  mean-gap (/ 420 (+ 0.15 flow))                  ; ms
                  gap (* (- mean-gap) (js/Math.log (- 1 (js/Math.random))))]
              (plip! ctx noise-buf out)
              (when (< (js/Math.random) 0.25)                     ; cluster
                (dotimes [i (+ 1 (rand-int 3))]
                  (later! (* (inc i) (+ 25 (* 40 (js/Math.random)))) #(plip! ctx noise-buf out))))
              (later! (min 2500 gap) tick)))]
    (tick)))

(defn apply-params! []
  (when-let [{:keys [^js hiss-g ^js body-g ^js hiss-f ^js master]} @!nodes]
    (let [{:keys [flow depth]} @!params
          ^js ctx @!ctx now (.-currentTime ctx)]
      (.setTargetAtTime (.-gain master) (* 0.7 (+ 0.15 flow)) now 0.1)
      ;; shallow = brighter hiss, less body; deep = hiss centered lower, more body
      (.setTargetAtTime (.-frequency hiss-f) (- 3600 (* 1800 depth)) now 0.2)
      (.setTargetAtTime (.-gain hiss-g) (- 0.55 (* 0.2 depth)) now 0.2)
      (.setTargetAtTime (.-gain body-g) (+ 0.08 (* 0.3 depth)) now 0.2))))

(defn start!
  "Idempotent. Must be called from a user gesture."
  []
  (when-not @!ctx
    (let [^js ctx (js/AudioContext.)
          buf     (white-noise-buffer ctx 3)
          master  (gain ctx 0)
          ^js hp  (.createBiquadFilter ctx)
          ;; hiss: two detuned bands so it doesn't sound like one filter
          hiss-f  (band ctx 2800 0.6) hiss-f2 (band ctx 4200 0.8)
          hiss-g  (gain ctx 0.5)      hiss-g2 (gain ctx 0.25)
          ;; body: mid band, quieter
          body-f  (band ctx 700 1.1)  body-g  (gain ctx 0.15)
          s1 (noise-source ctx buf) s2 (noise-source ctx buf) s3 (noise-source ctx buf)]
      (set! (.-type hp) "highpass")
      (set! (.-value (.-frequency hp)) 350)
      (.connect s1 hiss-f)  (.connect hiss-f hiss-g)   (.connect hiss-g hp)
      (.connect s2 hiss-f2) (.connect hiss-f2 hiss-g2) (.connect hiss-g2 hp)
      (.connect s3 body-f)  (.connect body-f body-g)   (.connect body-g hp)
      (.connect hp master)  (.connect master (.-destination ctx))
      (doseq [^js s [s1 s2 s3]] (.start s))
      (reset! !ctx ctx)
      (reset! !nodes {:master master :hiss-g hiss-g :hiss-f hiss-f :body-g body-g})
      (apply-params!)
      ;; independent random drifts on each band = the shimmer of moving water
      (drift! ctx (.-gain hiss-g2) 0.25 0.6)
      (drift! ctx (.-gain body-g) 0.15 0.5)
      (drift! ctx (.-frequency hiss-f2) 4200 0.25)
      (schedule-plips! ctx buf hp)
      (js/console.log "brook: started")))
  (when-let [^js ctx @!ctx] (when (= "suspended" (.-state ctx)) (.resume ctx)))
  true)

(defn set-param! [k v]
  (swap! !params assoc k v)
  (apply-params!))

;; ---- recorded loop (public/assets/audio/brook.mp3) -----------------------------

(defonce !file (atom nil))   ; {:el HTMLAudioElement}

(defn file-available?
  "HEAD the loop file; calls (cb true/false)."
  [cb]
  (-> (js/fetch "/assets/audio/brook.mp3" #js {:method "HEAD"})
      (.then (fn [^js r] (cb (.-ok r))))
      (.catch (fn [_] (cb false)))))

(defn- apply-file-flow! [^js el flow]
  (set! (.-volume el) (min 1 (* 0.9 (+ 0.15 flow))))
  ;; 0.3x at flow 0 … 1.2x at flow 1; pitch follows speed (slow = deeper). The
  ;; browser's preservesPitch time-stretch sounded artificial on this loop.
  (set! (.-preservesPitch el) false)
  (set! (.-playbackRate el) (+ 0.3 (* 0.9 flow))))

(defn start-file! []
  (when-not @!file
    (let [el (js/Audio. "/assets/audio/brook.mp3")]
      (set! (.-loop el) true)
      (apply-file-flow! el (:flow @!params))
      (.play el)
      (reset! !file {:el el})
      (js/console.log "brook: file started"))))

(defn stop-file! []
  (when-let [{:keys [^js el]} @!file]
    (.pause el) (set! (.-src el) "")
    (reset! !file nil)))

(defn set-file-volume! [flow]
  (when-let [{:keys [^js el]} @!file]
    (apply-file-flow! el flow)))

(defn stop! []
  (doseq [t @!timers] (js/clearTimeout t))
  (reset! !timers [])
  (when-let [^js ctx @!ctx] (.close ctx))
  (reset! !ctx nil) (reset! !nodes nil))
