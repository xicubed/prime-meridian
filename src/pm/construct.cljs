(ns pm.construct
  "The Construct's first two beats (GDD §2.7, §4.8). Beat 1 (`white-room`):
   a first-time player's jack-in lands in a silent white room; after a
   beat, the twin steps in and says its original opening line. Beat 2
   (`program-rack`): a rack of programs scrolls past, and your own agent --
   never mail, which many players can't connect on day one -- loads first
   as its sample version (GDD §4.6, §2.6), the moment its building drops
   out of the white sky. Original lines, drafted fresh for this scene, not
   quoted from the film (the film is only the beat sheet). Pure: no
   three.js, no atoms, no js/Date -- `now` (epoch ms) is always passed in,
   and whether this player has ever seen it lives outside `world` (pm.core,
   localStorage) as `seen?`, the same way `pm.core/!sample-only` mirrors
   its own localStorage key instead of living on `world`."
  (:require [pm.content :as c]))

(def silent-ms
  "How long the white room stays silent (GDD §2.7: \"White screen.
   Silence.\") before the twin steps in."
  1500)

(def line-ms
  "How long the twin's opening line stays up once it's said -- the same
   banner duration every other twin line uses."
  c/hal-ms)

(defn script
  "The Construct's words and program rack, from estate.edn's
   :construct-script (the twin's lines are content, like every other line it
   says; drafted fresh for this scene, not quoted from the film)."
  [world]
  (:construct-script world))

(defn opening-line
  "The twin's opening line for the first beat (GDD §2.7)."
  [world]
  (:opening (script world)))

(defn enter
  "Records `now` as the moment the white room was entered, the first time
   this is called for a first-time player (`seen?` false); a no-op once
   recorded, once `seen?`, or with a bad `now`."
  [world seen? now]
  (if (or seen? (:entered-at (:construct world)) (not (number? now)))
    world
    (assoc world :construct {:entered-at now})))

(defn white-room
  "The Construct's first beat (GDD §2.7) at `now`: nil for a returning
   player (`seen?` true), or before `enter` has recorded `world`'s
   :entered-at. For a first-time player: {:phase :silent} for `silent-ms`
   after entering, then {:phase :twin :line (opening-line world)} for `line-ms`
   more -- the moment the twin steps in and speaks -- then nil again (the
   first beat's done; the second, `program-rack`, picks up from there)."
  [world seen? now]
  (when-not seen?
    (when-let [entered-at (:entered-at (:construct world))]
      (when (number? now)
        (let [elapsed (- now entered-at)]
          (cond
            (< elapsed 0) nil
            (< elapsed silent-ms) {:phase :silent}
            (< elapsed (+ silent-ms line-ms)) {:phase :twin :line (opening-line world)}
            :else nil))))))

(defn programs
  "The rack of programs (GDD §2.6, §2.7) that scrolls past like the weapons
   racks in the film: skills and MCP connectors. The data lists your own
   agent first; it's chosen for you (never mail, which many players can't
   connect on day one)."
  [world]
  (vec (:programs (script world))))

(defn own-agent
  "The program chosen for you first: the rack's first entry."
  [world]
  (first (programs world)))

(def rack-ms
  "How long the program rack (GDD §2.6, §2.7) scrolls past before your own
   agent program loads."
  2200)

(defn loaded-line
  "The twin's line the moment your own agent loads (GDD §2.7)."
  [world]
  (:loaded (script world)))

(defn load-program
  "Loads `id` for the first time (GDD §2.6, §2.7): every program loads
   instantly as its sample version (GDD §4.6), recorded in `world`'s
   :construct :loaded set. The first time `id` is own-agent's, also records
   :building-at `now`, so the matching building can drop out of the white
   sky (`pm.plan/building-drop-pose` times that). A no-op once `id` is
   already loaded, or with a bad `now`."
  [world id now]
  (if (or (contains? (get-in world [:construct :loaded]) id) (not (number? now)))
    world
    (cond-> (update-in world [:construct :loaded] (fnil conj #{}) id)
      (= id (:id (own-agent world))) (assoc-in [:construct :building-at] now))))

(defn program-rack
  "The Construct's second beat (GDD §2.6, §2.7) at `now`, picking up right
   where `white-room` leaves off: nil for a returning player, before
   `enter`, with a bad `now`, or while the first beat is still showing.
   Then {:phase :rack :programs (programs world)} for `rack-ms` while the rack
   scrolls past, then {:phase :loaded :program (own-agent world) :line (loaded-line world)}
   for `line-ms` more -- the moment pm.core calls `load-program` and the
   matching building drops -- then nil again, the beat done."
  [world seen? now]
  (when-not seen?
    (when-let [entered-at (:entered-at (:construct world))]
      (when (number? now)
        (let [start   (+ entered-at silent-ms line-ms)
              elapsed (- now start)]
          (cond
            (< elapsed 0) nil
            (< elapsed rack-ms) {:phase :rack :programs (programs world)}
            (< elapsed (+ rack-ms line-ms)) {:phase :loaded :program (own-agent world) :line (loaded-line world)}
            :else nil))))))
