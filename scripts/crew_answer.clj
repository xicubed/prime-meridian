#!/usr/bin/env bb
;; The twin's own half of a prompt the fielder left alone (risky or unreadable):
;; crew_fielder.clj forwards it into the twin's Hermes chat with a line like this
;; script's usage; this answers it through the bridge, with the same prompt-id check
;; as the game's own Allow/Always/No (stale if the dialog has changed since).
;;
;;   bb crew-answer <member> allow|always|deny [--note "…"]
;;
;; Reads state/crew-forwarded.edn for the forwarded prompt's id (falling back to
;; state/crew.edn's current one), posts to the bridge as :by "twin", and prints the
;; result. Never reads a screen itself -- the bridge re-reads and re-decides, as
;; always.

(require '[babashka.http-client :as http]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[pm.bridge :as b]
         '[pm.fielder :as f])

(defn- slurp-or-nil [path]
  (try (slurp path) (catch Exception _ nil)))

(defn- read-edn [path]
  (try (edn/read-string (slurp-or-nil path)) (catch Exception _ nil)))

(defn- fail! [text]
  (binding [*out* *err*] (println (str "crew-answer: " text)))
  (System/exit 1))

(let [r (f/answer-request (vec *command-line-args*)
                           (read-edn "state/crew.edn")
                           (f/parse-forwarded (slurp-or-nil "state/crew-forwarded.edn")))]
  (if-not (:ok r)
    (fail! (:reason r))
    (let [bridge (read-edn "state/bridge.edn")]
      (if-not (map? bridge)
        (fail! "the bridge isn't running (bb crew-bridge)")
        (let [{:keys [port token]} bridge
              resp (http/post (str "http://127.0.0.1:" port "/crew/" (name (:member r)))
                               {:headers {"Origin" b/allowed-origin "Content-Type" "application/json"}
                                :body (json/generate-string (f/answer-body r token))
                                :throw false})
              body (try (json/parse-string (:body resp) true) (catch Exception _ nil))]
          (if (= 200 (:status resp))
            (println (str (name (:member r)) ": " (:result body)))
            (fail! (str (name (:member r)) ": " (:reason body)
                        (when (= 409 (:status resp)) " (stale: the dialog changed; nothing was sent)")))))))))
