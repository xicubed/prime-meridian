(ns pm.construct
  "The Construct's five beats (GDD §2.7, §4.8). Beat 1 (`white-room`):
   a first-time player's jack-in lands in a silent white room; after a
   beat, the twin steps in and says its original opening line. Beat 2
   (`program-rack`): a rack of programs scrolls past, and your own agent --
   never mail, which many players can't connect on day one -- loads first
   as its sample version (GDD §4.6, §2.6), the moment its building drops
   out of the white sky. Beat 3 (`sparring`): the whole loop in miniature --
   a sample-badged worker takes a practice job, gets stuck, raises a '!',
   you answer, a blueprint of the rounder rock appears, and approving it
   changes the rock. Beat 4 (`jump-program`): the first pretend jack-out --
   the twin shows the hardline, the phone rings with a pretend chore, you
   jack out, and you jack back in. Beat 5 (`free-your-mind`): the white
   room dissolves into the half-barren estate -- only the buildings for
   the programs loaded so far -- and the twin steps back, done for good.
   Original lines, drafted fresh for this scene, not quoted from the film
   (the film is only the beat sheet). Pure: no
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
   player (`seen?` true). For a first-time player: {:phase :waiting
   :prompt ...} until the jack-in click (`enter`), then {:phase :silent} for `silent-ms`
   after entering, then {:phase :twin :line (opening-line world)} for `line-ms`
   more -- the moment the twin steps in and speaks -- then nil again (the
   first beat's done; the second, `program-rack`, picks up from there)."
  [world seen? now]
  (when-not seen?
    (if-not (:entered-at (:construct world))
      ;; before the jack-in click: the browser plays no sound until the
      ;; player's first click, so the Construct starts with one
      {:phase :waiting :prompt (:start (script world))}
      (when (number? now)
        (let [elapsed (- now (:entered-at (:construct world)))]
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
   :construct :loaded set. A no-op once `id` is already loaded, or with a
   bad `now`. (Its building lands later, in beat 5, where you can see it:
   `land-building`.)"
  [world id now]
  (if (or (contains? (get-in world [:construct :loaded]) id) (not (number? now)))
    world
    (update-in world [:construct :loaded] (fnil conj #{}) id)))

(defn land-building
  "Beat 5 (GDD §2.7): as the white room dissolves, your own agent's building
   drops out of the sky into the estate -- records :building-at `now` once
   (`pm.plan/building-drop-pose` times the fall). Only once your own agent
   is loaded; a no-op otherwise, once landed, or with a bad `now`."
  [world now]
  (if (and (number? now)
           (contains? (get-in world [:construct :loaded]) (:id (own-agent world)))
           (nil? (get-in world [:construct :building-at])))
    (assoc-in world [:construct :building-at] now)
    world))

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

;; Beat 3: the sparring match (GDD §2.1, §2.7, §3.1, §4.5, §4.8). "The
;; whole loop in miniature": a sample-badged worker takes a practice job
;; ("make that rock rounder"), gets stuck and raises a "!", the player
;; answers, a blueprint of the rounder rock appears, and approving it
;; changes the rock. Entirely on sample data -- no bridge, no real agent --
;; so it plays the same in a public clone.

(defn- sparring-script [world]
  (:sparring (script world)))

(defn job-line
  "The twin's line handing the practice job to the sample worker."
  [world]
  (:job (sparring-script world)))

(defn worker-name
  "The sample worker's name. It wears the SAMPLE badge (GDD §4.6) in the UI,
   so even this first '!' is honest about being practice."
  [world]
  (:name (:worker (sparring-script world))))

(defn working-line
  "The worker's status line while it's visibly working the practice rock."
  [world]
  (:working (sparring-script world)))

(defn ask-line
  "The worker's question, raised as a '!' once it gets stuck."
  [world]
  (:ask (sparring-script world)))

(defn blueprint-summary
  "The one-line summary on the rounder rock's blueprint, once it appears."
  [world]
  (:blueprint (sparring-script world)))

(defn rock-done-line
  "The twin's line the moment the rock changes, once the blueprint's approved."
  [world]
  (:done (sparring-script world)))

(def work-ms
  "How long the worker visibly works the practice rock (GDD §2.7) before it
   gets stuck and raises its '!'."
  1800)

(defn- beat1-done-at [world]
  (when-let [entered-at (:entered-at (:construct world))]
    (+ entered-at silent-ms line-ms)))

(defn- beat2-done-at [world]
  (when-let [b1 (beat1-done-at world)]
    (+ b1 rack-ms line-ms)))

(defn answer
  "Records `now` as the moment the player answered the worker's '!' (GDD
   §2.1, §2.7, §3.1): a no-op once already answered, once `seen?`, or with
   a bad `now`."
  [world seen? now]
  (if (or seen? (:answered-at (:construct world)) (not (number? now)))
    world
    (assoc-in world [:construct :answered-at] now)))

(defn approve-rock
  "Records `now` as the moment the player approved the rounder-rock
   blueprint (GDD §2.1, §2.7, §4.5): what `sparring`'s following {:phase
   :done} times -- the moment the rock changes in front of you. A no-op
   once already approved, before it's answered, once `seen?`, or with a
   bad `now`."
  [world seen? now]
  (if (or seen? (:approved-at (:construct world)) (nil? (:answered-at (:construct world)))
          (not (number? now)))
    world
    (assoc-in world [:construct :approved-at] now)))

(defn sparring
  "The Construct's third beat (GDD §2.1, §2.7, §3.1, §4.5, §4.8) at `now`,
   picking up right where `program-rack` leaves off -- the whole loop in
   miniature, on sample data only. nil for a returning player, before
   `program-rack` is done, or with a bad `now`. Then {:phase :job :line ...}
   while the twin hands the sample-badged worker the practice job,
   {:phase :working :worker ... :text ...} while it visibly works the
   rock, {:phase :ask :worker ... :prompt ...} once it gets stuck and
   raises its '!' -- waiting on `answer` -- then {:phase :blueprint
   :summary ...} once a blueprint of the rounder rock appears -- waiting
   on `approve-rock` -- then {:phase :done :line ...} for `line-ms`, the
   moment the rock changes, then nil again: the Construct's first three
   beats done."
  [world seen? now]
  (when-not seen?
    (when-let [start (beat2-done-at world)]
      (when (number? now)
        (let [elapsed (- now start)
              c       (:construct world)]
          (cond
            (< elapsed 0) nil
            (< elapsed line-ms) {:phase :job :line (job-line world)}
            (< elapsed (+ line-ms work-ms))
            {:phase :working :worker (worker-name world) :text (working-line world)}
            (nil? (:answered-at c))
            {:phase :ask :worker (worker-name world) :prompt (ask-line world)}
            (nil? (:approved-at c))
            {:phase :blueprint :summary (blueprint-summary world)}
            (< (- now (:approved-at c)) line-ms)
            {:phase :done :line (rock-done-line world)}
            :else nil))))))

;; Beat 4: the jump program (GDD §2.3, §2.7, §4.8) -- "the first pretend
;; jack-out." The twin shows the hardline (the phone booth) and the one
;; rule that matters, the phone rings with a pretend chore, you jack out,
;; and you jack back in. Self-contained like the earlier beats -- its own
;; pretend state in :construct, not `pm.world`'s real :jack -- so it stays
;; a Construct-only rep with nothing real at stake.

(defn- jump-script [world]
  (:jump (script world)))

(defn show-line
  "The twin's line showing the hardline and the one rule that matters."
  [world]
  (:show (jump-script world)))

(defn ring-line
  "The phone's line once it rings with the pretend chore."
  [world]
  (:ring (jump-script world)))

(defn out-line
  "The line shown while jacked out, pretend-frozen, waiting on `jump-in`."
  [world]
  (:out (jump-script world)))

(defn back-line
  "The twin's line the moment you jack back in."
  [world]
  (:back (jump-script world)))

(defn- beat3-done-at [world]
  (when-let [approved-at (:approved-at (:construct world))]
    (+ approved-at line-ms)))

(defn jump-out
  "Records `now` as the moment the player jacked out during the Construct's
   fourth beat (GDD §2.3, §2.7, §4.8), the first pretend jack-out: a no-op
   once already jumped out, once `seen?`, or with a bad `now`."
  [world seen? now]
  (if (or seen? (:jumped-out-at (:construct world)) (not (number? now)))
    world
    (assoc-in world [:construct :jumped-out-at] now)))

(defn jump-in
  "Records `now` as the moment the player jacked back in: what
   `jump-program`'s following {:phase :back} times. A no-op once already
   jumped back in, before `jump-out`, once `seen?`, or with a bad `now`."
  [world seen? now]
  (if (or seen? (:jumped-in-at (:construct world)) (nil? (:jumped-out-at (:construct world)))
          (not (number? now)))
    world
    (assoc-in world [:construct :jumped-in-at] now)))

(defn jump-program
  "The Construct's fourth beat (GDD §2.3, §2.7, §4.8) at `now`, picking up
   right where `sparring` leaves off: nil for a returning player, before
   `sparring` is done, or with a bad `now`. Then {:phase :show :line ...}
   for `line-ms` while the twin shows the hardline and says the one rule
   that matters, then {:phase :ring :prompt ...} while the phone rings
   with a pretend chore -- waiting on `jump-out` -- then {:phase :out
   :prompt ...} once you jack out -- waiting on `jump-in` -- then
   {:phase :back :line ...} for `line-ms`, the moment you jack back in,
   then nil again: the Construct's first four beats done."
  [world seen? now]
  (when-not seen?
    (when-let [start (beat3-done-at world)]
      (when (number? now)
        (let [elapsed (- now start)
              c        (:construct world)]
          (cond
            (< elapsed 0) nil
            (< elapsed line-ms) {:phase :show :line (show-line world)}
            (nil? (:jumped-out-at c)) {:phase :ring :prompt (ring-line world)}
            (nil? (:jumped-in-at c)) {:phase :out :prompt (out-line world)}
            (< (- now (:jumped-in-at c)) line-ms) {:phase :back :line (back-line world)}
            :else nil))))))

;; Beat 5: free your mind (GDD §2.7, §4.8) -- the Construct's last beat.
;; The white room dissolves into the half-barren estate, showing only the
;; buildings for the programs loaded so far (the scene underneath, which
;; the white room has sat on top of all along, never draws anything else),
;; and the twin steps back with its last line before leaving the player to
;; walk through the door themselves.

(defn- free-script [world]
  (:free (script world)))

(defn step-back-line
  "The twin's line as it steps back (GDD §2.7), the done-when's \"the twin
   steps back\"."
  [world]
  (:line (free-script world)))

(def dissolve-ms
  "How long the white room dissolves into the estate (GDD §2.7) before the
   twin's step-back line shows."
  1500)

(defn- beat4-done-at [world]
  (when-let [jumped-in-at (:jumped-in-at (:construct world))]
    (+ jumped-in-at line-ms)))

(defn free-your-mind
  "The Construct's fifth and final beat (GDD §2.7, §4.8) at `now`, picking
   up right where `jump-program` leaves off: nil for a returning player,
   before `jump-program` is done, or with a bad `now`. Then {:phase
   :dissolve} for `dissolve-ms` while the white room dissolves away --
   `pm.ui` fades it out, uncovering the estate underneath, already showing
   only the buildings for the programs loaded so far -- then {:phase :line
   :line (step-back-line world)} for `line-ms`, the twin's step-back line,
   then nil again: the Construct done for good."
  [world seen? now]
  (when-not seen?
    (when-let [start (beat4-done-at world)]
      (when (number? now)
        (let [elapsed (- now start)]
          (cond
            (< elapsed 0) nil
            (< elapsed dissolve-ms) {:phase :dissolve}
            (< elapsed (+ dissolve-ms line-ms)) {:phase :line :line (step-back-line world)}
            :else nil))))))
