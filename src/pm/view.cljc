(ns pm.view
  "Where the player is looking and where everyone is (TASKS T-19): pure
   data in, pure data out. No I/O, no atoms, no js/Date, no three.js.
   Portable (bb + cljs): requires only clojure.string and pm.lot (cell,
   fmt, nearest-landmark, no-view)."
  (:require [clojure.string :as str]
            [pm.lot :as lot]))

(def max-coord 2000)          ; metres, the same bound as pm.camera/max-coord
(def max-walkers 32)
(def post-every-ms 2000)
(def keep-alive-ms 10000)     ; was 30000: a still camera re-posts every 10 s, so an open game's view is never near 30 s old
(def camera-move-m 0.05)      ; pos or target moved more than this -> changed
(def center-move-m 0.1)
(def walker-move-m 0.25)
(def fresh-ms 120000)
(def here-ms 30000)           ; --at here refuses a view older than this
(def eps 1e-9)
(def no-view lot/no-view)     ; "I can't see where you're looking: the game isn't sending its view"
(def stale-view "I can't see where you're looking: the game isn't open")
(def here-stale "I can't see where you're looking: the game's view is more than 30 s old; look at the ground and ask again")
(def here-sky   "the middle of your screen is sky: look at the ground and ask again")

(defn- sqrt* [x] #?(:clj (Math/sqrt x) :cljs (js/Math.sqrt x)))
(defn- round* [x] #?(:clj (Math/round (double x)) :cljs (js/Math.round x)))
(defn- abs* [x] (if (neg? x) (- x) x))

(defn- finite-num? [x]
  (and (number? x) #?(:clj (Double/isFinite (double x)) :cljs (js/isFinite x))))

(defn vec3?
  "True when v is sequential, has exactly 3 elements, and each is a finite
   number with |x| <= max-coord."
  [v]
  (and (sequential? v) (= 3 (count v))
       (every? (fn [x] (and (finite-num? x) (<= (abs* x) max-coord))) v)))

(defn round-cm
  "x rounded to the nearest centimetre."
  [x]
  (/ (round* (* x 100)) 100))

(defn snapshot
  "{:camera {:pos :target} :center c|nil :walkers {id pos ...}}, every
   number rounded to the centimetre, or nil when cam is nil/invalid (TASKS
   T-19). spots is [{:id :pos ...} ...] (as from pm.bricks/spots): any spot
   whose :id isn't a keyword or whose :pos fails vec3? is dropped, the rest
   sorted by (name :id) and capped at max-walkers."
  [cam center spots]
  (when (and (map? cam) (vec3? (:pos cam)) (vec3? (:target cam)))
    (let [walkers (->> spots
                        (filter (fn [s] (and (keyword? (:id s)) (vec3? (:pos s)))))
                        (sort-by (comp name :id))
                        (take max-walkers))]
      {:camera {:pos (mapv round-cm (:pos cam)) :target (mapv round-cm (:target cam))}
       :center (when (vec3? center) (mapv round-cm center))
       :walkers (into {} (map (fn [s] [(:id s) (mapv round-cm (:pos s))])) walkers)})))

(defn- d3 [[ax ay az] [bx by bz]]
  (sqrt* (+ (* (- ax bx) (- ax bx)) (* (- ay by) (- ay by)) (* (- az bz) (- az bz)))))

(defn changed?
  "True when snap differs enough from last to post again (TASKS T-19): no
   last, the camera's pos or target moved past camera-move-m, the centre
   appeared/disappeared or moved past center-move-m, or the walker set
   changed or one moved past walker-move-m."
  [last snap]
  (boolean
    (or (nil? last)
        (> (d3 (get-in snap [:camera :pos]) (get-in last [:camera :pos])) (+ camera-move-m eps))
        (> (d3 (get-in snap [:camera :target]) (get-in last [:camera :target])) (+ camera-move-m eps))
        (let [c1 (:center last) c2 (:center snap)]
          (or (and (nil? c1) (some? c2))
              (and (some? c1) (nil? c2))
              (and (some? c1) (some? c2) (> (d3 c1 c2) (+ center-move-m eps)))))
        (let [w1 (:walkers last) w2 (:walkers snap)]
          (or (not= (set (keys w1)) (set (keys w2)))
              (some (fn [[k v]] (> (d3 v (get w2 k)) (+ walker-move-m eps))) w1))))))

(defn post?
  "Whether view-tick! should post snap now (TASKS T-19), the first rule
   that matches: demo -> false; bridge off -> false; already in flight ->
   false; no snap -> false; no post yet -> true (first post); throttled
   (< post-every-ms since the last) -> false; something changed -> true;
   keep-alive (>= keep-alive-ms since the last) -> true; else false."
  [state snap now opts]
  (cond
    (:demo? opts) false
    (not (:bridge-on? opts)) false
    (:in-flight? state) false
    (nil? snap) false
    (nil? (:last-at state)) true
    (< (- now (:last-at state)) post-every-ms) false
    (changed? (:last state) snap) true
    (>= (- now (:last-at state)) keep-alive-ms) true
    :else false))

(defn sent
  "state at send time: remembers snap as :last, marks :in-flight?."
  [state snap now]
  (assoc state :last snap :last-at now :in-flight? true))

(defn settled
  "state once a post (success or failure) has finished."
  [state]
  (assoc state :in-flight? false))

(defn view-url [bridge]
  (str "http://127.0.0.1:" (:port bridge) "/view"))

(defn body
  "The POST /view JSON body (string keys) for snap."
  [bridge snap]
  {"token" (:token bridge) "action" "view"
   "camera" {"pos" (get-in snap [:camera :pos]) "target" (get-in snap [:camera :target])}
   "center" (:center snap)
   "walkers" (vec (sort-by #(get % "id")
                            (map (fn [[id pos]] {"id" (name id) "pos" pos}) (:walkers snap))))})

(defn view-edn
  "state/view.edn's shape from v, the successful result of
   pm.bridge/validate for /view."
  [v now]
  {:as-of now :camera (:camera v) :center (:center v) :walkers (:walkers v)})

(defn fresh
  "{:ok true :view raw} when raw looks like a view and is no older than
   fresh-ms (now - :as-of), else {:ok false :reason s}."
  [raw now]
  (cond
    (or (not (map? raw)) (not (number? (:as-of raw)))) {:ok false :reason no-view}
    (> (- now (:as-of raw)) fresh-ms) {:ok false :reason stale-view}
    :else {:ok true :view raw}))

(defn at-here
  "An at result (placement-v2) for `--at here`: `raw` is the parsed
   state/view.edn (or nil); `now` is in ms. The first match wins: no sane
   :as-of -> no-view; older than here-ms -> here-stale; :center isn't 3
   numbers (nil = sky) -> here-sky; else ok true at :center, yaw turned to
   face the camera (0, facing? false, with no camera)."
  [raw now]
  (cond
    (or (not (map? raw)) (not (number? (:as-of raw)))) {:ok false :reason no-view}
    (> (- now (:as-of raw)) here-ms) {:ok false :reason here-stale}
    (not (vec3? (:center raw))) {:ok false :reason here-sky}
    :else
    (let [center (:center raw)
          pos    (get-in raw [:camera :pos])]
      {:ok true :at (vec center) :yaw (lot/face-yaw center pos) :landmark :here :facing? (vec3? pos)})))

(defn at-xz
  "An at result (placement-v2) for `--at x,z`: always ok true, no checks.
   `y` is ground's height at [x z] when `ground` is a map and that's a
   number, else 0 (the game seats it on the real ground anyway). The view
   \"faces\" when `raw` is a map, no older than here-ms, and its camera's
   :pos is 3 numbers; the yaw then turns to face it, else 0."
  [[x z] ground raw now]
  (let [cell (when (map? ground) (lot/cell ground [x z]))
        y    (if (number? (:y cell)) (:y cell) 0)
        pos  (get-in raw [:camera :pos])
        faces (boolean (and (map? raw) (number? (:as-of raw))
                             (<= (- now (:as-of raw)) here-ms)
                             (vec3? pos)))]
    {:ok true :at [x y z] :yaw (if faces (lot/face-yaw [x y z] pos) 0) :landmark [x z] :facing? faces}))

(defn ground-word
  "What ground [x z] is on, from ground (state/ground.edn's shape)."
  [ground [x z]]
  (if-not (map? ground)
    "ground unknown (no ground map)"
    (case (:kind (lot/cell ground [x z]))
      "o" "open ground"
      "T" "under trees"
      "B" "bushes or walls"
      "R" "rocks"
      ":" "creek bed"
      "~" "water"
      "no ground")))

(defn where-lines
  "bb where's lines for a fresh view: how old it is, where the screen's
   centre is looking (or the sky), then one line per walker, sorted by id
   name -- each with the nearest landmark's distance when lms names one."
  [view now ground lms]
  (let [age (max 0 (quot (- now (:as-of view)) 1000))
        near (fn [p] (lot/nearest-landmark lms p))
        suffix (fn [n] (str (lot/fmt (:distance-m n)) " m from " (:word n)))
        center (:center view)
        center-line (if (nil? center)
                      "looking at the sky"
                      (let [[x _ z] center
                            n (near [x z])]
                        (str "looking at [" (lot/fmt x) " " (lot/fmt z) "]: " (ground-word ground [x z])
                             (when n (str ", " (suffix n))))))
        walker-lines (for [[id pos] (sort-by (comp name first) (:walkers view))
                           :let [[x _ z] pos
                                 n (near [x z])]]
                       (str (name id) " at [" (lot/fmt x) " " (lot/fmt z) "]"
                            (when n (str ": " (suffix n)))))]
    (vec (concat [(str "view " age " s old") center-line] walker-lines))))
