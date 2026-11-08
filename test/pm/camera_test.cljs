(ns pm.camera-test
  (:require [cljs.test :refer [deftest is]]
            [pm.camera :as c]))

(deftest c1-round-trip
  (is (= {:pos [4.5 6 -2] :target [4.5 1.8 -8]}
         (c/decode (c/encode [4.5 6 -2] [4.5 1.8 -8])))))

(deftest c2-rejects-junk
  (is (nil? (c/decode nil)))
  (is (nil? (c/decode "")))
  (is (nil? (c/decode "not json")))
  (is (nil? (c/decode "{\"v\":2,\"pos\":[0,1,2],\"target\":[0,0,0]}")))     ; other version
  (is (nil? (c/decode "{\"v\":1,\"pos\":[0,1],\"target\":[0,0,0]}")))       ; short
  (is (nil? (c/decode "{\"v\":1,\"pos\":[0,\"x\",2],\"target\":[0,0,0]}"))) ; not a number
  (is (nil? (c/decode "{\"v\":1,\"pos\":[0,1,9000],\"target\":[0,0,0]}")))  ; absurd
  (is (nil? (c/decode "{\"v\":1,\"pos\":[1,1,1],\"target\":[1,1,1]}"))))    ; eye on target

(deftest c3-changed
  (let [s {:pos [0 1 2] :target [0 0 0]}]
    (is (true? (c/changed? nil [0 1 2] [0 0 0])))
    (is (not (c/changed? s [0 1.005 2] [0 0 0])))
    (is (c/changed? s [0 1.02 2] [0 0 0]))
    (is (c/changed? s [0 1 2] [0 0 -0.5]))))

(deftest c4-url-view-wins
  (is (true? (c/restore? "")))
  (is (true? (c/restore? "?pony&v=3")))
  (is (false? (c/restore? "?view=creek")))
  (is (false? (c/restore? "?pony&view=island"))))
