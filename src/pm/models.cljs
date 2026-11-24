(ns pm.models
  "Pure fns for props drawn from a Blender model (`:model`, TASKS T-18): is
   a `:model` map usable, what a named material should become (the kit's
   own, or a glow), and the `MeshStandardMaterial` props to rebuild a
   parsed material with. No three.js, no DOM, no js/Date, no atoms --
   `pm.three` does the loading/rebuilding, `pm.scene` swaps."
  (:require [clojure.string :as str]
            [pm.materials :as mats]))

(defn- finite? [n]
  (and (number? n) (js/isFinite n)))

(defn- finite-pos? [n]
  (and (finite? n) (> n 0)))

(defn- clamp01 [n]
  (cond (< n 0) 0 (> n 1) 1 :else n))

(defn model-spec
  "`{:url s :materials {name entry}}` for a usable `:model` map, else nil.
   `:url` must be an absolute path (starts with \"/\", not \"//\"), longer
   than one character. `:materials` keeps only string-keyed, map-valued
   entries; absent/nil becomes `{}`; present but not a map -> nil (the
   whole model is ignored)."
  [m]
  (when (map? m)
    (let [url (:url m)]
      (when (and (string? url) (> (count url) 1)
                 (str/starts-with? url "/")
                 (not (str/starts-with? url "//")))
        (let [raw (:materials m)
              materials (cond
                          (nil? raw) {}
                          (map? raw) (into {} (filter (fn [[k v]] (and (string? k) (map? v)))) raw)
                          :else nil)]
          (when (some? materials)
            {:url url :materials materials}))))))

(defn material-override
  "`{:kit name}`, `{:glow true}`, both, or nil, for the model material
   named `name` in `materials` (the prop's `:model`'s `:materials`).
   `:kit` only for a non-blank name without `mats/wind-marker`; `:glow`
   only for a literal `true`, and then: `:light false` for a literal
   `false`, else `:light-m r` for a finite r > 0 (`:light-m`). nil when
   neither survives."
  [name materials]
  (when (map? materials)
    (let [e (get materials name)]
      (when (map? e)
        (let [kit   (:kit e)
              kit?  (and (string? kit) (not (str/blank? kit)) (not (str/includes? kit mats/wind-marker)))
              glow? (true? (:glow e))
              base  (cond-> {} kit? (assoc :kit kit) glow? (assoc :glow true))]
          (when (seq base)
            (if-not glow?
              base
              (let [lm (:light-m e)]
                (cond
                  (false? (:light e))       (assoc base :light false)
                  (finite-pos? lm)          (assoc base :light-m lm)
                  :else                     base)))))))))

(defn material-props
  "`MeshStandardMaterial` ctor props (camelCase, for `clj->js`) rebuilt from
   `src`, a plain map read off a parsed material. Bad/missing values fall
   back to sane defaults; opacity < 1 (or an explicit `:transparent`)
   forces `:transparent true`/`:depthWrite false` so glass stays
   see-through."
  [src]
  (let [opacity     (:opacity src)
        opacity'    (if (and (finite? opacity) (<= 0 opacity 1)) opacity 1)
        transparent (or (true? (:transparent src)) (< opacity' 1))
        roughness   (:roughness src)
        metalness   (:metalness src)
        ei          (:emissive-intensity src)
        side        (:side src)
        alpha       (:alpha-test src)]
    {:name              (if (string? (:name src)) (:name src) "")
     :color             (if (integer? (:color src)) (:color src) 0xffffff)
     :roughness         (if (finite? roughness) (clamp01 roughness) 1)
     :metalness         (if (finite? metalness) (clamp01 metalness) 0)
     :emissive          (if (integer? (:emissive src)) (:emissive src) 0)
     :emissiveIntensity (if (and (finite? ei) (>= ei 0)) ei 1)
     :opacity           opacity'
     :transparent       transparent
     :depthWrite        (not transparent)
     :side              (if (#{0 1 2} side) side 0)
     :vertexColors      (true? (:vertex-colors src))
     :alphaTest         (if (and (finite? alpha) (<= 0 alpha 1)) alpha 0)}))
