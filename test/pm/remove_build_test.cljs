(ns pm.remove-build-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string :as str]
            [pm.blueprint :as bp]
            [pm.blueprint-file :as bf]
            [pm.bridge :as b]
            [pm.crew :as crew]
            [pm.world :as w]))

(def TXT {:trial "trial: try it out" :make-real "Make it real" :undo "Undo" :saving "Saving…"
          :remove "Remove…" :close "Close" :remove-it "Remove it" :keep "Keep it"
          :removing "will be removed" :removing-wait "Removing…"
          :removed "Removed. It's gone for good."
          :no-bridge-remove "Start the bridge (bb crew-bridge) to remove it."})
(def B1 {:id :lamp    :at [1 2 3] :summary "a lamp post"})                       ; an estate build
(def B2 {:id :bench   :at [0 0 0] :summary "a bench" :from-file? true :by :le-testeur})  ; a file build: the second kind
(def B3 {:id :planter :at [5 0 5] :summary "a planter"})                         ; stays pending
(def W  (bp/apply-real-builds {:blueprint-text TXT :blueprints [B1 B2 B3]} {:real [:lamp :bench]}))
(def ap bp/approve-blueprint)
(defn st [w] (mapv :status (bp/all w)))
(def ctx {:token "t" :members #{}})
(defn req [body] {:method "POST" :uri b/real-uri :origin b/allowed-origin :body (assoc body "token" "t")})
(def LOOK  {:parts [{:shape :box :size [0.6 0.4 0.6] :at [0 0.4 0] :color :brown}]})
(def PL-IN {:id :planter :at [-2.0 1.0 -4.0] :summary "proposed: a planter by the path" :look LOOK})
(def E #{:bench-by-mailbox :lamp-post-by-canal})

;; -- Statuses, start-removal, keep-it --------------------------------------------

(deftest s1-statuses
  (is (= #{:pending :approved :real :removing :rejected} bp/statuses)))

(deftest r1-start-removal
  (let [w (bp/start-removal W :lamp)]
    (is (= [:removing :real :pending] (st w)))
    (is (= :lamp (:card-open w)))))

(deftest r2-start-removal-no-ops
  (is (identical? W (bp/start-removal W :planter)))
  (let [w (ap W :planter)]
    (is (identical? w (bp/start-removal w :planter))))
  (let [w (bp/reject-blueprint W :planter)]
    (is (identical? w (bp/start-removal w :planter))))
  (is (identical? W (bp/start-removal W :gazebo)))
  (let [w1 (bp/start-removal W :lamp)]
    (is (identical? w1 (bp/start-removal w1 :lamp)))))

(deftest r3-keep-it-restores-exactly
  (let [r (-> W (bp/start-removal :lamp) (bp/keep-it :lamp))]
    (is (= W r))))

(deftest r4-keep-it-no-ops
  (is (identical? W (bp/keep-it W :lamp)))
  (is (identical? W (bp/keep-it W :planter)))
  (is (identical? W (bp/keep-it W :gazebo))))

(deftest r5-start-removal-and-keep-it-second-build
  (is (= :removing (bp/status (bp/start-removal W :bench) :bench)))
  (let [r (-> W (bp/start-removal :bench) (bp/keep-it :bench))]
    (is (= W r))))

;; -- finish-removal, fail-removal -------------------------------------------------

(deftest f1-finish-removal
  (let [w (bp/finish-removal (bp/start-removal W :lamp) :lamp 1000)]
    (is (= [:rejected :real :pending] (st w)))
    (is (false? (contains? w :card-open)))
    (is (= {:event :blueprint-removed :text "Removed. It's gone for good." :until 7000} (:hal w)))))

(deftest f2-finish-removal-real-not-previewed-is-a-no-op
  (is (identical? W (bp/finish-removal W :lamp 1000))))

(deftest f3-finish-removal-no-words-no-caption
  (let [w (-> W (update :blueprint-text dissoc :removed) (bp/start-removal :lamp) (bp/finish-removal :lamp 1000))]
    (is (= :rejected (bp/status w :lamp)))
    (is (nil? (:hal w)))))

(deftest f4-finish-removal-takes-the-card-and-the-build-down
  (let [w (bp/finish-removal (bp/start-removal W :lamp) :lamp 1000)]
    (is (nil? (bp/card-of w)))
    (is (= [[:blueprint :bench]] (mapv :key (bp/built-plan w))))
    (is (= {:add [] :remove #{}} (bp/drawable-changes #{:lamp :bench :planter} w)))))

(deftest x1-fail-removal-back-to-real-with-caption-and-card
  (let [w (bp/fail-removal (bp/start-removal W :lamp) :lamp nil 1000)]
    (is (= [:real :real :pending] (st w)))
    (is (= :lamp (:card-open w)))
    (is (= {:event :blueprint-remove-refused :text "Start the bridge (bb crew-bridge) to remove it." :until 7000} (:hal w)))
    (is (= {:id :lamp :mode :real} (bp/card-of w)))))

(deftest x2-fail-removal-caption-includes-the-reason
  (is (= "Start the bridge (bb crew-bridge) to remove it. (not a real build)"
         (:text (:hal (bp/fail-removal (bp/start-removal W :bench) :bench "not a real build" 1000))))))

(deftest x3-fail-removal-not-previewed-is-a-no-op
  (is (identical? W (bp/fail-removal W :lamp nil 1000))))

;; -- The card's owner and mode ------------------------------------------------------

(deftest o1-card-of-nil-when-nothing-open
  (is (nil? (bp/card-of W))))

(deftest o2-card-of-real
  (is (= {:id :lamp :mode :real} (bp/card-of (bp/open-card W :lamp)))))

(deftest o3-open-card-no-ops
  (is (identical? W (bp/open-card W :planter)))
  (is (identical? W (bp/open-card W :gazebo))))

(deftest o4-card-of-removing
  (is (= {:id :lamp :mode :removing} (bp/card-of (bp/start-removal W :lamp)))))

(deftest o5-card-of-trial
  (is (= {:id :planter :mode :trial} (bp/card-of (ap W :planter)))))

(deftest o6-open-card-fronts-a-standing-trial-close-card-brings-it-back
  (let [w (-> W (ap :planter) (bp/open-card :lamp))]
    (is (= {:id :lamp :mode :real} (bp/card-of w)))
    (is (= {:id :planter :mode :trial} (bp/card-of (bp/close-card w))))))

(deftest o7-open-card-on-another-build-mid-preview-keeps-it-real
  (let [w (-> W (bp/start-removal :lamp) (bp/open-card :bench))]
    (is (= [:real :real :pending] (st w)))
    (is (= {:id :bench :mode :real} (bp/card-of w)))))

(deftest o8-close-card-mid-preview-restores-it
  (let [r (-> W (bp/start-removal :lamp) bp/close-card)]
    (is (= W r))))

(deftest o9-close-card-no-card-open-is-a-no-op
  (is (identical? W (bp/close-card W))))

(deftest o10-card-of-stale-card-open
  (is (nil? (bp/card-of (assoc W :card-open :planter)))))

;; -- The card's buttons ---------------------------------------------------------

(deftest c1-card-nil-when-no-owner
  (is (nil? (bp/card W nil))))

(deftest c2-card-real
  (is (= {:id :lamp :mode :real
          :buttons [{:action :remove :label "Remove…" :disabled? false}
                    {:action :close :label "Close" :disabled? false}]}
         (bp/card (bp/open-card W :lamp) nil))))

(deftest c3-card-removing
  (is (= {:id :lamp :mode :removing
          :buttons [{:action :remove-it :label "Remove it" :disabled? false}
                    {:action :keep :label "Keep it" :disabled? false}]}
         (bp/card (bp/start-removal W :lamp) nil))))

(deftest c4-card-removing-in-flight
  (is (= {:id :lamp :mode :removing
          :buttons [{:action :remove-it :label "Removing…" :disabled? true}
                    {:action :keep :label "Keep it" :disabled? true}]}
         (bp/card (bp/start-removal W :lamp) :lamp))))

(deftest c5-card-trial
  (is (= {:id :planter :mode :trial
          :buttons [{:action :real :label "Make it real" :disabled? false}
                    {:action :undo :label "Undo" :disabled? false}]}
         (bp/card (ap W :planter) nil))))

(deftest c6-card-nil-when-words-missing
  (is (nil? (bp/card (bp/open-card (update W :blueprint-text dissoc :close) :lamp) nil)))
  (is (nil? (bp/card (bp/start-removal (update W :blueprint-text dissoc :removing-wait) :lamp) nil))))

(deftest c7-card-real-second-build
  (is (= {:id :bench :mode :real
          :buttons [{:action :remove :label "Remove…" :disabled? false}
                    {:action :close :label "Close" :disabled? false}]}
         (bp/card (bp/open-card W :bench) nil))))

;; -- Sign, plans, drawing, merge --------------------------------------------------

(deftest p1-sign-text-removing
  (is (= "a lamp post\nwill be removed" (bp/sign-text W (assoc B1 :status :removing)))))

(deftest p2-built-plan-removing-item
  (let [items (bp/built-plan (bp/start-removal W :lamp))]
    (is (= {:key [:blueprint :lamp] :ghost? true :tint :removal :label :sign :text "a lamp post\nwill be removed"}
           (select-keys (first items) [:key :ghost? :tint :label :text])))
    (is (= 2 (count items)))
    (let [second-item (second items)]
      (is (= [:blueprint :bench] (:key second-item)))
      (is (nil? (:ghost? second-item)))
      (is (nil? (:label second-item))))))

(deftest p3-ghost-plan-excludes-removing
  (is (= [[:blueprint :planter]] (mapv :key (bp/ghost-plan (bp/start-removal W :lamp))))))

(deftest p4-drawable-changes-draws-removing
  (is (= {:add [:lamp] :remove #{}} (bp/drawable-changes #{:bench :planter} (bp/start-removal W :lamp)))))

(deftest p5-merge-file-keeps-a-previewed-file-build-the-file-drops
  (let [w0 (-> (bp/merge-file {:blueprint-text TXT :blueprints [B1]} {:as-of 1 :blueprints [(dissoc B2 :from-file?)]})
               :world
               (bp/apply-real-builds {:real [:bench]})
               (bp/start-removal :bench))
        r  (bp/merge-file w0 {:as-of 2 :blueprints []})]
    (is (= [] (:removed r)))
    (is (= :removing (bp/status (:world r) :bench)))))

;; -- apply-real-builds with :removed ----------------------------------------------

(deftest a1-apply-real-builds-removed
  (is (= [:real :rejected :pending]
         (st (bp/apply-real-builds {:blueprints [B1 B2 B3]} {:real [:lamp] :removed [:bench]})))))

(deftest a2-apply-real-builds-removed-beats-real
  (is (= [:real :rejected :pending]
         (st (bp/apply-real-builds {:blueprints [B1 B2 B3]} {:real [:lamp :bench] :removed [:bench]})))))

(deftest a3-apply-real-builds-removed-applies-to-file-and-estate-builds
  (let [wf (:world (bp/merge-file {:blueprints [B1]} {:as-of 1 :blueprints [(dissoc B2 :from-file?)]}))]
    (is (= :rejected (bp/status (bp/apply-real-builds wf {:removed [:bench]}) :bench)))
    (is (= :rejected (bp/status (bp/apply-real-builds wf {:removed [:lamp]}) :lamp)))))

(deftest a4-apply-real-builds-re-apply-keeps-a-preview
  (is (= :removing (bp/status (bp/apply-real-builds (bp/start-removal W :lamp) {:real [:lamp :bench]}) :lamp))))

(deftest a5-apply-real-builds-removed-beats-removing
  (is (= :rejected (bp/status (bp/apply-real-builds (bp/start-removal W :lamp) {:real [:bench] :removed [:lamp]}) :lamp))))

(deftest a6-apply-real-builds-no-op
  (is (identical? W (bp/apply-real-builds W nil)))
  (is (identical? W (bp/apply-real-builds W {:real [] :removed []}))))

(deftest a7-apply-real-builds-removed-never-drawn
  (is (= {:add [:bench :planter] :remove #{}}
         (bp/drawable-changes nil (bp/apply-real-builds {:blueprints [B1 B2 B3]} {:removed [:lamp]})))))

;; -- The bridge -----------------------------------------------------------------

(deftest br1-remove-real-build
  (is (= {:as-of 9 :real [:bench] :removed [:lamp]}
         (b/remove-real-build {:as-of 5 :real [:bench :lamp]} :lamp 9))))

(deftest br2-remove-real-build-second-removal
  (is (= {:as-of 12 :real [] :removed [:bench :lamp]}
         (b/remove-real-build {:as-of 9 :real [:bench] :removed [:lamp]} :bench 12))))

(deftest br3-remove-real-build-refused
  (is (nil? (b/remove-real-build {:real [:bench]} :lamp 9)))
  (is (nil? (b/remove-real-build {:as-of 9 :real [:bench] :removed [:lamp]} :lamp 12)))
  (is (nil? (b/remove-real-build nil :lamp 9))))

(deftest br4-remove-real-build-shape
  (is (= #{:as-of :real :removed} (set (keys (b/remove-real-build {:real [:lamp]} :lamp 9))))))

(deftest br5-add-real-build-keeps-removed
  (is (= {:as-of 12 :real [:bench :planter] :removed [:lamp]}
         (b/add-real-build {:as-of 9 :real [:bench] :removed [:lamp]} :planter #{:bench :lamp :planter} 12))))

(deftest br6-add-real-build-refuses-a-removed-id
  (is (nil? (b/add-real-build {:real [] :removed [:lamp]} :lamp #{:lamp} 12))))

(deftest br7-validate-remove
  (is (= {:ok true :action :remove-build :id :lamp-post-by-canal}
         (b/validate (req {"action" "remove" "id" "lamp-post-by-canal"}) ctx)))
  (is (= 400 (:status (b/validate (req {"action" "remove" "id" "../x"}) ctx)))))

(deftest br8-remove-build-body
  (is (= {"token" "t" "action" "remove" "id" "lamp-post-by-canal"}
         (crew/remove-build-body {:token "t" :port 8091} :lamp-post-by-canal))))

;; -- bb blueprint-add refusing a removed id --------------------------------------

(deftest d1-add-refuses-a-removed-id
  (is (= {:refused "id :planter was removed; pick a new id"}
         (bf/add nil E :le-testeur PL-IN 10 #{:planter}))))

(deftest d2-add-ok-when-id-not-removed
  (is (= {:ok {:as-of 10 :blueprints [(assoc PL-IN :by :le-testeur)]} :id :planter}
         (bf/add nil E :le-testeur PL-IN 10 #{:other}))))

(deftest d3-add-5-arity-is-empty-removed-set
  (is (= (bf/add nil E :le-testeur PL-IN 10) (bf/add nil E :le-testeur PL-IN 10 #{})))
  (is (some? (:ok (bf/add nil E :le-testeur PL-IN 10 nil)))))

(deftest d4-add-estate-beats-removed
  (is (= {:refused "id :planter is already in the estate"}
         (bf/add nil #{:planter} :le-testeur PL-IN 10 #{:planter}))))

(deftest d5-add-removed-beats-already-in-the-file
  (is (= {:refused "id :planter was removed; pick a new id"}
         (bf/add {:as-of 1 :blueprints [(assoc PL-IN :by :x)]} E :le-testeur PL-IN 10 #{:planter}))))

(deftest d6-removed-ids
  (is (= #{:a :c} (bf/removed-ids {:real [:x] :removed [:a "b" :c]})))
  (is (= #{} (bf/removed-ids nil))))

;; -- The estate's words -----------------------------------------------------------

(deftest e1-estate-removal-words-are-non-blank-strings
  (let [t (:blueprint-text (w/load-estate w/estate-edn))]
    (doseq [k [:remove :close :remove-it :keep :removing :removing-wait :removed :no-bridge-remove]]
      (let [v (get t k)]
        (is (string? v) (str k " is a string"))
        (is (not (str/blank? v)) (str k " is not blank"))))))

(deftest e2-estate-removal-words-exactly
  (let [t (:blueprint-text (w/load-estate w/estate-edn))]
    (is (= {:remove "Remove…" :close "Close" :remove-it "Remove it" :keep "Keep it"
            :removing "will be removed" :removing-wait "Removing…"
            :removed "Removed. It's gone for good."
            :no-bridge-remove "Start the bridge (bb crew-bridge) to remove it."}
           (select-keys t [:remove :close :remove-it :keep :removing :removing-wait :removed :no-bridge-remove])))))

(deftest e3-the-shipped-estate-gets-a-real-card
  (is (= {:id :lamp-post-by-canal :mode :real
          :buttons [{:action :remove :label "Remove…" :disabled? false}
                    {:action :close :label "Close" :disabled? false}]}
         (bp/card (bp/open-card (bp/apply-real-builds (w/load-estate w/estate-edn) {:real [:lamp-post-by-canal]})
                                 :lamp-post-by-canal)
                  nil))))
