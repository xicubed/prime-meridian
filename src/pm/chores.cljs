(ns pm.chores
  "Click-to-do-a-chore and the checkbox undo/redo it leaves in the talk log.
   A creature's open request is a todo: a counted chore ticks it instead of
   adding a new line. Pure: no atoms, no DOM. Never names a mirror."
  (:require [pm.world :as w]
            [pm.content :as c]
            [pm.talk :as tk]))

(defn- name-of [mirror id]
  (or (:name mirror) (name id)))

(defn- finite? [n]
  (and (number? n) (js/isFinite n)))

(defn chore-at-click [world mirror-id now]
  (let [mirror (get-in world [:mirrors mirror-id])
        chores (:chores mirror)]
    (when (and mirror (seq chores))
      (if-let [due-chore (first (filter #(w/due? world mirror-id (:id %) now) chores))]
        {:due?  true
         :mirror mirror-id
         :chore (:id due-chore)
         :event {:event :chore-done :mirror mirror-id :chore (:id due-chore) :at now :via :click :who :me}}
        (let [c0 (first chores)]
          {:due?    false
           :mirror  mirror-id
           :chore   (:id c0)
           :last-at (w/last-done-at world mirror-id (:id c0))
           :next-at (w/next-due world mirror-id (:id c0))})))))

(defn click [world table mirror-id now offset-min]
  (let [info (when (finite? now) (chore-at-click world mirror-id now))]
    (cond
      (nil? info) world

      (:due? info)
      (let [w1         (w/chore-done world (:event info))
            last-entry (peek (:log w1))]
        (if (= :counted (:result last-entry))
          (-> w1
              (c/hal-on-chore table)
              (update :talk tk/link-chore last-entry
                      (name-of (get-in world [:mirrors mirror-id]) mirror-id) now))
          w1))

      :else
      (let [{:keys [last-at next-at chore]} info]
        (if (and (number? last-at) (number? next-at))
          (c/hal-say world table :chore-not-due
                     {:name     (name-of (get-in world [:mirrors mirror-id]) mirror-id)
                      :chore    (name chore)
                      :last-at  (tk/clock-text last-at offset-min)
                      :next-at  (tk/clock-text next-at offset-min)}
                     now)
          world)))))

(defn untick [world table n now]
  (let [e (tk/entry (:talk world) n)]
    (if-not (and e (tk/box? e) (:done? e))
      world
      (let [record     (:chore e)
            w1         (w/undo-chore world record now)
            last-entry (peek (:log w1))]
        (if (= :undone (:result last-entry))
          (let [m  (:mirror record)
                w2 (-> w1
                       (c/hal-say table :chore-undone
                                  {:name  (name-of (get-in world [:mirrors m]) m)
                                   :chore (name (:chore record))}
                                  now)
                       (update :talk tk/set-done n false nil))]
            (assoc-in w2 [:talk :last-say m] (tk/say-text w1 m now)))
          w1)))))

(defn tick [world table n now offset-min]
  (let [e (tk/entry (:talk world) n)]
    (if-not (and e (tk/box? e) (not (:done? e)))
      world
      (let [m          (:mirror e)
            c          (tk/box-chore e)
            w1         (if (:chore e)
                         (w/redo-chore world (:chore e) now)
                         (w/chore-done world {:event :chore-done :mirror m :chore c
                                               :at now :via :chat :who :me}))
            last-entry (peek (:log w1))]
        (if (= :counted (:result last-entry))
          (-> w1
              (c/hal-on-chore table)
              (update :talk tk/set-done n true (tk/chore-record last-entry)))
          (let [last-at (w/last-done-at world m c)
                next-at (w/next-due world m c)]
            (if (and (number? last-at) (number? next-at))
              (c/hal-say world table :chore-not-due
                         {:name    (name-of (get-in world [:mirrors m]) m)
                          :chore   (name c)
                          :last-at (tk/clock-text last-at offset-min)
                          :next-at (tk/clock-text next-at offset-min)}
                         now)
              world)))))))
