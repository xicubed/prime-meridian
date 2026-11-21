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

(defn- screen [pane-id]
  (or (herdr "pane" "read" pane-id "--source" "visible") ""))

(defn- to-hhmm
  "\"7pm\" / \"7:30pm\" / \"11am\" -> \"19:00\" / \"19:30\" / \"11:00\"."
  [s]
  (when-let [[_ h m ap] (re-find #"(?i)(\d{1,2})(?::(\d{2}))?\s*(am|pm)" (or s ""))]
    (let [h (Integer/parseInt h) h (mod h 12) h (if (= "pm" (str/lower-case ap)) (+ h 12) h)]
      (format "%02d:%s" h (or m "00")))))

(defn- section
  "The panel text from `start` up to the next heading in `stops`."
  [t start stops]
  (when-let [i (str/index-of t start)]
    (let [rest (subs t (+ i (count start)))
          j    (reduce (fn [m s] (if-let [k (str/index-of rest s)] (min m k) m)) (count rest) stops)]
      (subs rest 0 j))))

(defn- used+resets [sec]
  (when sec
    (let [[_ used]   (re-find #"(\d+)% used" sec)
          [_ resets] (re-find #"Resets ([^(]+?)\s*\(" sec)]
      (when used {:used (Integer/parseInt used) :resets (some-> resets str/trim)}))))

(defn parse-usage
  "The numbers out of Claude Code's /usage panel text; nil when it isn't one.
   The panel lays out differently with the pane's width (narrow: 'Current
   session · Resets 7pm' then the bar and '22% used'; wide: 'Current session',
   the bar with '37% used', then 'Resets 6:59pm'), so each section is read
   for its '% used' and 'Resets' wherever they fall."
  [text]
  (let [t    (str/replace text #"\s+" " ")
        s    (used+resets (section t "Current session" ["Current week" "What's contributing" "Usage credits"]))
        w    (used+resets (section t "Current week (all models)" ["Current week (" "What's contributing" "Usage credits"]))]
    (when (and s (:resets s))
      {:name "Claude" :tokens-used (:used s) :cap 100 :refill-at (to-hhmm (:resets s))
       :resets (:resets s)
       :week w})))

(defn probe!
  "One reading through `want` (or any idle Claude member). Returns the login map or nil."
  [want]
  (if-let [{:keys [name pane_id]} (idle-claude want)]
    (do
      (herdr "pane" "send-text" pane_id "/usage")
      (Thread/sleep 400)
      (herdr "pane" "send-keys" pane_id "enter")
      ;; the panel can take a few seconds to fetch; poll for it up to 12 s
      (let [u (loop [n 0]
                (Thread/sleep 1500)
                (or (parse-usage (screen pane_id))
                    (when (< n 8) (recur (inc n)))))]
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
