(ns pm.ask-build-test
  (:require [cljs.test :refer [deftest is]]
            [clojure.string]
            [cljs.reader :as reader]
            [pm.ask-build :as ab]
            [pm.lot :as lot]
            [pm.blueprint :as bp]
            [pm.world :as w]))

(def RAW   (reader/read-string w/estate-edn))       ; the raw estate (mirrors a vector)
(def DB    (:demo-builds RAW))
(def SAMP  (:sampler DB))
(def RULES (:build-rules RAW))                      ; {:step-m 0.5 :yaws [0 45 90 135] :search-m 6 :gap-m 1.2 :route-m 0.8 :max-spread-m 0.12}
(def E0    {:demo-builds DB :mirrors [] :walkers []})            ; an empty estate
(def DB2   (assoc DB :words {"banc" :bench} :places {"ici" :here "poste" :post}))
(def E1    {:demo-builds DB2 :post {:at [0 1 0]} :mirrors [] :walkers []})   ; one place at the origin
(defn grid [kind] {:origin [-7 -7] :step 0.5 :cols 29 :rows 29
                   :kinds (vec (repeat 29 (apply str (repeat 29 kind)))) :y (vec (repeat 29 (vec (repeat 29 1))))})
(def OPEN  (grid "o")) (def TREES (grid "T"))
(defn ctx [estate world file here] {:estate estate :world world :file file :rules RULES :here here :by :tw :now 5000})
(def BENCH (get-in DB [:looks :bench :look]))
(def BP1   {:id :bench-1 :at [0 1 0] :yaw 0 :summary "A bench" :look BENCH :by :tw})
(def BP2   {:id :table-1 :at [3 1 3] :yaw 0 :summary "A table" :look (get-in DB [:looks :table :look]) :by :tw})
(def approx= (fn [a b] (< (js/Math.abs (- a b)) 1e-6)))

;; Parser

(deftest t01-normalize-text
  (is (= (ab/normalize-text "Put a SEAT, here!") " put a seat here "))
  (is (= (ab/normalize-text "  lamp   post ") " lamp post "))
  (is (= (ab/normalize-text nil) " "))
  (is (= (ab/normalize-text "") " "))
  (is (= (ab/normalize-text 42) " ")))

(deftest t02-look-plus-here
  (is (= (ab/parse-request DB "put a bench here") {:look :bench :near :here})))

(deftest t03-synonym-case-punctuation-a-place
  (is (= (ab/parse-request DB "Put a SEAT by the mailbox!") {:look :bench :near :mailbox})))

(deftest t04-two-word-look-and-place
  (is (= (ab/parse-request DB "can you build a lamp post near the phone booth?") {:look :lamp-post :near :phone-booth})))

(deftest t05-no-place-word-means-here
  (is (= (ab/parse-request DB "a table") {:look :table :near :here})))

(deftest t06-planter-synonym-by-the-creek
  (is (= (ab/parse-request DB "flowers by the creek please") {:look :planter :near :creek})))

(deftest t07-the-earliest-look-wins
  (is (= (ab/parse-request DB "a planter and a bench") {:look :planter :near :here})))

(deftest t08-the-earliest-place-wins
  (is (= (ab/parse-request DB "table by the mailbox near the creek") {:look :table :near :mailbox})))

(deftest t09-one-word-other-places
  (is (= (ab/parse-request DB "a lamppost at the workshop") {:look :lamp-post :near :workshop}))
  (is (= (ab/parse-request DB "a light by the booth") {:look :lamp-post :near :phone-booth})))

(deftest t10-the-help-cases
  (is (= (ab/parse-request DB "hello there") {:help true}))
  (is (= (ab/parse-request DB "") {:help true}))
  (is (= (ab/parse-request DB nil) {:help true}))
  (is (= (ab/parse-request DB "   ") {:help true}))
  (is (= (ab/parse-request DB "benches") {:help true}))
  (is (= (ab/parse-request DB "a tablecloth") {:help true})))

(deftest t11-find-word-ties
  (is (= (ab/find-word {"lamp" :a "lamp post" :b} "a lamp post") :b))
  (is (= (ab/find-word {"zz" :z "aa" :a} "zz") :z))
  (is (nil? (ab/find-word {} "x"))))

(deftest t12-the-words-come-from-data
  (is (= (ab/parse-request DB2 "un banc ici") {:look :bench :near :here}))
  (is (= (ab/parse-request DB2 "un banc pres de la poste") {:look :bench :near :post}))
  (is (= (ab/parse-request DB2 "put a bench here") {:help true})))

;; Ids, targets, grid

(deftest t13-next-id
  (is (= (ab/next-id :bench #{}) :bench-1))
  (is (= (ab/next-id :bench #{:bench-1 :bench-2}) :bench-3))
  (is (= (ab/next-id :bench #{:bench-2}) :bench-1))
  (is (= (ab/next-id :lamp-post #{:lamp-post-1}) :lamp-post-2)))

(deftest t14-taken-ids-over-the-world-and-the-file
  (is (= (ab/taken-ids {:blueprints [{:id :bench-1} {:id :bench-by-mailbox}]}
                        {:blueprints [{:id :bench-2}] :real [:bench-3] :removed [:bench-4]})
         #{:bench-1 :bench-by-mailbox :bench-2 :bench-3 :bench-4}))
  (is (= (ab/next-id :bench (ab/taken-ids {:blueprints [{:id :bench-1} {:id :bench-by-mailbox}]}
                                           {:blueprints [{:id :bench-2}] :real [:bench-3] :removed [:bench-4]}))
         :bench-5))
  (is (= (ab/taken-ids nil nil) #{})))

(deftest t15-target-xz
  (is (= (ab/target-xz RAW DB :mailbox nil) [4 -8]))
  (is (= (ab/target-xz RAW DB :phone-booth nil) [4.9 -6.6]))
  (is (= (ab/target-xz RAW DB :workshop nil) [-6.2 -3.5]))
  (is (= (ab/target-xz RAW DB :creek nil) [-0.9 -15.7]))
  (is (= (ab/target-xz RAW DB :here [1 2 3]) [1 3]))
  (is (nil? (ab/target-xz RAW DB :here nil)))
  (is (nil? (ab/target-xz RAW DB :nowhere nil))))

(deftest t16-grid-spec
  (is (= (ab/grid-spec [0 0] RULES 1) {:origin [-7 -7] :step 0.5 :cols 29 :rows 29}))
  (is (= (ab/grid-spec [4 -8] RULES 1) {:origin [-3 -15] :step 0.5 :cols 29 :rows 29}))
  (is (= (ab/grid-spec [0.2 0.3] RULES 1) {:origin [-7 -7] :step 0.5 :cols 30 :rows 30}))
  (is (= (ab/grid-spec [3.5 -9.25] RULES 1) {:origin [-3.5 -16.5] :step 0.5 :cols 29 :rows 30})))

;; Sampler

(def TER "SM_Hiroshi_Terrain/MAT")
(def GRV "SM_Hiroshi_Gravel/MAT")

(deftest t17-open-and-no-ground
  (is (= (ab/cell-kind SAMP [1.5 TER] [1.5 TER]) "o"))
  (is (= (ab/cell-kind SAMP [1.5 TER] nil) "o"))
  (is (= (ab/cell-kind SAMP nil nil) " "))
  (is (= (ab/cell-kind SAMP nil [3 "Oak_Leaves"]) " ")))

(deftest t18-trees-and-bushes-by-height
  (let [g [1.5 TER]]
    (is (= (ab/cell-kind SAMP g [5.2 "Oak_CanopyMass"]) "T"))
    (is (= (ab/cell-kind SAMP g [2.0 "Hedge_Leaves"]) "B"))
    (is (= (ab/cell-kind SAMP g [3.5 "X_Leaves"]) "B"))
    (is (= (ab/cell-kind SAMP g [3.6 "X_Leaves"]) "T"))
    (is (= (ab/cell-kind SAMP g [1.85 "X_Leaves"]) "B"))
    (is (= (ab/cell-kind SAMP g [1.7 "X_Leaves"]) "o"))))

(deftest t19-water-rocks-gravel-in-that-order
  (is (= (ab/cell-kind SAMP [1.2 GRV] [1.6 "SM_creek_water"]) "~"))
  (is (= (ab/cell-kind SAMP [1.5 TER] [1.9 "Bank_Rocks"]) "R"))
  (is (= (ab/cell-kind SAMP [1.2 GRV] [1.2 GRV]) ":"))
  (is (= (ab/cell-kind SAMP [1.5 TER] [1.52 "Path_Gravel"]) ":"))
  (is (= (ab/cell-kind SAMP [1.5 TER] [1.5 "x_CREEK"]) "~")))

(deftest t20-sample-cell-rounds-y-to-the-cm
  (is (= (ab/sample-cell SAMP [1.8249 TER] [1.8249 TER]) {:kind "o" :y 1.82}))
  (is (= (ab/sample-cell SAMP [1.826 TER] nil) {:kind "o" :y 1.83}))
  (is (= (ab/sample-cell SAMP nil nil) {:kind " " :y nil})))

(deftest t21-cover-name-and-name-has
  (is (true? (ab/cover-name? SAMP "Oak_Leaves")))
  (is (true? (ab/cover-name? SAMP "SM_WoodlandNear_01")))
  (is (true? (ab/cover-name? SAMP "Bank_Rocks/MAT")))
  (is (true? (ab/cover-name? SAMP "creek_surface")))
  (is (false? (ab/cover-name? SAMP "SM_Hiroshi_Terrain")))
  (is (false? (ab/cover-name? SAMP nil)))
  (is (false? (ab/cover-name? SAMP "")))
  (is (true? (ab/name-has? ["ab"] "xABy"))))

;; Data and helpers

(deftest t22-the-four-looks-are-buildable
  (doseq [k [:bench :lamp-post :table :planter]]
    (let [entry   (get-in DB [:looks k])
          summary (:summary entry)
          look    (:look entry)
          parts   (:parts look)]
      (is (string? summary))
      (is (not (clojure.string/blank? summary)))
      (doseq [{:keys [shape size color]} parts]
        (is (contains? #{:box :cone :sphere :cylinder} shape))   ; the shapes blueprints draw (the lamp has a cone hood and a glowing sphere)
        (is (every? #(>= % 0.08) size))
        (is (contains? #{:brown :tin :gold} color)))
      (is (< (js/Math.abs (apply min (map (fn [{[_ sy _] :size [_ ay _] :at}] (- ay (/ sy 2))) parts)))
             1e-9))
      (is (seq (lot/feet look))))))

(deftest t23-feet-by-hand
  (is (= (lot/feet (get-in DB [:looks :bench :look])) [[0.45 0.15] [0.45 -0.15] [-0.45 0.15] [-0.45 -0.15]]))
  (is (= (lot/feet (get-in DB [:looks :lamp-post :look])) [[0 0]]))
  (is (= (lot/feet (get-in DB [:looks :table :look])) [[0.42 0.27] [0.42 -0.27] [-0.42 0.27] [-0.42 -0.27]]))
  (is (= (lot/feet (get-in DB [:looks :planter :look])) [[0 0] [-0.4 -0.2] [-0.4 0.2] [0.4 -0.2] [0.4 0.2]])))

(deftest t24-look-radii-by-hand
  (is (approx= (lot/look-radius (get-in DB [:looks :bench :look]) 1) 0.5852349955))
  ;; the demo's lamp is the estate's own lamp (lamp-post-by-canal), glow and all
  (let [canal (some #(when (= :lamp-post-by-canal (:id %)) %) (:blueprints RAW))]
    (is (= (get-in DB [:looks :lamp-post :look]) (:look canal)))
    (is (approx= (lot/look-radius (get-in DB [:looks :lamp-post :look]) 1) (lot/look-radius (:look canal) 1)))
    (is (some :glow (get-in DB [:looks :lamp-post :look :parts]))))
  (is (approx= (lot/look-radius (get-in DB [:looks :table :look]) 1) 0.6103277808))
  (is (approx= (lot/look-radius (get-in DB [:looks :planter :look]) 1) 0.4472135955)))

(deftest t25-the-data-resolves
  (is (every? #(contains? (:looks DB) %) (vals (:words DB))))
  (is (every? (fn [v] (or (= v :here) (some? (ab/target-xz RAW DB v nil)))) (vals (:places DB))))
  (doseq [k [:placed :refused :help :made-real :sky]]
    (is (string? (get-in DB [:lines k])))
    (is (not (clojure.string/blank? (get-in DB [:lines k])))))
  (is (= (get-in DB [:lines :help]) "I can build a bench, a lamp post, a table or a planter. Try: put a bench here."))
  (is (nil? (some #(clojure.string/includes? (pr-str DB) %) ["Michael" "Hal" "Flora" "Juniper"]))))

(deftest t26-fill
  (is (= (ab/fill "{a} and {b}" {:a 1 :b "x"}) "1 and x"))
  (is (= (ab/fill "{a} {zz}" {:a 1}) "1 {zz}"))
  (is (nil? (ab/fill nil {}))))

(deftest t27-lot-estate-keeps-what-blocks
  (let [world {:blueprints [{:id :a :at [0 0 0] :summary "a"}
                             {:id :b :at [0 0 0] :summary "b" :status :rejected}
                             {:id :c :at [0 0 0] :summary "c" :status :real}
                             {:id :d :at [0 0 0]}
                             {:id :e :at [0 0 0] :summary "e" :status :removing}]}
        r (ab/lot-estate {:x 1} world)]
    (is (= (mapv :id (:blueprints r)) [:a :c :e]))
    (is (= (:x r) 1))))

;; ask / plan

(deftest t28-ask-help-and-sky
  (is (= (ab/ask (ctx E0 {} ab/empty-file [0 1 0]) "hello")
         {:say "I can build a bench, a lamp post, a table or a planter. Try: put a bench here."}))
  (is (= (ab/ask (ctx E0 {} ab/empty-file nil) "put a bench here")
         {:say "The middle of your screen is sky. Look at the ground and ask me again."})))

(deftest t29-ask-ready-to-measure
  (is (= (ab/ask (ctx E0 {} ab/empty-file [0 1 0]) "put a bench here")
         {:req {:look :bench :near :here} :target [0 0] :spec {:origin [-7 -7] :step 0.5 :cols 29 :rows 29}})))

(deftest t30-ask-a-place-with-no-position
  (let [E2 (assoc-in E0 [:demo-builds :places] {"moon" :moon})]
    (is (= (ab/ask (ctx E2 {} ab/empty-file [0 1 0]) "a bench by the moon")
           {:say "I can't put that there: I don't know a place called moon."}))))

(deftest t31-plan-open-ground-here
  (is (= (ab/plan (ctx E0 {} ab/empty-file [0 1 0]) {:look :bench :near :here} OPEN)
         {:ok true :id :bench-1
          :blueprint {:id :bench-1 :at [0 1 0] :yaw 0 :summary "A bench" :look BENCH :by :tw}
          :file {:as-of 5000 :blueprints [{:id :bench-1 :at [0 1 0] :yaw 0 :summary "A bench" :look BENCH :by :tw}]
                 :real [] :removed []}
          :say "A bench, drawn near where you're looking. Try it, and make it real if you like it."})))

(deftest t32-plan-keeps-clear-of-a-place
  (let [r (ab/plan (ctx E1 {} ab/empty-file [0 1 0]) {:look :bench :near :here} OPEN)]
    (is (true? (:ok r)))
    (is (= (:at (:blueprint r)) [-2.5 1 0]))
    (is (= (:yaw (:blueprint r)) 0)))
  (let [r (ab/plan (ctx E1 {} ab/empty-file nil) {:look :bench :near :post} OPEN)]
    (is (true? (:ok r)))
    (is (= (:at (:blueprint r)) [-2.5 1 0]))
    (is (= (:say r) "A bench, drawn near the post. Try it, and make it real if you like it."))))

(deftest t33-plan-refused-under-trees
  (is (= (ab/plan (ctx E0 {} ab/empty-file [0 1 0]) {:look :bench :near :here} TREES)
         {:ok false :say "I can't put that there: no level open ground within 6 m of where you're looking: most of it is under trees."})))

(deftest t34-plan-the-id-avoids-the-world-and-the-file
  (let [world {:blueprints [{:id :bench-1 :at [50 1 50] :summary "x"}]}
        file  {:blueprints [] :real [] :removed [:bench-2]}
        r     (ab/plan (ctx E0 world file [0 1 0]) {:look :bench :near :here} OPEN)]
    (is (= (:id r) :bench-3))
    (is (= (:at (:blueprint r)) [0 1 0]))))

(deftest t35-plan-other-looks-and-a-bad-look
  (let [here [0 1 0]]
    (is (= (:id (ab/plan (ctx E0 {} ab/empty-file here) {:look :lamp-post :near :here} OPEN)) :lamp-post-1))
    (is (= (:at (:blueprint (ab/plan (ctx E0 {} ab/empty-file here) {:look :lamp-post :near :here} OPEN))) here))
    (is (= (:id (ab/plan (ctx E0 {} ab/empty-file here) {:look :table :near :here} OPEN)) :table-1))
    (is (= (:id (ab/plan (ctx E0 {} ab/empty-file here) {:look :planter :near :here} OPEN)) :planter-1))
    (is (clojure.string/starts-with? (:say (ab/plan (ctx E0 {} ab/empty-file here) {:look :planter :near :here} OPEN))
                                      "A planter, drawn near"))
    (is (= (ab/plan (ctx E0 {} ab/empty-file here) {:look :rocket :near :here} OPEN)
           {:ok false :say (get-in DB [:lines :help])}))
    (is (= (:ok (ab/plan (ctx E0 {} ab/empty-file here) {:look :bench :near :here} nil)) false))
    (is (= (:say (ab/plan (ctx E0 {} ab/empty-file here) {:look :bench :near :here} nil))
           "I can't put that there: no ground map (state/ground.edn): the twin samples it from the kit."))))

;; The demo file

(deftest t36-empty-file-normalize-file
  (is (= ab/empty-file {:as-of 0 :blueprints [] :real [] :removed []}))
  (is (= (ab/normalize-file {:as-of 1.5 :blueprints [BP1 {:id :x} BP1 7] :real [:a "b" :a] :removed nil :junk 1})
         {:as-of 0 :blueprints [BP1] :real [:a] :removed []})))

(deftest t37-save-text-restore-round-trip
  (let [F (-> ab/empty-file (ab/add-build BP1 1000) (ab/mark-real :bench-1 1100) (ab/mark-removed :bench-by-mailbox 1200))]
    (is (= (ab/restore (ab/save-text F)) F)))
  (is (= (ab/restore nil) ab/empty-file))
  (is (= (ab/restore "") ab/empty-file))
  (is (= (ab/restore "{:as-of") ab/empty-file))
  (is (= (ab/restore "[1 2]") ab/empty-file)))

(deftest t38-add-build-adds-once
  (is (= (ab/add-build ab/empty-file BP1 1000) {:as-of 1000 :blueprints [BP1] :real [] :removed []}))
  (is (= (ab/add-build (ab/add-build ab/empty-file BP1 1000) BP1 2000)
         (ab/add-build ab/empty-file BP1 1000)))
  (is (= (:as-of (ab/add-build {:as-of 1000 :blueprints [] :real [] :removed []} BP2 900)) 1001)))

(deftest t39-mark-real
  (is (= (ab/mark-real (ab/add-build ab/empty-file BP1 1000) :bench-1 1500)
         {:as-of 1500 :blueprints [BP1] :real [:bench-1] :removed []}))
  (let [once (ab/mark-real (ab/add-build ab/empty-file BP1 1000) :bench-1 1500)]
    (is (= (ab/mark-real once :bench-1 1600) once)))
  (is (= (ab/mark-real {:removed [:x]} :x 10) {:as-of 0 :blueprints [] :real [] :removed [:x]})))

(deftest t40-mark-removed
  (let [first-result (ab/mark-real (ab/add-build ab/empty-file BP1 1000) :bench-1 1500)]
    (is (= (ab/mark-removed first-result :bench-1 1600)
           {:as-of 1600 :blueprints [] :real [] :removed [:bench-1]})))
  (is (= (ab/mark-removed ab/empty-file :bench-by-mailbox 10)
         {:as-of 10 :blueprints [] :real [] :removed [:bench-by-mailbox]}))
  (let [once (ab/mark-removed ab/empty-file :bench-by-mailbox 10)]
    (is (= (ab/mark-removed once :bench-by-mailbox 20) once))))

(deftest t41-builds-of
  (is (= (ab/builds-of {:real [:a] :removed [:b] :blueprints []}) {:real [:a] :removed [:b]}))
  (is (= (ab/builds-of nil) {:real [] :removed []})))

;; The merge, as core does it

(def E  (w/load-estate w/estate-edn))
(def W1 (:world (bp/merge-file E ab/empty-file)))

(deftest t42-the-first-merge-at-load-announces-nothing
  (is (= (:blueprints-as-of W1) 0))
  (is (= (:added (bp/merge-file E ab/empty-file)) [])))

(deftest t43-a-new-build-is-added-and-announced-once
  (let [F1 (ab/add-build ab/empty-file BP1 1000)
        r  (bp/merge-file W1 F1)]
    (is (= (:added r) [:bench-1]))
    (is (= (:announce r) [:bench-1]))
    (is (= (bp/status (:world r) :bench-1) :pending))
    (is (= (:by (first (filter #(= :bench-1 (:id %)) (:blueprints (:world r))))) :tw))
    (let [again (bp/merge-file (:world r) F1)]
      (is (= (:added again) []))
      (is (identical? (:world again) (:world r))))
    (let [F2 (ab/add-build F1 BP2 2000)
          r2 (bp/merge-file (:world r) F2)]
      (is (= (:added r2) [:table-1])))))

(deftest t44-the-real-list-is-applied
  (let [F1 (ab/add-build ab/empty-file BP1 1000)
        F3 (ab/mark-real (ab/mark-real F1 :bench-1 1500) :bench-by-mailbox 1500)
        W3 (bp/apply-real-builds (:world (bp/merge-file W1 F3)) (ab/builds-of F3))]
    (is (= (bp/status W3 :bench-1) :real))
    (is (= (bp/status W3 :bench-by-mailbox) :real))
    (is (= (bp/status W3 :lamp-post-by-canal) :pending))))

(deftest t45-a-removal-sticks-across-a-reload
  (let [F1 (ab/add-build ab/empty-file BP1 1000)
        F3 (ab/mark-real (ab/mark-real F1 :bench-1 1500) :bench-by-mailbox 1500)
        F5 (-> F3 (ab/mark-removed :bench-1 1600) (ab/mark-removed :bench-by-mailbox 1600))
        W5 (bp/apply-real-builds (:world (bp/merge-file W1 F5)) (ab/builds-of F5))]
    (is (not (contains? (set (map :id (:blueprints W5))) :bench-1)))
    (is (= (bp/status W5 :bench-by-mailbox) :rejected))
    (is (= (ab/next-id :bench (ab/taken-ids W5 F5)) :bench-2))))
