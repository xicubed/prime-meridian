#!/usr/bin/env bb
;; Local bridge for the game's 💭: a tiny HTTP server on 127.0.0.1:8091 that turns an
;; allow/deny/say click in the browser into a Herdr send-keys/prompt against a crew member.
;; Writes state/bridge.edn {:port :token :as-of}, rewritten every 5 s, deleted on
;; shutdown. Only the game at http://localhost:8080, with that token, can reach it.
;;
;; The same server also speaks the bridge's websocket (GDD §4.1, §4.7, §3.1) at /events:
;; the four events -- agent-event, answer, chore-done, jack-out -- as JSON in both
;; directions. A connection is only upgraded for the game's own origin and the current
;; pairing token (carried in the query string, since a handshake has no body); anything
;; else gets a plain refusal and no upgrade. Demo mode (no real agents yet): on open it
;; sends the shipped sample events (resources/content/sample-events.edn) as JSON, and
;; acks whatever well-formed event the game sends back.
;;
;;   bb crew-bridge
;;
;; The bridge decides: it re-reads the member's live status and screen at request time and
;; recomputes the prompt (id, kind, risky) with pm.prompt, never trusting crew.edn or the
;; browser's claim. Every request is logged (time, member, action, result; never the token
;; or the text) to crew/bridge.log.

(require '[org.httpkit.server :as http]
         '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.edn :as edn]
         '[clojure.java.io :as io]
         '[clojure.string :as str]
         '[pm.bridge :as b]
         '[pm.events :as events]
         '[pm.prompt :as pr]
         '[pm.crew-log :as cl])

(import '[java.security SecureRandom]
        '[java.time Instant])

(def herdr (or (System/getenv "HERDR") "herdr"))
(def bridge-file "state/bridge.edn")
(def hold-file "state/hold.edn")
(def crew-log-file "state/crew-log.edn")
(def log-file "crew/bridge.log")
(def crew-json-file ".crew/crew.json")
(def rewrite-ms 5000)
(def ^:private req-lock (Object.))

(defn- repo-path []
  (.getCanonicalPath (io/file ".")))

(defn- random-token []
  (let [bytes (byte-array 32)]
    (.nextBytes (SecureRandom.) bytes)
    (b/token-hex bytes)))

(defn- write-bridge-file! [token]
  (let [f   (io/file bridge-file)
        tmp (io/file (str bridge-file ".tmp"))]
    (io/make-parents f)
    (spit tmp (pr-str (b/bridge-edn token (System/currentTimeMillis))))
    (try (.setReadable tmp false false) (.setReadable tmp true true)
         (.setWritable tmp false false) (.setWritable tmp true true)
         (catch Exception _))
    (.renameTo tmp f)))

(defn- delete-bridge-file! []
  (try (.delete (io/file bridge-file)) (catch Exception _)))

(defn- write-edn! [path data]
  (try
    (let [f   (io/file path)
          tmp (io/file (str path ".tmp"))]
      (io/make-parents f)
      (spit tmp (pr-str data))
      (.renameTo tmp f))
    (catch Exception _ false)))

(defn- crew-json []
  (try (json/parse-string (slurp crew-json-file) true) (catch Exception _ nil)))

(defn- log! [member action status reason]
  (try
    (io/make-parents (io/file log-file))
    (spit log-file (str (b/log-line (str (Instant/now)) member action status reason) "\n") :append true)
    (catch Exception _)))

(defn- live-for
  "The member's status and (when blocked) its prompt, read right now."
  [member]
  (let [{:keys [exit out]} (p/sh (b/step-argv herdr member [:list]))
        status (if (zero? exit)
                 (:status (get (b/agent-statuses (json/parse-string out true)) member {:status :unknown}))
                 :unknown)]
    {:status status
     :prompt (when (= status :blocked)
               (let [{:keys [exit out]} (p/sh (b/step-argv herdr member [:read]))]
                 (when (zero? exit) (pr/read-prompt out (repo-path)))))}))

(defn- append-crew-log!
  "Appends an allow/deny outcome to state/crew-log.edn. A failure is logged
   and never changes the bridge's reply."
  [member action prompt]
  (try
    (let [cur  (cl/parse (try (slurp crew-log-file) (catch Exception _ nil)))
          e    (cl/entry (System/currentTimeMillis) member action :player prompt)
          log' (cl/append cur e)]
      (when-not (write-edn! crew-log-file log')
        (throw (ex-info "write failed" {}))))
    (catch Exception ex
      (log! member action nil (str "crew-log not saved: " (.getMessage ex))))))

(defn- run-decided!
  "Runs decision's steps in order against member. A failed :wait-idle skips
   the :prompt step that follows it but still counts as success."
  [member decision]
  (loop [steps (:steps decision) skip-prompt? false]
    (cond
      (empty? steps) {:ok true :reason (if skip-prompt?
                                         "declined; note not sent (still busy)"
                                         (:reason decision))}

      (and skip-prompt? (= :prompt (first (first steps))))
      (recur (rest steps) skip-prompt?)

      :else
      (let [step       (first steps)
            kind       (first step)
            {:keys [exit]} (p/sh (b/step-argv herdr member step))
            succeeded? (zero? exit)]
        (cond
          (and (= kind :wait-idle) (not succeeded?))
          (recur (rest steps) true)

          (not succeeded?)
          {:ok false :status 502 :reason "herdr failed"}

          :else
          (recur (rest steps) skip-prompt?))))))

(defn- member-from-uri [uri]
  (second (re-matches #"^/crew/([a-z][a-z0-9_-]{0,31})$" (or uri ""))))

(defn- query-params
  "A websocket handshake carries its token in the query string, not a JSON
   body; a tiny decoder since babashka has no ring-codec."
  [qs]
  (into {}
        (keep (fn [kv]
                (when (seq kv)
                  (let [[k v] (str/split kv #"=" 2)]
                    (when (seq k)
                      [k (try (java.net.URLDecoder/decode (or v "") "UTF-8")
                              (catch Exception _ (or v "")))])))))
        (str/split (or qs "") #"&")))

(defn- sample-ws-events
  "Demo mode (GDD §4.8): until a real agent connects, the shipped sample
   events are what the bridge sends the game on open, so the websocket
   transport is something you can see working even with zero accounts."
  []
  (try (events/valid-events (edn/read-string (slurp "resources/content/sample-events.edn")))
       (catch Exception _ [])))

(defn- handle-ws
  "The events websocket (GDD §4.1, §4.7): accepted only for the game's own
   origin and the current pairing token; anything else is a plain refused
   response, never an upgrade."
  [token req]
  (let [origin (get-in req [:headers "origin"])
        qtoken (get (query-params (:query-string req)) "token")
        v      (b/validate-ws {:uri (:uri req) :origin origin :token qtoken} {:token token})]
    (if-not (:ok v)
      (do (log! nil :ws (:status v) (:reason v))
          {:status (:status v) :headers (merge {"Content-Type" "text/plain"} (b/cors-headers origin))
           :body (:reason v)})
      (http/as-channel req
        {:on-open
         (fn [ch]
           (log! nil :ws 101 "open")
           (doseq [ev (sample-ws-events)]
             (http/send! ch (json/generate-string (events/event->json ev)))))
         :on-receive
         (fn [ch msg]
           (let [m  (try (json/parse-string msg) (catch Exception _ nil))
                 ev (events/json->event m)]
             (if (events/valid-event? ev)
               (do (log! nil :ws 200 (str "recv " (name (events/event-kind ev))))
                   (http/send! ch (json/generate-string {"ok" true})))
               (do (log! nil :ws 400 "bad event")
                   (http/send! ch (json/generate-string {"ok" false "reason" "bad event"}))))))
         :on-close
         (fn [_ch _status] (log! nil :ws 0 "closed"))}))))

(defn- handle [token req]
  (if (= (:uri req) b/events-uri)
    (handle-ws token req)
    (locking req-lock
      (let [origin (get-in req [:headers "origin"])
            method (str/upper-case (name (:request-method req)))]
        (if (= method "OPTIONS")
        (let [r (b/preflight origin)]
          (log! nil nil (:status r) "preflight")
          {:status (:status r) :headers (:headers r) :body (or (:body r) "")})
        (let [body-str (slurp (:body req))]
          (if (> (count (.getBytes ^String body-str "UTF-8")) b/max-body)
            (do (log! (member-from-uri (:uri req)) nil 413 "too large")
                {:status 413 :headers {"Content-Type" "application/json"}
                 :body (json/generate-string {"ok" false "reason" "too large"})})
            (let [body (try (json/parse-string body-str) (catch Exception _ :bad-json))
                  ctx  {:token token :members (set (keys (b/roster (crew-json))))}
                  req' {:method method :uri (:uri req) :origin origin :body body}
                  v    (b/validate req' ctx)]
              (if-not (:ok v)
                (let [r (b/reply v origin)]
                  (log! (member-from-uri (:uri req)) (get body "action") (:status r) (:reason v))
                  {:status (:status r) :headers (:headers r) :body (json/generate-string (:body r))})
                (if (= :hold (:action v))
                  (if (write-edn! hold-file (cl/hold-edn (:on v) (System/currentTimeMillis)))
                    (let [result (if (:on v) "on" "off")]
                      (log! nil :hold 200 result)
                      {:status 200 :headers (merge {"Content-Type" "application/json"} (b/cors-headers origin))
                       :body (json/generate-string {"ok" true "result" result})})
                    (do
                      (log! nil :hold 500 "hold not saved")
                      {:status 500 :headers (merge {"Content-Type" "application/json"} (b/cors-headers origin))
                       :body (json/generate-string {"ok" false "reason" "hold not saved"})}))
                  (let [live     (live-for (:member v))
                        decision (b/decide v live)]
                    (if-not (:ok decision)
                      (let [r (b/reply decision origin)]
                        (log! (:member v) (:action v) (:status r) (:reason decision))
                        {:status (:status r) :headers (:headers r) :body (json/generate-string (:body r))})
                      (let [ran    (run-decided! (:member v) decision)
                            reason (if (:ok ran) (:reason decision) (:reason ran))
                            final  (if (:ok ran) decision ran)
                            r      (b/reply (assoc final :reason reason) origin)]
                        (log! (:member v) (:action v) (:status r) reason)
                        (when (and (:ok ran) (contains? #{:allow :deny} (:action v)))
                          (append-crew-log! (:member v) (if (= :allow (:action v)) :allowed :declined) (:prompt live)))
                        {:status (:status r) :headers (:headers r) :body (json/generate-string (:body r))})))))))))))))

(defn -main [& _]
  (let [token (random-token)]
    (write-bridge-file! token)
    (let [rewrite (future
                    (loop []
                      (Thread/sleep rewrite-ms)
                      (write-bridge-file! token)
                      (recur)))]
      (.addShutdownHook (Runtime/getRuntime)
                         (Thread. (fn [] (future-cancel rewrite) (delete-bridge-file!))))
      (try
        (http/run-server (partial handle token) {:ip b/host :port b/port :max-body b/max-body :legacy-return-value? false})
        (println (str "bb crew-bridge: listening on " b/host ":" b/port))
        @(promise)
        (catch Exception e
          (println (str "bb crew-bridge: could not start (" (.getMessage e) ")"))
          (System/exit 1))))))

(apply -main *command-line-args*)
