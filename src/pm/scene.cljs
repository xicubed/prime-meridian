(ns pm.scene
  "Mounts the plan's items into the kit's (or scaffold's) scene, through
   `pm.three` only. Never touches `js/THREE`, the npm module, or
   `window.hiroshi` directly."
  (:require [pm.three :as t]
            [pm.plan :as p]))

(def palette
  "The only hex literals. A look colour not in this map -> :stand-in."
  {:pink         0xf2a7c3
   :brown        0x8a5a3b
   :glass        0xbfe3dc
   :gold         0xe3c46b
   :moss         0x7a9a5a
   :stand-in     0xb8b0a6
   :bubble-paper 0xfffaf0
   :bubble-ink   0x3a2d22
   :sign-paper   0xe9d3a8
   :sign-ink     0x4a3420})

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
  (let [{:keys [look center]} item
        ^js mesh (t/stand-in {:shape (:shape look)
                               :size  (:size look)
                               :color (color (:color look))
                               :pos   center})]
    (set! (.-name mesh) (pr-str (:key item)))
    (t/add! root mesh)
    (when (:label item)
      (let [sprite (t/label (label-opts (:label item) (:label-at item) (:text item)))]
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
