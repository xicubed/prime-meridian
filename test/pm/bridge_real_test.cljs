(ns pm.bridge-real-test
  (:require [cljs.test :refer [deftest is]]
            [pm.bridge :as b]))

(def ctx {:token "t" :members #{}})
(defn- req [body] {:method "POST" :uri b/real-uri :origin b/allowed-origin :body (assoc body "token" "t")})

(deftest t01-validate-build-and-mirror
  (is (= {:ok true :action :real-build :id :lamp-post-by-canal}
         (b/validate (req {"action" "build" "id" "lamp-post-by-canal"}) ctx)))
  (is (= {:ok true :action :real-mirror :mirror :flora :state {:stock 29 :last-fed 5}}
         (b/validate (req {"action" "mirror" "mirror" "flora" "state" {"stock" 29 "last-fed" 5}}) ctx)))
  (is (= 400 (:status (b/validate (req {"action" "build" "id" "../etc"}) ctx))))
  (is (= 400 (:status (b/validate (req {"action" "mirror" "mirror" "flora" "state" {"stock" "lots"}}) ctx))))
  (is (= 400 (:status (b/validate (req {"action" "burn"}) ctx))))
  (is (= 401 (:status (b/validate (assoc-in (req {"action" "build" "id" "x"}) [:body "token"] "no") ctx)))))

(deftest t02-add-real-build
  (is (= {:as-of 9 :real [:bench :lamp]} (b/add-real-build {:real [:lamp]} :bench #{:bench :lamp} 9)))
  (is (= {:as-of 9 :real [:lamp]} (b/add-real-build {:real [:lamp]} :lamp #{:lamp} 9)) "once")
  (is (nil? (b/add-real-build {} :tower #{:lamp} 9)) "only blueprints in the estate's data"))

(deftest t03-merge-real-mirror
  (let [f {:as-of 1 :mirrors {:flora {:state {:stock 30 :last-fed 1}}}}]
    (is (= {:as-of 9 :mirrors {:flora {:state {:stock 29 :last-fed 8}}}}
           (b/merge-real-mirror f :flora {:stock 29 :last-fed 8 :source :hacked :mood 3} #{:stock :last-fed :source} 9)))
    (is (nil? (b/merge-real-mirror f :flora {:mood 3} #{:stock} 9)))))

(def CB {:id :bench-by-creek :at [1.0 1.2 -9.0] :yaw 0 :summary "proposed: a bench by the creek"})
(def PL {:id :planter :at [-2.0 1.0 -4.0] :summary "proposed: a planter by the path"})
(def E-BENCH {:id :bench-by-mailbox :at [4.0 1.66 -6.5] :yaw 90 :summary "proposed: a bench by the mailbox"})
(def E-LAMP {:id :lamp-post-by-canal :at [3.8 0.63 -5.0] :summary "proposed: a lamp post by the canal"})

(deftest t04-known-build-ids
  (is (= #{:bench-by-mailbox :lamp-post-by-canal :bench-by-creek :planter}
         (b/known-build-ids {:blueprints [E-BENCH E-LAMP]}
                             {:as-of 1 :blueprints [CB PL {:id "bad" :at [0 0 0] :summary "s"}]})))
  (is (= #{:bench-by-mailbox :lamp-post-by-canal} (b/known-build-ids {:blueprints [E-BENCH E-LAMP]} nil)))
  (is (= #{} (b/known-build-ids nil nil)))
  (is (= {:as-of 9 :real [:lamp-post-by-canal :planter]}
         (b/add-real-build {:real [:lamp-post-by-canal]} :planter
                            (b/known-build-ids {:blueprints [E-BENCH E-LAMP]} {:as-of 1 :blueprints [PL]}) 9)))
  (is (nil? (b/add-real-build {:real [:lamp-post-by-canal]} :planter
                               (b/known-build-ids {:blueprints [E-BENCH E-LAMP]} nil) 9)))
  (is (= {:ok true :action :real-build :id :bench-by-creek}
         (b/validate (req {"action" "build" "id" "bench-by-creek"}) ctx))))

;; ---------------------------------------------------------------------------
;; view-share-v0 (TASKS T-19): POST /view

(def GB {"action" "view" "camera" {"pos" [1.23 5.68 -10] "target" [0 0 -2.35]} "center" [1.5 1.7 -8.2]
         "walkers" [{"id" "hal" "pos" [2.5 1.8 -7]} {"id" "b-walker" "pos" [-5 1.6 -14]}]})
(defn- vreq [body] {:method "POST" :uri b/view-uri :origin b/allowed-origin :body (assoc body "token" "t")})
(defn- ws [n] (vec (for [i (range n)] {"id" (str "w" i) "pos" [0 0 0]})))

(deftest t05-validate-view-good
  (is (= {:ok true :action :view :camera {:pos [1.23 5.68 -10] :target [0 0 -2.35]}
          :center [1.5 1.7 -8.2] :walkers {:hal [2.5 1.8 -7] :b-walker [-5 1.6 -14]}}
         (b/validate (vreq GB) ctx)))
  (is (true? (:ok (b/validate (vreq (assoc GB "center" nil)) ctx))))
  (is (nil? (:center (b/validate (vreq (assoc GB "center" nil)) ctx))))
  (is (true? (:ok (b/validate (vreq (assoc GB "walkers" [])) ctx))))
  (is (= {} (:walkers (b/validate (vreq (assoc GB "walkers" [])) ctx))))
  (is (true? (:ok (b/validate (vreq (assoc GB "walkers" (ws 32))) ctx))))
  (is (= 32 (count (:walkers (b/validate (vreq (assoc GB "walkers" (ws 32))) ctx))))))

(deftest t06-validate-view-refusals
  (is (= {:ok false :status 400 :reason "bad action"}
         (b/validate (vreq (assoc GB "action" "look")) ctx)))
  (is (= {:ok false :status 400 :reason "bad camera"}
         (b/validate (vreq (dissoc GB "camera")) ctx)))
  (is (= {:ok false :status 400 :reason "bad camera"}
         (b/validate (vreq (assoc-in GB ["camera" "pos"] [1 2])) ctx)))
  (is (= {:ok false :status 400 :reason "bad camera"}
         (b/validate (vreq (assoc-in GB ["camera" "target"] [0 0 2001])) ctx)))
  (is (= {:ok false :status 400 :reason "bad camera"}
         (b/validate (vreq (assoc-in GB ["camera" "pos"] [js/NaN 0 0])) ctx)))
  (is (= {:ok false :status 400 :reason "bad center"}
         (b/validate (vreq (dissoc GB "center")) ctx)))
  (is (= {:ok false :status 400 :reason "bad center"}
         (b/validate (vreq (assoc GB "center" [0 0])) ctx)))
  (is (= {:ok false :status 400 :reason "bad center"}
         (b/validate (vreq (assoc GB "center" "here")) ctx)))
  (is (= {:ok false :status 400 :reason "bad walkers"}
         (b/validate (vreq (dissoc GB "walkers")) ctx)))
  (is (= {:ok false :status 400 :reason "bad walkers"}
         (b/validate (vreq (assoc GB "walkers" "x")) ctx)))
  (is (= {:ok false :status 400 :reason "too many walkers"}
         (b/validate (vreq (assoc GB "walkers" (ws 33))) ctx)))
  (is (= {:ok false :status 400 :reason "bad walker"}
         (b/validate (vreq (assoc GB "walkers" [{"id" "Hal" "pos" [1 1 1]}])) ctx)))
  (is (= {:ok false :status 400 :reason "bad walker"}
         (b/validate (vreq (assoc GB "walkers" [{"id" "../x" "pos" [1 1 1]}])) ctx)))
  (is (= {:ok false :status 400 :reason "bad walker"}
         (b/validate (vreq (assoc GB "walkers" [{"id" "ok" "pos" [0 0]}])) ctx)))
  (is (= {:ok false :status 400 :reason "bad walker"}
         (b/validate (vreq (assoc GB "walkers" ["hal"])) ctx)))
  (is (= {:ok false :status 400 :reason "bad walker"}
         (b/validate (vreq (assoc GB "walkers" [{"id" "hal" "pos" [0 0 0]} {"id" "hal" "pos" [1 1 1]}])) ctx))))

(deftest t07-validate-view-guards
  (is (= {:ok false :status 401 :reason "bad token"}
         (b/validate (assoc-in (vreq GB) [:body "token"] "no") ctx)))
  (is (= {:ok false :status 403 :reason "bad origin"}
         (b/validate (assoc (vreq GB) :origin "http://evil.test") ctx)))
  (is (= 405 (:status (b/validate (assoc (vreq GB) :method "GET") ctx))))
  (is (= 404 (:status (b/validate (assoc (vreq GB) :uri "/views") ctx))))
  (is (= {:ok false :status 400 :reason "bad json"}
         (b/validate {:method "POST" :uri b/view-uri :origin b/allowed-origin :body "x"} ctx))))

(deftest t08-log-line-view
  (is (= "2026-11-24T14:00:00Z - view 400 bad camera"
         (b/log-line "2026-11-24T14:00:00Z" nil "view" 400 "bad camera"))))
