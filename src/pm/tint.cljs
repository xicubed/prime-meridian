(ns pm.tint
  "Per-walker tints: clone a character's materials and multiply their colour.
   Touches only the three.js objects handed to it (.traverse, .isMesh, .material,
   .clone, .color.getHex/.setHex) and imports nothing, so Node can test it."
  (:require [pm.motion :as m]))

(defn recolor-object!
  "Gives a mannequin a walker's own two colours (pm.motion/recolor-hex):
   light shell -> :body, dark joints -> :joints. Clones each material once.
   Returns the number of materials changed; 0 without valid `colors`."
  [^js obj colors]
  (if-not (m/valid-colors? colors)
    0
    (let [clones (atom {})
          clone-of (fn [^js mt]
                     (or (get @clones mt)
                         (if (.-color mt)
                           (let [^js c (.clone mt)]
                             (.setHex (.-color c) (m/recolor-hex (.getHex (.-color mt)) colors))
                             (swap! clones assoc mt c)
                             c)
                           mt)))]
      (.traverse obj
        (fn [^js o]
          (when (.-isMesh o)
            (let [mat (.-material o)]
              (if (js/Array.isArray mat)
                (set! (.-material o) (into-array (map clone-of mat)))
                (when (.-color mat)
                  (set! (.-material o) (clone-of mat))))))))
      (count @clones))))

(defn tint-object! [^js obj tint]
  (if-not (and (integer? tint) (<= 0 tint 0xffffff))
    0
    (let [clones   (atom {})
          n        (atom 0)
          clone-of (fn [^js mt]
                     (if-let [c (get @clones mt)]
                       c
                       (if (.-color mt)
                         (let [^js c (.clone mt)]
                           (.setHex (.-color c) (m/tint-hex (.getHex (.-color mt)) tint))
                           (swap! clones assoc mt c)
                           (swap! n inc)
                           c)
                         mt)))]
      (.traverse obj
        (fn [^js o]
          (when (.-isMesh o)
            (let [mat (.-material o)]
              (if (js/Array.isArray mat)
                (set! (.-material o) (into-array (map clone-of mat)))
                (when (.-color mat)
                  (set! (.-material o) (clone-of mat))))))))
      @n)))
