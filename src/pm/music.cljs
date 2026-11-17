(ns pm.music
  "The soundtrack (estate.edn :music): one instrumental track at a time,
   crossfading when the scene's mood changes -- the Construct's theme in
   the white room, the dojo track while you spar, then one track per sky
   hour (pm.sky), so the music turns with the light. Under the brook, its
   own ♪ switch, remembered per browser like the brook's.

   `track-for` is the whole rule and pure (tested); the player below only
   moves two <audio> elements' volumes to match.")

(defn track-for
  "Which track should play: :construct while the Construct shows (white
   room, rack, jump, free your mind), :dojo while sparring, else the
   track `:by-sky` maps the sky's hour to. nil = silence (no hour, or a
   mapping to nothing)."
  [music {:keys [construct? sparring? sky]}]
  (cond
    sparring?  :dojo
    construct? :construct
    sky        (get (:by-sky music) sky)
    :else      nil))

(defn url-for
  "The file for `track`, or nil when there isn't one (the track's file
   isn't in this checkout, e.g. a track kept out of the public repo)."
  [music track]
  (get (:tracks music) track))

;; ---- the player (side effects) ------------------------------------------

(defonce ^:private !p (atom {:track nil :el nil :els {}}))

(def ^:private key-on "pm.music")

(defn on?
  "The player's choice, per browser; on unless they turned it off."
  []
  (try (not= "0" (.getItem js/localStorage key-on)) (catch :default _ true)))

(defn- save-on! [v]
  (try (.setItem js/localStorage key-on (if v "1" "0")) (catch :default _ nil)))

(defn- ramp!
  "Fades `el` from its volume to `to` over `secs`; pauses it at 0. A new
   ramp on the same element replaces the one in progress (a track faded
   out and straight back in mustn't have two ramps fighting)."
  [^js el to secs]
  (when-let [old (.-pmRamp el)] (js/clearInterval old))
  (let [from  (.-volume el)
        steps (max 1 (js/Math.round (* secs 20)))
        n     (atom 0)
        id    (js/setInterval
                (fn []
                  (swap! n inc)
                  (let [v (+ from (* (- to from) (/ @n steps)))]
                    (set! (.-volume el) (max 0 (min 1 v))))
                  (when (>= @n steps)
                    (js/clearInterval (.-pmRamp el))
                    (set! (.-pmRamp el) nil)
                    (when (zero? to) (.pause el))))
                50)]
    (set! (.-pmRamp el) id)))

(defn play!
  "Crossfades to `track` (nil = fade to silence) at `volume` over `fade-s`.
   A no-op when it's already the track playing. Needs a prior gesture."
  [music track]
  (let [{cur :track ^js el :el} @!p
        url    (url-for music track)
        vol    (or (:volume music) 0.3)
        fade-s (or (:fade-s music) 4)]
    (when (not= track cur)
      (when el (ramp! el 0 fade-s))
      (if-not url
        (swap! !p assoc :track track :el nil)
        ;; one element per file, kept: coming back to a track (the theme
        ;; after sparring) resumes it where it paused instead of restarting
        (let [^js a (or (get-in @!p [:els url])
                        ;; a new element starts silent and fades up (a kept one
                        ;; fades up from wherever its fade-out got to)
                        (doto (js/Audio. url) (aset "volume" 0)))]
          (swap! !p assoc-in [:els url] a)
          (set! (.-loop a) true)
          (-> (.play a) (.catch (fn [_] nil)))
          (ramp! a vol fade-s)
          (swap! !p assoc :track track :el a))))))

(defn stop! []
  (when-let [^js el (:el @!p)] (ramp! el 0 1))
  (swap! !p assoc :track nil :el nil))

(defn toggle!
  "The ♪ switch: off stops and is remembered; on resumes `track`."
  [music track]
  (if (on?)
    (do (save-on! false) (stop!) false)
    (do (save-on! true) (play! music track) true)))
