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
