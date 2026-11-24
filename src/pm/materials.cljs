(ns pm.materials
  "Pure fns for giving our props the kit's own textured materials (TASKS
   T-17, Michael: \"they're kinda plain\"), tiled by part size, with rounded
   `:box` edges. No three.js, no DOM, no js/Date, no atoms -- `pm.three`
   does the interop, `pm.scene` wires it in."
  (:require [clojure.string :as str]))

(def wind-marker " / wind")   ; the kit's animated node materials carry this in their name: never used
(def bevel-max-m 0.02)        ; the largest edge radius
(def bevel-segments 5)        ; segments per axis of the unit box the rounded box starts from (odd)

(defn- finite-pos? [n]
  (and (number? n) (js/isFinite n) (> n 0)))

(defn- valid-size? [size]
  (and (vector? size) (= 3 (count size)) (every? finite-pos? size)))

(defn index-materials
  "{name material} from `pairs` ([name material], traverse order). Skips a
   pair whose name isn't a non-blank string, whose name contains
   `wind-marker`, or whose material is nil. First occurrence wins."
  [pairs]
  (reduce (fn [acc [name material]]
            (if (and (string? name) (not (str/blank? name))
                     (not (str/includes? name wind-marker))
                     (some? material)
                     (not (contains? acc name)))
              (assoc acc name material)
              acc))
          {} pairs))

(defn props-item?
  "True for one of our props (a blueprint or an estate fixture); false for a
   mirror's item (it has a `:mirror` key: creatures stay painted and
   square)."
  [item]
  (not (contains? item :mirror)))

(defn material-for
  "`{:material m :tile-m t}` for `part`'s kit material, or nil when it stays
   its flat colour: glow parts, a non-keyword `:color`, a missing/bad
   `kit-materials` entry, a wind name, or a name not in `index`."
  [part kit-materials index]
  (when-not (:glow part)
    (let [color (:color part)]
      (when (keyword? color)
        (let [entry (get kit-materials color)]
          (when (map? entry)
            (let [nm (:name entry)]
              (when (and (string? nm) (not (str/includes? nm wind-marker)))
                (let [material (get index nm)]
                  (when (some? material)
                    (let [tile-m (:tile-m entry)
                          t (when (finite-pos? tile-m) tile-m)]
                      {:material material :tile-m t})))))))))))

(defn box-face-repeats
  "Texture repeats for a box's three face pairs, matching BoxGeometry's UVs.
   `[1 1]` for every pair without a valid `size`/`tile-m`."
  [size tile-m]
  (if (and (valid-size? size) (finite-pos? tile-m))
    (let [[sx sy sz] size t tile-m]
      {:x [(/ sz t) (/ sy t)]
       :y [(/ sx t) (/ sz t)]
       :z [(/ sx t) (/ sy t)]})
    {:x [1 1] :y [1 1] :z [1 1]}))

(defn shape-repeat
  "One texture repeat for the whole part, for `:cylinder`/`:cone`; `[1 1]`
   for any other shape or bad `size`/`tile-m`."
  [shape size tile-m]
  (if (and (#{:cylinder :cone} shape) (valid-size? size) (finite-pos? tile-m))
    (let [[sx sy _] size t tile-m]
      [(/ (* js/Math.PI sx) t) (/ sy t)])
    [1 1]))

(defn bevel-radius
  "`min(bevel-max-m, smallest dimension / 4)`, or 0 for a bad `size`."
  [size]
  (if (valid-size? size)
    (let [[sx sy sz] size]
      (min bevel-max-m (/ (min sx sy sz) 4)))
    0))

(defn round-box-vertex
  "One vertex of the rounded box: `p` a vertex of `BoxGeometry(1,1,1,n,n,n)`
   (each coord in [-0.5, 0.5]), `size` `[sx sy sz]` metres, `r` radius
   metres, `n` the segment count. `{:pos [x y z] :normal [nx ny nz]}`."
  [[px py pz] [sx sy sz] r n]
  (let [h   (/ 0.5 n)
        sgn js/Math.sign
        dx  (- px (* (sgn px) h))
        dy  (- py (* (sgn py) h))
        dz  (- pz (* (sgn pz) h))
        len (js/Math.sqrt (+ (* dx dx) (* dy dy) (* dz dz)))
        [nx ny nz] (if (zero? len) [0 0 0] [(/ dx len) (/ dy len) (/ dz len)])
        bx  (- (/ sx 2) r)
        by  (- (/ sy 2) r)
        bz  (- (/ sz 2) r)]
    {:pos [(+ (* bx (sgn px)) (* nx r))
           (+ (* by (sgn py)) (* ny r))
           (+ (* bz (sgn pz)) (* nz r))]
     :normal [nx ny nz]}))

(defn missing-names
  "Every distinct `:name` string in `kit-materials`' map entries that is not
   a key of `index` (a wind name is always missing). `[]` for a
   nil/non-map `kit-materials`."
  [kit-materials index]
  (if (map? kit-materials)
    (->> (vals kit-materials)
         (keep (fn [entry] (when (map? entry) (:name entry))))
         (filter string?)
         distinct
         (filter (fn [nm] (or (str/includes? nm wind-marker) (not (contains? index nm)))))
         sort
         vec)
    []))
