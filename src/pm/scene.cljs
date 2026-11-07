(ns pm.scene
  "Mounts the plan's items into the kit's (or scaffold's) scene, through
   `pm.three` only. Never touches `js/THREE`, the npm module, or
   `window.hiroshi` directly."
  (:require [pm.three :as t]
            [pm.plan :as p]))

(def palette
  "The only hex literals. A look colour not in this map -> :stand-in."
  {:pink         0xff9ec4
   :brown        0x8a5a3b
   :glass        0xbfe3dc
   :gold         0xe3c46b
   :moss         0x7a9a5a
   :gill         0xe0678f
   :eye          0x1e1a1c
   :mane         0x3b2a1e
   :tin          0x9a9184
   :stand-in     0xb8b0a6
   :bubble-paper 0xfffaf0
   :bubble-ink   0x3a2d22
   :sign-paper   0xe9d3a8
   :sign-ink     0x4a3420
   :crew         0x5b7fa3
   :crew-head    0xf0e6d6})

(def label-height 0.3)

(defn- color [kw]
  (get palette kw (:stand-in palette)))

(defonce ^:private !mounted (atom nil))

(defn- label-opts [label-kind pos text]
  (let [[paper ink] (if (= label-kind :sign)
                       [:sign-paper :sign-ink]
                       [:bubble-paper :bubble-ink])]
    {:text   text
     :height label-height
     :paper  (color paper)
     :ink    (color ink)
     :pos    pos}))

(defn- mount-item! [root item]
  (let [{:keys [key at yaw parts label label-at text]} item
        ^js item-group (t/group {:name (pr-str key) :pos at :rot [0 (p/radians yaw) 0]})]
    (doseq [[i part] (map-indexed vector parts)]
      (let [^js mesh (t/stand-in {:shape (:shape part)
                                  :size  (:size part)
                                  :color (color (:color part))
                                  :pos   (:at part)
                                  :rot   (mapv p/radians (:rot part))})]
        (set! (.-name mesh) (str (pr-str key) "/" i))
        (t/add! item-group mesh)))
    (t/add! root item-group)
    (when label
      (let [sprite (t/label (label-opts label label-at text))]
        (t/add! root sprite)
        sprite))))

(defn update!
  "Refresh only the labels whose text changed since the last call. Never
   creates or removes meshes/labels."
  [world now]
  (when-let [{:keys [labels texts]} @!mounted]
    (let [plan    (p/scene-plan world now)
          changes (p/label-changes texts plan)]
      (doseq [[k text] changes]
        (when-let [sprite (get labels k)]
          (t/set-label-text! sprite text)))
      (swap! !mounted assoc :texts (merge texts changes) :plan plan)))
  nil)

(defn mount!
  "Build the plan's meshes and labels into `scene` once. No-op if already
   mounted."
  [scene world now]
  (when-not @!mounted
    (let [plan   (p/scene-plan world now)
          root   (t/group {:name "pm-mirrors"})
          labels (reduce (fn [acc item]
                            (if-let [sprite (mount-item! root item)]
                              (assoc acc (:key item) sprite)
                              acc))
                          {} plan)]
      (t/add! scene root)
      (reset! !mounted {:root root :labels labels :texts (p/label-texts plan) :plan plan})
      (update! world now)))
  nil)

(defn summary []
  (when-let [{:keys [plan]} @!mounted]
    (p/plan-summary plan)))

(defn mounted-root []
  (:root @!mounted))

(defn current-plan []
  (:plan @!mounted))
