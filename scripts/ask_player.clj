#!/usr/bin/env bb
;; ask_player (GDD §3.1): the one way a crew member raises the estate's "!".
;;
;;   bb ask-player <member> "question" [--wait SECONDS]
;;
;; Sends an ask_player question to the local bridge (bb crew-bridge), which relays it to
;; the game: the "!" goes up over that member's walker. Then it waits for the player's
;; answer and prints it, so an agent that runs it simply carries on with the answer
;; (exit 0). If no answer comes within --wait seconds (default 600), it says so and
;; exits 2; the answer still reaches the member later, typed into its pane by the bridge
;; once it's idle.

(require '[babashka.http-client.websocket :as ws]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[pm.bridge :as b]
         '[pm.events :as events])

(defn- usage! []
  (binding [*out* *err*]
    (println "usage: bb ask-player <member> \"question\" [--wait SECONDS]"))
  (System/exit 1))

(let [[member question & more] *command-line-args*
      wait-s (or (some-> (second (drop-while #(not= "--wait" %) more)) parse-long) 600)]
  (when (or (empty? member) (empty? question)) (usage!))
  (let [f (io/file "state/bridge.edn")]
    (when-not (.exists f)
      (binding [*out* *err*] (println "ask-player: the bridge isn't running (bb crew-bridge)"))
      (System/exit 1))
    (let [{:keys [port token]} (edn/read-string (slurp f))
          answer (promise)
          sock   (ws/websocket
                  {:uri     (str "ws://127.0.0.1:" port b/events-uri "?token=" token)
                   :headers {"Origin" b/allowed-origin}
                   :on-message (fn [_ws data _last]
                                 (let [ev (some-> (try (json/parse-string (str data)) (catch Exception _ nil))
                                                  events/json->event)]
                                   (when (and (= :answer (:type ev)) (= member (:agent ev)))
                                     (deliver answer (:text ev)))))})
          ev     {:type :agent-event :agent member :kind :question :text question
                  :source {:agent member :session "ask-player" :machine "local"}}]
      (when-not (events/valid-event? ev)
        (binding [*out* *err*] (println "ask-player: not a valid question event")) (System/exit 1))
      (ws/send! sock (json/generate-string (events/event->json ev)))
      (let [a (deref answer (* 1000 wait-s) ::none)]
        (ws/close! sock)
        (if (= ::none a)
          (do (println (str "No answer yet after " wait-s " s. It will be typed into your pane when it comes."))
              (System/exit 2))
          (do (println a) (System/exit 0)))))))
