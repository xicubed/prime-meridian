#!/usr/bin/env bb
;; hal-say (GDD §2.1, TASKS T-1, T-2): an agent speaks on the estate.
;;
;;   bb hal-say "line" [--as <member>] [--at <where>]
;;
;; With --at, it's an errand: the walker first goes to <where> (another member's name,
;; mailbox, phone-booth, lobby) and says the line there, then carries on.
;;
;; Sends a :say agent-event to the local bridge (bb crew-bridge), which relays it to the
;; game: the line shows on a speech plate over that walker for a few seconds, and lands
;; as a quiet line in that member's own 💭 thread too (pm.talk/observe-says) -- so a
;; --as <member> line is never speech-plate-only. When the speaker is the twin (the
;; default, the estate's twin walker), the line is also his caption, spoken when voice
;; is on. No answer is waited for.

(require '[babashka.http-client.websocket :as ws]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[pm.bridge :as b]
         '[pm.events :as events])

(defn- usage! []
  (binding [*out* *err*] (println "usage: bb hal-say \"line\" [--as <member>] [--at <where>]"))
  (System/exit 1))

(defn- twin-agent
  "The estate's twin walker's :agent name: the walker whose :name is the twin's."
  []
  (let [estate (edn/read-string (slurp "resources/estate.edn"))
        twin   (get-in estate [:jumeaux :twin :name])]
    (some->> (:walkers estate) (filter #(= twin (:name %))) first :agent name)))

(let [[line & more] *command-line-args*
      as (second (drop-while #(not= "--as" %) more))
      at (second (drop-while #(not= "--at" %) more))]
  (when (or (empty? line) (> (count line) 1000)) (usage!))
  (let [f (io/file "state/bridge.edn")]
    (when-not (.exists f)
      (binding [*out* *err*] (println "hal-say: the bridge isn't running (bb crew-bridge)"))
      (System/exit 1))
    (let [agent (or as (twin-agent))
          _     (when (nil? agent)
                  (binding [*out* *err*] (println "hal-say: no twin walker in the estate; use --as <member>"))
                  (System/exit 1))
          {:keys [port token]} (edn/read-string (slurp f))
          sock (ws/websocket {:uri (str "ws://127.0.0.1:" port b/events-uri "?token=" token)
                              :headers {"Origin" b/allowed-origin}})
          ev   (cond-> {:type :agent-event :agent agent :kind (if at :errand :say) :text line
                        :source {:agent agent :session "hal-say" :machine "local"}}
                 at (assoc :where at))]
      (when-not (events/valid-event? ev)
        (binding [*out* *err*] (println "hal-say: not a valid say event")) (System/exit 1))
      (ws/send! sock (json/generate-string (events/event->json ev)))
      (Thread/sleep 300)
      (ws/close! sock)
      (println (str agent (if at (str " goes to " at " and says: ") " says: ") line)))))
