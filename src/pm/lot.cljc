(ns pm.lot
  "Where a crew member's draft may go (TASKS T-13): pure data in, pure data
   out. No I/O, no atoms, no js/Date, no three.js. Used by bb (scripts/
   blueprint_draft.clj) and the cljs tests, so portable (cljc): requires
   only clojure.string and pm.blueprint-file (its id-re, member-re)."
  (:require [clojure.string :as str]
            [pm.blueprint-file :as bf]))

(def eps 1e-9)
(def default-radius-m 0.6)
(def grounded-m 0.02)
(def narrow-m 0.3)
(def usage "usage: bb blueprint-draft <member> <draft.edn> --near <landmark|here|walker|x,z> [--dry-run] | bb blueprint-draft --landmarks")
(def no-ground-map "no ground map (state/ground.edn): the twin samples it from the kit")
(def nothing-grounded "nothing touches the ground")
(def no-view "I can't see where you're looking: the game isn't sending its view")
(def sky "I can't tell where you're looking: the middle of your screen is sky")

(def ^:private pi #?(:clj Math/PI :cljs js/Math.PI))
(defn- sqrt* [x]  #?(:clj (Math/sqrt x) :cljs (js/Math.sqrt x)))
(defn- cos*  [x]  #?(:clj (Math/cos x)  :cljs (js/Math.cos x)))
(defn- sin*  [x]  #?(:clj (Math/sin x)  :cljs (js/Math.sin x)))
(defn- round* [x] #?(:clj (Math/round (double x)) :cljs (js/Math.round x)))
(defn- ceil*  [x] #?(:clj (Math/ceil x)  :cljs (js/Math.ceil x)))
(defn- floor* [x] #?(:clj (Math/floor x) :cljs (js/Math.floor x)))
(defn- to-radians [deg] (* deg (/ pi 180)))
(defn- abs* [x] (if (neg? x) (- x) x))

(defn- words [k]
  (str/replace (name k) "-" " "))

(defn- valid-at3? [at]
  (and (vector? at) (= 3 (count at)) (every? number? at)))

(defn- sized-part? [{:keys [size at]}]
  (and (vector? size) (= 3 (count size)) (every? number? size)
       (vector? at) (= 3 (count at)) (every? number? at)))

(defn feet
  "The look's feet, in part order: a grounded part (bottom within
   grounded-m of 0, rotation ignored) at most narrow-m across in x and z
   gives one foot at its centre; any other grounded part gives its four xz
   corners and its centre. No grounded part (or nil look) -> []."
  [look]
  (vec
   (mapcat
    (fn [part]
      (if (sized-part? part)
        (let [[sx sy sz] (:size part)
              [ax ay az] (:at part)]
          (if (<= (abs* (- ay (/ sy 2))) (+ grounded-m eps))
            (if (and (<= sx (+ narrow-m eps)) (<= sz (+ narrow-m eps)))
              [[ax az]]
              (let [hx (/ sx 2) hz (/ sz 2)]
                [[ax az] [(- ax hx) (- az hz)] [(- ax hx) (+ az hz)]
                 [(+ ax hx) (- az hz)] [(+ ax hx) (+ az hz)]]))
            []))
        []))
    (:parts look))))

(defn look-radius
  "Half the diagonal of the xz bounding box of every part with :size and
   :at (rotation ignored), times scale. No such part (or nil look) -> nil."
  [look scale]
  (let [parts (filter sized-part? (:parts look))]
    (when (seq parts)
      (let [xs (mapcat (fn [{[sx _ _] :size [ax _ _] :at}] [(- ax (/ sx 2)) (+ ax (/ sx 2))]) parts)
            zs (mapcat (fn [{[_ _ sz] :size [_ _ az] :at}] [(- az (/ sz 2)) (+ az (/ sz 2))]) parts)
            w  (- (apply max xs) (apply min xs))
            d  (- (apply max zs) (apply min zs))]
        (* scale 0.5 (sqrt* (+ (* w w) (* d d))))))))

(defn- round12 [v]
  (/ (round* (* v 1e12)) 1e12))

(defn turn
  "[dx dz] turned by yaw degrees -> [wx wz]. +90 turns local +x to world -z."
  [[dx dz] yaw]
  (let [c (round12 (cos* (to-radians yaw)))
        s (round12 (sin* (to-radians yaw)))]
    [(+ (* c dx) (* s dz)) (+ (* (- s) dx) (* c dz))]))

(defn feet-at
  "Each foot turned by yaw and added to [x z], same order as feet."
  [feet [x z] yaw]
  (mapv (fn [[dx dz]]
          (let [[wx wz] (turn [dx dz] yaw)]
            [(+ x wx) (+ z wz)]))
        feet))

(defn cell
  "Nearest cell of ground to [x z]: {:kind s :y h}, {:kind \" \" :y nil}
   outside the grid or past a row string's end."
  [ground [x z]]
  (let [{:keys [origin step cols rows kinds y]} ground
        [x0 z0] origin
        c (round* (/ (- x x0) step))
        r (round* (/ (- z z0) step))]
    (if (and (<= 0 c) (< c cols) (<= 0 r) (< r rows))
      (let [row (nth kinds r)]
        {:kind (if (< c (count row)) (str (nth row c)) " ")
         :y (get-in y [r c])})
      {:kind " " :y nil})))

(defn segments
  "One segment per consecutive route pair, per walker, in order; plus
   [last first] when :loop is true and the route has >= 2 points; a
   one-point route gives [[p p]]. No walkers -> []."
  [estate]
  (vec
   (mapcat
    (fn [{:keys [route loop]}]
      (if (and (sequential? route) (seq route))
        (let [pts (vec route)]
          (if (= 1 (count pts))
            [[(first pts) (first pts)]]
            (let [pairs (mapv vector pts (rest pts))]
              (if (and loop (>= (count pts) 2))
                (conj pairs [(last pts) (first pts)])
                pairs))))
        []))
    (:walkers estate))))

(defn- sub2 [[ax az] [bx bz]] [(- ax bx) (- az bz)])
(defn- dot2 [[ax az] [bx bz]] (+ (* ax bx) (* az bz)))
(defn- norm2 [[x z]] (sqrt* (+ (* x x) (* z z))))

(defn seg-dist
  "Distance from p to segment a-b (all [x z])."
  [p a b]
  (if (= a b)
    (norm2 (sub2 p a))
    (let [ab (sub2 b a)
          ap (sub2 p a)
          t  (-> (dot2 ap ab) (/ (dot2 ab ab)) (max 0) (min 1))
          proj [(+ (first a) (* t (first ab))) (+ (second a) (* t (second ab)))]]
      (norm2 (sub2 p proj)))))

(defn- places-of [estate]
  (keep (fn [[k v]]
          (when (and (map? v) (valid-at3? (:at v)))
            {:id k :xz [(nth (:at v) 0) (nth (:at v) 2)]
             :r default-radius-m :word (str "the " (words k))}))
        estate))

(defn- mirror-items-of [estate]
  (mapcat
   (fn [mirror]
     (let [mirror-id (:id mirror)]
       (keep (fn [item]
               (when (and (keyword? (:id item)) (valid-at3? (:at item)))
                 (let [item-id (:id item)
                       at (:at item)]
                   {:id (keyword (str (name mirror-id) "-" (name item-id)))
                    :xz [(nth at 0) (nth at 2)]
                    :r (or (look-radius (:look item) (or (:scale item) 1)) default-radius-m)
                    :word (str (or (:name mirror) (words mirror-id)) "'s " (words item-id))})))
             (:placed mirror))))
   (:mirrors estate)))

(defn- blueprints-of [bps]
  (keep (fn [bp]
          (when (and (keyword? (:id bp)) (valid-at3? (:at bp)))
            (let [at (:at bp)]
              {:id (:id bp)
               :xz [(nth at 0) (nth at 2)]
               :r (or (look-radius (:look bp) (or (:scale bp) 1)) default-radius-m)
               :word (str "the " (words (:id bp)))})))
        bps))

(defn- obstacles-of [estate]
  (map-indexed
   (fn [i o] {:id (keyword (str "obstacle-" (inc i))) :xz (:at o) :r (:r o) :word "an obstacle"})
   (filter (fn [o] (and (vector? (:at o)) (= 2 (count (:at o))) (number? (:r o))))
           (:obstacles (:motion estate)))))

(defn things
  "Every thing a draft must keep clear of, sorted (stably) by (name :id)."
  [estate file-blueprints]
  (vec (sort-by (comp name :id)
                (concat (places-of estate)
                        (mirror-items-of estate)
                        (blueprints-of (:blueprints estate))
                        (blueprints-of file-blueprints)
                        (obstacles-of estate)))))

(defn- landmarks3
  "Sorted map {id {:word w :xz [x z]}}, plus :creek when ground has any
   creek/water cell and no :creek landmark already exists."
  [ground estate file-blueprints]
  (let [named (concat (places-of estate)
                       (mirror-items-of estate)
                       (blueprints-of (:blueprints estate))
                       (blueprints-of file-blueprints))
        base (reduce (fn [m t]
                       (if (contains? m (:id t)) m (assoc m (:id t) {:word (:word t) :xz (:xz t)})))
                     (sorted-map)
                     named)]
    (if (and (map? ground) (not (contains? base :creek)))
      (let [{:keys [origin step cols rows kinds]} ground
            [x0 z0] origin
            cells (for [r (range rows)
                        c (range cols)
                        :let [row (nth kinds r)
                              ch (if (< c (count row)) (str (nth row c)) " ")]
                        :when (or (= ch ":") (= ch "~"))]
                    [(+ x0 (* c step)) (+ z0 (* r step))])]
        (if (seq cells)
          (assoc base :creek {:word "the creek" :cells (vec cells)})
          base))
      base)))

(defn view-landmarks
  "Sorted map of the view's own landmarks (TASKS T-19): :here (the view's
   :center, when non-nil) and one entry per walker in (:walkers view),
   named by the matching estate walker's :name, else its id with dashes
   turned to spaces. nil view -> {}."
  [view estate]
  (if (nil? view)
    (sorted-map)
    (let [wnames (into {} (map (fn [w] [(:id w) (:name w)])) (:walkers estate))
          base (reduce (fn [m [id [x _ z]]]
                         (assoc m id {:word (or (get wnames id) (words id)) :xz [x z]}))
                       (sorted-map)
                       (:walkers view))]
      (if-let [c (:center view)]
        (assoc base :here {:word "where you're looking" :xz [(nth c 0) (nth c 2)]})
        base))))

(defn landmarks
  "Sorted map {id {:word w :xz [x z]}} (see landmarks3); the 4-arity also
   merges in the view's own landmarks (view-landmarks) for any id not
   already named -- estate names win. No view (3-arity, or a nil view)
   behaves exactly as landmarks3."
  ([ground estate file-blueprints] (landmarks3 ground estate file-blueprints))
  ([ground estate file-blueprints view]
   (let [base (landmarks3 ground estate file-blueprints)]
     (reduce (fn [m [k v]] (if (contains? m k) m (assoc m k v)))
             base
             (view-landmarks view estate)))))

(declare fmt)

;; A spot given as coordinates (`--near x,z`, the twin's pick from where the
;; player is looking) is a landmark of its own, named by its numbers.
(defn coord-near
  "\"x,z\" -> [x z] (numbers), else nil."
  [s]
  (when-let [[_ x z] (re-matches #"(-?\d+(?:\.\d+)?),(-?\d+(?:\.\d+)?)" (str s))]
    [#?(:clj (Double/parseDouble x) :cljs (js/parseFloat x))
     #?(:clj (Double/parseDouble z) :cljs (js/parseFloat z))]))

(defn resolve-landmark
  "The landmark `near` names: a keyword from `lms`, or an [x z] spot."
  [lms near]
  (if (vector? near)
    {:word (str "[" (fmt (first near)) " " (fmt (second near)) "]") :xz near}
    (get lms near)))

(defn landmark-lines [lms]
  (mapv (fn [[k {:keys [word]}]] (str (name k) ": " word)) lms))

(defn- dist-to-landmark [lm [x z]]
  (if (:cells lm)
    (apply min (map (fn [[cx cz]] (norm2 [(- x cx) (- z cz)])) (:cells lm)))
    (norm2 (sub2 [x z] (:xz lm)))))

(defn nearest-landmark
  "The closest of lms (a sorted map, see landmarks) to [x z]: {:id :word
   :distance-m}, or nil for an empty lms. A tie keeps the first in lms
   order (the smaller name), since only a strictly closer candidate
   replaces the best so far."
  [lms [x z]]
  (reduce (fn [best [id lm]]
            (let [d (dist-to-landmark lm [x z])]
              (if (or (nil? best) (< d (:distance-m best)))
                {:id id :word (:word lm) :distance-m d}
                best)))
          nil
          lms))

(defn- cell-reason [kind]
  (cond
    (= kind "T") "most of it is under trees"
    (= kind "B") "most of it is bushes or walls"
    (= kind "R") "most of it is rocks"
    (or (= kind ":") (= kind "~")) "most of it is in the creek"
    :else "most of it has no ground"))

(defn spot
  "Verdict for one candidate point and yaw: {:ok true :y h :spread s} or
   {:ok false :why reason}. ctx = {:ground :rules :feet :radius :things
   :segments}."
  [{:keys [ground rules feet radius things segments]} [x z] yaw]
  (let [{:keys [max-spread-m gap-m route-m]} rules
        world-feet (feet-at feet [x z] yaw)
        cells (map #(cell ground %) world-feet)
        bad (first (remove (fn [{:keys [kind y]}] (and (= kind "o") (some? y))) cells))]
    (if bad
      {:ok false :why (if (= (:kind bad) "o") "most of it has no ground" (cell-reason (:kind bad)))}
      (let [ys (map :y cells)
            spread (- (apply max ys) (apply min ys))]
        (if (> spread (+ max-spread-m eps))
          {:ok false :why "too steep"}
          (let [clearances (map (fn [t] [(- (norm2 (sub2 [x z] (:xz t))) radius (:r t)) t]) things)
                closest (first (sort-by first clearances))]
            (if (and closest (< (first closest) (- gap-m eps)))
              {:ok false :why (str "too close to " (:word (second closest)))}
              (let [on-route (some (fn [[a b]] (< (- (seg-dist [x z] a b) radius) (- route-m eps))) segments)]
                (if on-route
                  {:ok false :why "on a walker's path"}
                  {:ok true :y (apply max ys) :spread spread})))))))))

(defn fmt [x]
  (let [n (round* (* x 100))
        sign (if (neg? n) "-" "")
        n (abs* n)
        q (quot n 100)
        r (rem n 100)]
    (str sign
         (cond
           (zero? r) (str q)
           (zero? (rem r 10)) (str q "." (quot r 10))
           :else (str q "." (if (< r 10) (str "0" r) r))))))

(defn refusal [whys search-m word]
  (if (seq whys)
    (let [freqs (frequencies whys)
          best (first (sort-by (fn [[w n]] [(- n) w]) freqs))]
      (str "no level open ground within " (fmt search-m) " m of " word ": " (first best)))
    (str "no level open ground within " (fmt search-m) " m of " word)))

(defn placed-line [draft word result]
  (let [{:keys [at yaw distance-m spread-m]} result
        [x y z] at]
    (str "placed " (name (:id draft)) " " (fmt distance-m) " m from " word
         " at [" (fmt x) " " (fmt y) " " (fmt z) "] yaw " (fmt yaw)
         " (feet within " (round* (* spread-m 100)) " cm)")))

(defn blueprint [draft result]
  {:id (:id draft) :at (:at result) :yaw (:yaw result)
   :summary (:summary draft) :look (:look draft)})

(defn place
  "The quest's placement: ground, estate, file-blueprints, rules, draft,
   near (a landmark keyword) -> {:ok true :at [x y z] :yaw deg :landmark k
   :distance-m d :spread-m s} | {:ok false :reason \"...\"}. `seen` (7-arity,
   TASKS T-19) is (view/fresh raw now) or nil: when ok, its :view's :here
   and walkers join the landmarks near can name; the 6-arity is this with
   seen nil, behaving exactly as before."
  ([ground estate file-blueprints rules draft near]
   (place ground estate file-blueprints rules draft near nil))
  ([ground estate file-blueprints rules draft near seen]
   (if-not (map? ground)
    {:ok false :reason no-ground-map}
    (let [view (when (:ok seen) (:view seen))
          lms (landmarks ground estate file-blueprints view)]
      (if-not (resolve-landmark lms near)
        (cond
          (and (= near :here) (some? view))
          {:ok false :reason sky}

          (and (or (= near :here) (contains? (set (map :id (:walkers estate))) near))
               (nil? view))
          {:ok false :reason (or (:reason seen) no-view)}

          :else
          {:ok false :reason (str "I don't know a place called " (name near) "; I know: "
                                   (str/join ", " (map name (keys lms))))})
        (let [fs (feet (:look draft))]
          (if (empty? fs)
            {:ok false :reason nothing-grounded}
            (let [radius (or (look-radius (:look draft) 1) default-radius-m)
                  ts (things estate file-blueprints)
                  segs (segments estate)
                  ctx {:ground ground :rules rules :feet fs :radius radius :things ts :segments segs}
                  lm (resolve-landmark lms near)
                  step (:step-m rules)
                  search-m (:search-m rules)
                  yaws (:yaws rules)
                  [lo-x hi-x lo-z hi-z]
                  (if (:cells lm)
                    (let [xs (map first (:cells lm)) zs (map second (:cells lm))]
                      [(apply min xs) (apply max xs) (apply min zs) (apply max zs)])
                    (let [[lx lz] (:xz lm)] [lx lx lz lz]))
                  kx-lo (ceil*  (- (/ (- lo-x search-m) step) eps))
                  kx-hi (floor* (+ (/ (+ hi-x search-m) step) eps))
                  kz-lo (ceil*  (- (/ (- lo-z search-m) step) eps))
                  kz-hi (floor* (+ (/ (+ hi-z search-m) step) eps))
                  candidates (for [kx (range kx-lo (inc kx-hi))
                                   kz (range kz-lo (inc kz-hi))
                                   :let [x (* kx step) z (* kz step)
                                         d (dist-to-landmark lm [x z])]
                                   :when (<= d (+ search-m eps))
                                   yaw yaws]
                               [x z yaw d])
                  verdicts (map (fn [[x z yaw d]] [x z yaw d (spot ctx [x z] yaw)]) candidates)
                  accepted (filter (fn [[_ _ _ _ v]] (:ok v)) verdicts)
                  whys (keep (fn [[_ _ _ _ v]] (when-not (:ok v) (:why v))) verdicts)]
              (if (seq accepted)
                (let [[x z yaw d v]
                      (first (sort-by (fn [[x z yaw d v]]
                                        [(round* (* d 1e6)) (round* (* (:spread v) 1e6)) x z yaw])
                                      accepted))]
                  {:ok true :at [x (:y v) z] :yaw yaw :landmark near :distance-m d :spread-m (:spread v)})
                {:ok false :reason (refusal whys search-m (:word lm))})))))))))

(defn parse-args [args]
  (let [args (vec (or args []))]
    (cond
      (= ["--landmarks"] args)
      {:mode :landmarks}

      (and (>= (count args) 2)
           (re-matches bf/member-re (first args))
           (not (str/blank? (second args)))
           (not (str/starts-with? (second args) "--")))
      (let [member (first args) path (second args) rest-args (vec (drop 2 args))
            near (fn [n]
                   (or (coord-near n)
                       (let [n' (str/replace-first n #"^:" "")]
                         (when (re-matches bf/id-re n') (keyword n')))))]
        (cond
          (and (= 2 (count rest-args)) (= "--near" (first rest-args)) (near (second rest-args)))
          {:mode :draft :member (keyword member) :path path :near (near (second rest-args)) :dry-run false}

          (and (= 3 (count rest-args)) (= "--near" (first rest-args)) (near (second rest-args)) (= "--dry-run" (nth rest-args 2)))
          {:mode :draft :member (keyword member) :path path :near (near (second rest-args)) :dry-run true}

          (and (= 3 (count rest-args)) (= "--dry-run" (first rest-args)) (= "--near" (second rest-args)) (near (nth rest-args 2)))
          {:mode :draft :member (keyword member) :path path :near (near (nth rest-args 2)) :dry-run true}

          :else
          {:usage usage}))

      :else
      {:usage usage})))
