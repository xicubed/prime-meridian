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
(def keep-alive-ms 30000)
(def camera-move-m 0.05)      ; pos or target moved more than this -> changed
(def center-move-m 0.1)
(def walker-move-m 0.25)
(def fresh-ms 120000)
(def eps 1e-9)
(def no-view lot/no-view)     ; "I can't see where you're looking: the game isn't sending its view"
(def stale-view "I can't see where you're looking: the game isn't open")

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
