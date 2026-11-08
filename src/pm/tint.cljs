(ns pm.tint
  "Per-walker tints: clone a character's materials and multiply their colour.
   Touches only the three.js objects handed to it (.traverse, .isMesh, .material,
   .clone, .color.getHex/.setHex) and imports nothing, so Node can test it."
  (:require [pm.motion :as m]))

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
