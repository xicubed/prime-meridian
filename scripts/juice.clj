#!/usr/bin/env bb
;; juice (GDD §2.1, §4.4): the real usage meter, read the way a person would.
;;
;;   bb juice [--watch [SECONDS]] [--member NAME]
;;
;; The crew are Claude Code sessions on this machine, on the same plan as the player,
;; so one of them can be asked: this types `/usage` into an idle member's pane, reads
;; the panel ("Current session · Resets 7pm", "22% used"), closes it with Escape, and
;; writes state/juice.edn (gitignored, served same-origin by pm.dev-http):
;;   {:as-of <ms> :logins {:claude {:name "Claude" :tokens-used 22 :cap 100 :refill-at "19:00"
;;                                  :week {:used 19 :resets "Nov 26 at 2pm"}}}}
;; The game shows it as REAL while the file is fresh, the sample otherwise. --watch
;; repeats every SECONDS (default 300) and skips a tick when nobody is idle.

(require '[babashka.process :as p]
         '[cheshire.core :as json]
         '[clojure.java.io :as io]
         '[clojure.string :as str])

(def out-file "state/juice.edn")

(defn- herdr [& args]
  (let [{:keys [out exit]} (p/sh (into ["herdr"] args))]
    (when (zero? exit) (str out))))

(defn- agents []
  (some-> (herdr "agent" "list") (json/parse-string true) :result :agents))

(defn- idle-claude
  "An idle Claude member's agent entry (name + pane_id), preferring `want`."
  [want]
  (let [as (filter #(and (:pane_id %) (not= "hermes" (some-> (:kind %) str/lower-case))
                         (contains? #{nil "idle" "done"} (some-> (:status %) str/lower-case)))
                   (agents))]
    (or (first (filter #(= want (:name %)) as)) (first as))))

(defn- screen [member]
  (or (herdr "agent" "read" member "--source" "visible") ""))

(defn- to-hhmm
  "\"7pm\" / \"7:30pm\" / \"11am\" -> \"19:00\" / \"19:30\" / \"11:00\"."
  [s]
  (when-let [[_ h m ap] (re-find #"(?i)(\d{1,2})(?::(\d{2}))?\s*(am|pm)" (or s ""))]
    (let [h (Integer/parseInt h) h (mod h 12) h (if (= "pm" (str/lower-case ap)) (+ h 12) h)]
      (format "%02d:%s" h (or m "00")))))

(defn parse-usage
  "The numbers out of Claude Code's /usage panel text; nil when it isn't one."
  [text]
  (let [t (str/replace text #"\s+" " ")]
    (when-let [[_ resets] (re-find #"Current session · Resets ([^(]+?) \(" t)]
      (let [[_ used] (re-find #"Current session · Resets [^(]+?\([^)]*\) [█▏▎▍▌▋▊▉ ]* (\d+)% used" t)
            [_ wresets] (re-find #"Current week \(all models\) · Resets ([^(]+?) \(" t)
            [_ wused] (re-find #"Current week \(all models\) · Resets [^(]+?\([^)]*\) [█▏▎▍▌▋▊▉ ]* (\d+)% used" t)]
        (when used
          {:name "Claude" :tokens-used (Integer/parseInt used) :cap 100 :refill-at (to-hhmm resets)
           :resets (str/trim resets)
           :week (when wused {:used (Integer/parseInt wused) :resets (some-> wresets str/trim)})})))))

(defn probe!
  "One reading through `want` (or any idle Claude member). Returns the login map or nil."
  [want]
  (if-let [{:keys [name pane_id]} (idle-claude want)]
    (do
      (herdr "pane" "send-text" pane_id "/usage")
      (Thread/sleep 400)
      (herdr "pane" "send-keys" pane_id "enter")
      (Thread/sleep 5000)
      (let [u (parse-usage (screen name))]
        (herdr "pane" "send-keys" pane_id "escape")
        (if u
          (do (io/make-parents out-file)
              (spit out-file (pr-str {:as-of (System/currentTimeMillis) :via name :logins {:claude u}}))
              (println (str "juice: Claude " (:tokens-used u) "% used till " (:refill-at u) " (via " name ")"))
              u)
          (do (binding [*out* *err*] (println (str "juice: couldn't read the /usage panel on " name))) nil))))
    (do (binding [*out* *err*] (println "juice: no idle Claude member to ask")) nil)))

(let [args   (vec *command-line-args*)
      watch? (some #{"--watch"} args)
      every  (or (some-> (second (drop-while #(not= "--watch" %) args)) parse-long) 300)
      want   (second (drop-while #(not= "--member" %) args))]
  (if watch?
    (loop []
      (try (probe! want) (catch Exception e (binding [*out* *err*] (println "juice:" (.getMessage e)))))
      (Thread/sleep (* 1000 every))
      (recur))
    (System/exit (if (probe! want) 0 1))))
