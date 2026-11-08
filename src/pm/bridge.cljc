(ns pm.bridge
  "Pure rules for the local crew bridge: the Herdr roster, agent statuses,
   request validation, the allow/deny/say decision, replies, argv for
   babashka.process, and the log line. Portable (bb + cljs via the :test
   build); never required by pm.core."
  (:require [clojure.string :as str]))

(def host "127.0.0.1")
(def port 8091)
(def allowed-origin "http://localhost:8080")
(def max-text 1000)
(def max-body 4096)
(def actions #{"allow" "deny" "say"})
(def wait-ms 5000)
(def hold-uri "/hold")

(def ^:private name-re #"[a-z][a-z0-9_-]{0,31}")
(def ^:private uri-re #"^/crew/([a-z][a-z0-9_-]{0,31})$")
(def ^:private prompt-id-re #"^[0-9a-f]{40}$")
(def ^:private control-char-re #"[\x00-\x1f\x7f]")
(def ^:private hex-chars "0123456789abcdef")

(defn roster [crew-json]
  (if-not (and (map? crew-json) (sequential? (:members crew-json)))
    {}
    (into {}
          (keep (fn [m]
                  (let [nm (:name m)]
                    (when (and (map? m)
                               (true? (:enabled m))
                               (not (true? (:external m)))
                               (nil? (:machine m))
                               (string? nm)
                               (re-matches name-re nm))
                      (let [title (:title m)
                            title (if (and (string? title) (not (str/blank? title)) (<= (count title) 64))
                                    title
                                    nm)]
                        [(keyword nm) title])))))
          (:members crew-json))))

(defn agent-statuses [data]
  (into {}
        (keep (fn [a]
                (let [nm (:name a)]
                  (when (and (map? a) (string? nm) (re-matches name-re nm))
                    [(keyword nm) {:status (keyword (or (:agent_status a) "unknown"))}]))))
        (get-in data [:result :agents])))

(defn- byte->hex [b]
  (let [v (bit-and b 0xff)]
    (str (nth hex-chars (bit-shift-right v 4)) (nth hex-chars (bit-and v 0xf)))))

(defn token-hex [bytes]
  (apply str (map byte->hex bytes)))

(defn- char-code [s i]
  #?(:clj (int (.charAt ^String s i)) :cljs (.charCodeAt s i)))

(defn token= [a b]
  (boolean
    (and (string? a) (string? b) (= (count a) (count b))
         (zero? (reduce (fn [acc i] (bit-or acc (bit-xor (char-code a i) (char-code b i))))
                         0
                         (range (count a)))))))

(defn bridge-edn [token now]
  {:port port :token token :as-of now})

(defn cors-headers [origin]
  (if (= origin allowed-origin)
    {"Access-Control-Allow-Origin" allowed-origin "Vary" "Origin"}
    {}))

(defn preflight [origin]
  (if (= origin allowed-origin)
    {:status 204 :headers {"Access-Control-Allow-Origin" allowed-origin
                            "Access-Control-Allow-Methods" "POST"
                            "Access-Control-Allow-Headers" "content-type"
                            "Access-Control-Max-Age" "600"
                            "Vary" "Origin"}}
    {:status 403 :headers {} :body "bad origin"}))

(defn- validate-text [raw required?]
  (cond
    (and (some? raw) (not (string? raw))) {:error "bad text"}
    (and (string? raw) (re-find control-char-re raw)) {:error "control characters"}
    :else
    (let [t (when (string? raw) (str/trim raw))]
      (cond
        (or (nil? t) (str/blank? t)) (if required? {:error "text required"} {:text nil})
        (> (count t) max-text) {:error "text too long"}
        :else {:text t}))))

(defn validate [req ctx]
  (let [{:keys [method uri origin body]} req]
    (cond
      (not= method "POST")
      {:ok false :status 405 :reason "method not allowed"}

      (not (or (and (string? uri) (re-matches uri-re uri)) (= uri hold-uri)))
      {:ok false :status 404 :reason "not found"}

      (not (map? body))
      {:ok false :status 400 :reason "bad json"}

      (not (token= (get body "token") (:token ctx)))
      {:ok false :status 401 :reason "bad token"}

      (not= origin allowed-origin)
      {:ok false :status 403 :reason "bad origin"}

      (= uri hold-uri)
      (cond
        (not= (get body "action") "hold") {:ok false :status 400 :reason "bad action"}
        (not (boolean? (get body "on"))) {:ok false :status 400 :reason "bad on"}
        :else {:ok true :action :hold :on (get body "on")})

      :else
      (let [member (keyword (second (re-matches uri-re uri)))]
        (cond
          (not (contains? (:members ctx) member))
          {:ok false :status 404 :reason "unknown member"}

          (not (contains? actions (get body "action")))
          {:ok false :status 400 :reason "bad action"}

          :else
          (let [action (keyword (get body "action"))
                pid    (get body "prompt-id")]
            (if (and (contains? #{:allow :deny} action)
                     (not (and (string? pid) (re-matches prompt-id-re pid))))
              {:ok false :status 400 :reason "bad prompt-id"}
              (let [tr (case action
                         :allow {:text nil}
                         :deny  (validate-text (get body "text") false)
                         :say   (validate-text (get body "text") true))]
                (if (:error tr)
                  {:ok false :status 400 :reason (:error tr)}
                  {:ok true :member member :action action
                   :prompt-id (if (= action :say) nil pid)
                   :text (:text tr)})))))))))

(defn decide [v live]
  (let [{:keys [action prompt-id text]} v
        {:keys [status prompt]} live]
    (case action
      :allow
      (cond
        (not= status :blocked) {:ok false :status 409 :reason "not blocked"}
        (or (nil? prompt) (not= (:id prompt) prompt-id)) {:ok false :status 409 :reason "prompt changed"}
        (not= (:kind prompt) :bash) {:ok false :status 403 :reason "unknown prompt"}
        (:risky prompt) {:ok false :status 403 :reason "risky: your twin reviews this one"}
        :else {:ok true :steps [[:keys "1"]] :reason "allowed"})

      :deny
      (cond
        (not= status :blocked) {:ok false :status 409 :reason "not blocked"}
        (or (nil? prompt) (not= (:id prompt) prompt-id)) {:ok false :status 409 :reason "prompt changed"}
        :else {:ok true
               :steps (cond-> [[:keys "esc"]]
                        (some? text) (conj [:wait-idle wait-ms] [:prompt text]))
               :reason "declined"})

      :say
      (cond
        (contains? #{:idle :done} status) {:ok true :steps [[:prompt text]] :reason "sent"}
        (= status :blocked) {:ok false :status 409 :reason "blocked: answer its prompt first"}
        :else {:ok false :status 409 :reason "busy"}))))

(defn reply [decision origin]
  (let [headers (merge {"Content-Type" "application/json"} (cors-headers origin))]
    (if (:ok decision)
      {:status 200 :headers headers :body {"ok" true "result" (:reason decision)}}
      {:status (:status decision) :headers headers :body {"ok" false "reason" (:reason decision)}})))

(defn step-argv [herdr member step]
  (let [m (name member)]
    (case (first step)
      :keys      [herdr "agent" "send-keys" m (second step)]
      :wait-idle [herdr "agent" "wait" m "--until" "idle" "--until" "done" "--timeout" (str (second step))]
      ;; Herdr has no `--`; it reads a leading "-" as an option, so such text gets a space first.
      :prompt    [herdr "agent" "prompt" m (let [t (second step)] (if (re-find #"^-" t) (str " " t) t))]
      :read      [herdr "agent" "read" m "--source" "visible"]
      :list      [herdr "agent" "list"]
      nil)))

(defn- loggable [v]
  (cond
    (keyword? v) (name v)
    (string? v) v
    :else nil))

(defn log-line [iso member action status reason]
  (let [m (loggable member)
        a (loggable action)
        m' (cond (nil? member) "-" (and m (re-matches name-re m)) m :else "?")
        a' (cond (nil? action) "-" (and a (or (contains? actions a) (= a "hold"))) a :else "?")]
    (str iso " " m' " " a' " " status " " reason)))
