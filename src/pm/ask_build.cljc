(ns pm.ask-build
  "The demo's pretend twin builds what you ask for in 💭 (GDD: every
   connection has an in-game pretend version; demo build only): a typed or
   spoken request is parsed from estate data, a spot is found with
   `pm.lot/place`, and the result is kept as a demo \"file\" that reaches
   the world through the existing blueprint-file path. Portable (bb +
   cljs) and pure: no atoms, no I/O, no `js/Date`, no three.js. Reader
   conditionals only for maths and reading EDN, the way `pm.lot` does. No
   person, pet or member is named anywhere in it."
  (:require [clojure.string :as str]
            [pm.lot :as lot]
            [pm.blueprint-file :as bf]
            #?(:clj [clojure.edn :as edn] :cljs [cljs.reader :as reader])))

(def ^:private eps 1e-9)
(defn- floor* [x] #?(:clj (Math/floor x) :cljs (js/Math.floor x)))
(defn- ceil*  [x] #?(:clj (Math/ceil x)  :cljs (js/Math.ceil x)))
(defn- round* [x] #?(:clj (Math/round (double x)) :cljs (js/Math.round x)))

;; -- The request parser (text -> {:look :near} | {:help true}) ------------

(defn- tokenize [s]
  (if (string? s)
    (vec (remove str/blank? (str/split (str/lower-case s) #"[^a-z0-9]+")))
    []))

(defn normalize-text
  "\" \" + the lower-cased a-z0-9 tokens of `s`, space-joined, + \" \"; a
   non-string or blank `s` gives \" \". Letters outside a-z split words;
   that's a known limit."
  [s]
  (if (and (string? s) (not (str/blank? s)))
    (str " " (str/join " " (tokenize s)) " ")
    " "))

(defn- normalize-phrase [s]
  (str/join " " (tokenize (str s))))

(defn find-word
  "The keyword `table` (phrase -> keyword) maps to the phrase that starts
   earliest in `text` (as a whole word/phrase); on a tie the longer phrase
   wins, then the smaller keyword name. nil when nothing matches."
  [table text]
  (let [norm (normalize-text text)
        hits (keep (fn [[phrase kw]]
                      (let [p   (normalize-phrase phrase)
                            idx (str/index-of norm (str " " p " "))]
                        (when idx [idx (- (count p)) (name kw) kw])))
                    table)]
    (when (seq hits)
      (peek (first (sort hits))))))

(defn parse-request
  "`demo` is the `:demo-builds` map. `{:help true}` with no look word in
   `text`, else `{:look <it> :near (or <place word> :here)}`."
  [demo text]
  (if-let [look (find-word (:words demo) text)]
    {:look look :near (or (find-word (:places demo) text) :here)}
    {:help true}))

;; -- Ids, targets, the measuring grid --------------------------------------

(defn taken-ids
  "Every id in play: `world`'s and `file`'s blueprints, and every keyword
   in `file`'s `:real` and `:removed`."
  [world file]
  (set (concat (map :id (:blueprints world))
               (map :id (:blueprints file))
               (filter keyword? (:real file))
               (filter keyword? (:removed file)))))

(defn next-id
  "`<look>-<n>` for the smallest n >= 1 not in `taken`."
  [look taken]
  (loop [n 1]
    (let [id (keyword (str (name look) "-" n))]
      (if (contains? taken id) (recur (inc n)) id))))

(defn- valid-xz3? [v]
  (and (vector? v) (= 3 (count v)) (every? number? v)))

(defn- valid-xz2? [v]
  (and (vector? v) (= 2 (count v)) (every? number? v)))

(defn target-xz
  "The [x z] to measure around: `here` (a ground hit [x y z]) for `:here`;
   a keyword's own `:at` on `estate`; else `demo`'s `:anchors`. nil when
   none of those resolve."
  [estate demo near here]
  (if (= near :here)
    (when (valid-xz3? here) [(nth here 0) (nth here 2)])
    (let [at (:at (get estate near))]
      (if (valid-xz3? at)
        [(nth at 0) (nth at 2)]
        (let [a (get-in demo [:anchors near])]
          (when (valid-xz2? a) a))))))

(defn grid-spec
  "{:origin [x0 z0] :step s :cols c :rows r} covering `(:search-m rules) +
   pad-m` around [tx tz], on `(:step-m rules)`'s grid -- the origin sits on
   the step grid, so `lot/place`'s candidates land exactly on cells."
  [[tx tz] rules pad-m]
  (let [s  (:step-m rules)
        h  (+ (:search-m rules) pad-m)
        x0 (* s (floor* (+ (/ (- tx h) s) eps)))
        x1 (* s (ceil*  (- (/ (+ tx h) s) eps)))
        z0 (* s (floor* (+ (/ (- tz h) s) eps)))
        z1 (* s (ceil*  (- (/ (+ tz h) s) eps)))]
    {:origin [x0 z0] :step s
     :cols (inc (round* (/ (- x1 x0) s)))
     :rows (inc (round* (/ (- z1 z0) s)))}))

;; -- The sampler (two rays a cell -> {:kind :y}) ---------------------------

(defn name-has?
  "True when `name` is a string containing any of `needles` (case-insensitive
   substring)."
  [needles name]
  (boolean (and (string? name) (seq needles)
                (let [lname (str/lower-case name)]
                  (some #(str/includes? lname (str/lower-case (str %))) needles)))))

(defn cover-name? [sampler name]
  (name-has? (:cover sampler) name))

(defn cell-kind
  "The ground+cover hits `g` `c` (each [y name] or nil; a nil `c` counts as
   `g`), classified: no ground \" \"; water \"~\"; rocks \"R\"; gravel \":\";
   past `:tree-m` \"T\"; past `:bush-m\" \"B\"; else \"o\"."
  [sampler g c]
  (let [c (or c g)]
    (cond
      (nil? g) " "
      (name-has? (:water sampler) (second c)) "~"
      (name-has? (:rocks sampler) (second c)) "R"
      (or (name-has? (:gravel sampler) (second c)) (name-has? (:gravel sampler) (second g))) ":"
      :else
      (let [h (- (first c) (first g))]
        (cond
          (> h (+ (:tree-m sampler) eps)) "T"
          (>= h (- (:bush-m sampler) eps)) "B"
          :else "o")))))

(defn sample-cell
  "{:kind (cell-kind ...) :y (g's height, rounded to the cm, or nil)}."
  [sampler g c]
  {:kind (cell-kind sampler g c)
   :y (when g (/ (round* (* 100 (first g))) 100))})

;; -- Text and the look estate -----------------------------------------------

(defn fill
  "`{word}` placeholders in `s` filled from `vars` (keywords); a missing or
   nil var keeps the placeholder. A non-string `s` gives nil. Same
   behaviour as `pm.content/fill`, re-done here because this ns is cljc."
  [s vars]
  (when (string? s)
    (str/replace s #"\{([a-z0-9-]+)\}"
                 (fn [[whole field]]
                   (let [v (get vars (keyword field))]
                     (if (nil? v) whole (str v)))))))

(defn lot-estate
  "`estate` with `:blueprints` replaced by `world`'s blueprints that are
   `bf/valid?` and still in play (`:pending :approved :real :removing`):
   rejected and removed builds don't block a spot; ghosts and builds do."
  [estate world]
  (assoc estate :blueprints
         (filterv (fn [bp] (and (bf/valid? bp)
                                 (contains? #{:pending :approved :real :removing} (or (:status bp) :pending))))
                  (:blueprints world))))

;; -- ask / plan: the two steps of one request ------------------------------

(declare add-build)

(defn ask
  "The step before measuring: help or sky/refused, or `{:req :target
   :spec}` ready for `ground-grid` + `plan`."
  [ctx text]
  (let [estate (:estate ctx)
        demo   (:demo-builds estate)
        req    (parse-request demo text)]
    (if (:help req)
      {:say (get-in demo [:lines :help])}
      (let [near (:near req)
            xz   (target-xz estate demo near (:here ctx))]
        (cond
          (and (nil? xz) (= near :here))
          {:say (get-in demo [:lines :sky])}

          (nil? xz)
          {:say (fill (get-in demo [:lines :refused])
                      {:reason (str "I don't know a place called " (name near))})}

          :else
          (let [rules (:rules ctx)
                pad-m (get-in demo [:sampler :pad-m])]
            (cond-> {:req req :target xz :spec (grid-spec xz rules pad-m)}
              (> (lot/max-search-m rules) (:search-m rules))
              (assoc :wide-spec (grid-spec xz (assoc rules :search-m (lot/max-search-m rules)) pad-m)))))))))

(defn plan
  "The step after measuring: a new ghost in the world and the `:placed`
   line, or `:ok false` and why not (`:refused`, or `:help` for an unknown
   look)."
  [ctx req ground]
  (let [{:keys [estate world file rules here by now]} ctx
        demo     (:demo-builds estate)
        look     (:look req)
        near     (:near req)
        look-def (get-in demo [:looks look])]
    (if (nil? look-def)
      {:ok false :say (get-in demo [:lines :help])}
      (let [id    (next-id look (taken-ids world file))
            draft {:id id :summary (:summary look-def) :look (:look look-def)}
            est   (lot-estate estate world)
            seen  (when (= near :here) {:ok true :view {:center here :walkers {}}})
            res   (lot/place ground est [] rules draft near seen)]
        (if (:ok res)
          (let [bp     (assoc (lot/blueprint draft res) :by by)
                word   (:word (get (lot/landmarks ground est [] (:view seen)) near))
                placed (fill (get-in demo [:lines :placed])
                             {:summary (str/replace (:summary look-def) #"\.$" "") :place word})
                moved  (lot/moved-line (get-in estate [:blueprint-text :moved]) word res)]
            {:ok true :id id :blueprint bp :file (add-build file bp now)
             :say (if moved (str moved " " placed) placed)})
          {:ok false :say (fill (get-in demo [:lines :refused]) {:reason (:reason res)})
           :reason (:reason res)})))))

(defn here?
  "True iff `asked` (ask's result) is a request for :here -- the build
   goes exactly at the view's centre, with no measuring (placement-v2). A
   help or sky result (no :req) gives false."
  [asked]
  (= :here (get-in asked [:req :near])))

(defn plan-here
  "Show me (placement-v2): a request for :here places exactly at `ctx`'s
   `:here` (a ground hit [x y z]), facing `ctx`'s `:camera` ([x y z] or
   nil) -- no measuring, no `lot/place`. Same result shapes as `plan`."
  [ctx req]
  (let [{:keys [estate world file by now here camera]} ctx
        demo     (:demo-builds estate)
        look     (:look req)
        look-def (get-in demo [:looks look])]
    (cond
      (nil? look-def)
      {:ok false :say (get-in demo [:lines :help])}

      (not (valid-xz3? here))
      {:ok false :say (get-in demo [:lines :sky])}

      :else
      (let [id    (next-id look (taken-ids world file))
            draft {:id id :summary (:summary look-def) :look (:look look-def)}
            bp    (assoc (lot/blueprint draft {:at (vec here) :yaw (lot/face-yaw here camera)}) :by by)]
        {:ok true :id id :blueprint bp :file (add-build file bp now)
         :say (fill (get-in demo [:lines :placed])
                    {:summary (str/replace (:summary look-def) #"\.$" "") :place lot/here-word})}))))

(defn widen?
  "Measure the wide grid and plan again? True iff `asked` (ask's result) has
   a `:wide-spec` and `p1` (plan's result on the near grid) refused for
   lack of ground (its `:reason` starts with lot/refusal-prefix), not for
   an unknown look or landmark."
  [asked p1]
  (boolean (and (map? (:wide-spec asked))
                (false? (:ok p1))
                (string? (:reason p1))
                (str/starts-with? (:reason p1) lot/refusal-prefix))))

;; -- The demo file (localStorage pm.demo-builds.v1) ------------------------

(def empty-file {:as-of 0 :blueprints [] :real [] :removed []})

(defn- distinct-by-id [bps]
  (first (reduce (fn [[out seen] bp]
                    (if (contains? seen (:id bp))
                      [out seen]
                      [(conj out bp) (conj seen (:id bp))]))
                  [[] #{}] bps)))

(defn normalize-file
  "A non-map gives `empty-file`. `:as-of` kept when an integer, else 0.
   `:blueprints` keeps only `bf/valid?` maps, first per id, in order.
   `:real`/`:removed` keep only keywords, distinct, in order. Other keys
   dropped."
  [f]
  (if-not (map? f)
    empty-file
    {:as-of (if (integer? (:as-of f)) (:as-of f) 0)
     :blueprints (distinct-by-id (filter bf/valid? (if (sequential? (:blueprints f)) (:blueprints f) [])))
     :real (vec (distinct (filter keyword? (if (sequential? (:real f)) (:real f) []))))
     :removed (vec (distinct (filter keyword? (if (sequential? (:removed f)) (:removed f) []))))}))

(defn save-text [f]
  (pr-str (normalize-file f)))

(defn restore
  "`(normalize-file <read-string of text>)`; nil, \"\" and garbage give
   `empty-file`. Never throws."
  [text]
  (normalize-file
    (try
      #?(:clj (edn/read-string text) :cljs (reader/read-string text))
      (catch #?(:clj Exception :cljs :default) _ nil))))

(defn add-build
  "`file` with `bp` added, once: already-present id -> unchanged. Else
   `conj`s `bp` and bumps `:as-of`."
  [file bp now]
  (let [f (normalize-file file)]
    (if (some #(= (:id bp) (:id %)) (:blueprints f))
      f
      (-> f (update :blueprints conj bp) (assoc :as-of (max now (inc (:as-of f))))))))

(defn mark-real
  "`id` onto `:real`, once: already `:real` or `:removed` -> unchanged."
  [file id now]
  (let [f (normalize-file file)]
    (if (or (contains? (set (:real f)) id) (contains? (set (:removed f)) id))
      f
      (-> f (update :real conj id) (assoc :as-of (max now (inc (:as-of f))))))))

(defn mark-removed
  "`id` onto `:removed`, dropped from `:blueprints`/`:real`, once: already
   `:removed` -> unchanged. The id is never reused (`taken-ids`)."
  [file id now]
  (let [f (normalize-file file)]
    (if (contains? (set (:removed f)) id)
      f
      (-> f
          (update :blueprints (fn [bps] (vec (remove #(= id (:id %)) bps))))
          (update :real (fn [rs] (vec (remove #{id} rs))))
          (update :removed conj id)
          (assoc :as-of (max now (inc (:as-of f))))))))

(defn builds-of
  "`{:real :removed}` of the normalized file, the shape
   `pm.blueprint/apply-real-builds` takes."
  [file]
  (select-keys (normalize-file file) [:real :removed]))
