(ns pm.booth-model-test
  (:require [cljs.test :refer [deftest is]]
            [pm.models :as md]
            [pm.plan :as p]
            [pm.pick :as pk]
            [pm.world :as w]
            [pm.content :as c]))

(def WE (w/load-estate w/estate-edn))
(def M  {"lamp" {:glow true :light-m 3} "sign" {:glow true :light false}})
(def URL "/assets/props/phone-booth.glb")
(def T0 1800000000000)
(def jo {:event :jack-out :reason :household :who :household :text "Dinner!"
         :source {:agent "twin" :session "s1" :machine "laptop"}})
(def BOX {:phone-booth {:at [1 2 3] :yaw 90
                        :look {:parts [{:shape :box :size [1 2 1] :at [0 1 0] :color :booth-red}]}
                        :model {:url "/m.glb"}}})
(defn near? [a b] (and (= (count a) (count b)) (every? #(< (js/Math.abs %) 1e-9) (map - a b))))

;; 5.1 material-override

(deftest case-1-material-override-lamp
  (is (= {:glow true :light-m 3} (md/material-override "lamp" M))))

(deftest case-2-material-override-sign
  (is (= {:glow true :light false} (md/material-override "sign" M))))

(deftest case-3-material-override-glass-keeps-its-own
  (is (nil? (md/material-override "glass" M))))

(deftest case-4-material-override-none-for-the-rest
  (doseq [name ["booth-red" "floor" "letters" "metal" "leaf" "stem"]]
    (is (nil? (md/material-override name M)))))

(deftest case-5-material-override-unknown-or-bad-names
  (doseq [name ["nope" "Lamp" nil 7]]
    (is (nil? (md/material-override name M)))))

(deftest case-6-material-override-no-map
  (is (nil? (md/material-override "lamp" nil)))
  (is (nil? (md/material-override "lamp" {}))))

(deftest case-7-material-override-kit
  (is (= {:kit "MAT_Hiroshi_Glass"}
         (md/material-override "glass" {"glass" {:kit "MAT_Hiroshi_Glass"}}))))

(deftest case-8-material-override-kit-and-glow
  (is (= {:kit "K" :glow true} (md/material-override "x" {"x" {:kit "K" :glow true}}))))

(deftest case-9-material-override-plain-glow
  (is (= {:glow true} (md/material-override "x" {"x" {:glow true}})))
  (is (= {:glow true} (md/material-override "x" {"x" {:glow true :light true}}))))

(deftest case-10-material-override-no-light-wins-over-range
  (is (= {:glow true :light false}
         (md/material-override "x" {"x" {:glow true :light false :light-m 3}}))))

(deftest case-11-material-override-bad-ranges-dropped
  (doseq [v [0 -1 "3" js/NaN js/Infinity]]
    (is (= {:glow true} (md/material-override "x" {"x" {:glow true :light-m v}})))))

(deftest case-12-material-override-light-options-need-glow
  (is (nil? (md/material-override "x" {"x" {:light-m 3}})))
  (is (nil? (md/material-override "x" {"x" {:light false}})))
  (is (nil? (md/material-override "x" {"x" {:glow "yes" :light-m 3}})))
  (is (nil? (md/material-override "x" {"x" {:glow false}}))))

(deftest case-13-material-override-light-only-off-on-literal-false
  (is (= {:glow true :light-m 2}
         (md/material-override "x" {"x" {:glow true :light "no" :light-m 2}})))
  (is (= {:glow true}
         (md/material-override "x" {"x" {:glow true :light nil}}))))

(deftest case-14-material-override-bad-kit-names
  (is (nil? (md/material-override "x" {"x" {:kit ""}})))
  (is (nil? (md/material-override "x" {"x" {:kit "  "}})))
  (is (nil? (md/material-override "x" {"x" {:kit 7}})))
  (is (nil? (md/material-override "x" {"x" {:kit "A / wind"}})))
  (is (= {:glow true} (md/material-override "x" {"x" {:kit "A / wind" :glow true}}))))

(deftest case-15-material-override-not-a-map-entry-or-extra-keys
  (is (nil? (md/material-override "x" {"x" "MAT_Hiroshi_Glass"})))
  (is (= {:kit "K"} (md/material-override "x" {"x" {:kit "K" :foo 1}}))))

;; 5.2 model-spec

(deftest case-16-model-spec-shipped-shape
  (is (= {:url URL :materials M} (md/model-spec {:url URL :materials M}))))

(deftest case-17-model-spec-no-materials
  (is (= {:url "/m.glb" :materials {}} (md/model-spec {:url "/m.glb"})))
  (is (= {:url "/m.glb" :materials {}} (md/model-spec {:url "/m.glb" :materials nil}))))

(deftest case-18-model-spec-junk-entries-dropped
  (is (= {:url "/m.glb" :materials {"lamp" {:glow true}}}
         (md/model-spec {:url "/m.glb"
                          :materials {"lamp" {:glow true} :sign {:glow true} "x" "str" 7 {}}}))))

(deftest case-19-model-spec-extra-keys-dropped
  (is (= {:url "/m.glb" :materials {}} (md/model-spec {:url "/m.glb" :scale 2 :yaw 90}))))

(deftest case-20-model-spec-bad-models
  (doseq [bad [nil "/m.glb" {} {:url nil} {:url 7} {:url "assets/props/phone-booth.glb"}
               {:url " /m.glb"} {:url "/"} {:url "//cdn.example/m.glb"}
               {:url "https://example.com/m.glb"}]]
    (is (nil? (md/model-spec bad)))))

(deftest case-21-model-spec-bad-materials-ignores-whole-model
  (is (nil? (md/model-spec {:url "/m.glb" :materials "lamp"})))
  (is (nil? (md/model-spec {:url "/m.glb" :materials [["lamp" {:glow true}]]}))))

;; 5.3 material-props

(deftest case-22-material-props-glass-stays-see-through
  (is (= {:name "glass" :color 0xdde8ee :roughness 0.05 :metalness 0 :emissive 0
          :emissiveIntensity 1 :opacity 0.35 :transparent true :depthWrite false
          :side 2 :vertexColors false :alphaTest 0}
         (md/material-props {:name "glass" :color 0xdde8ee :roughness 0.05 :metalness 0
                              :emissive 0 :emissive-intensity 1 :opacity 0.35
                              :transparent true :side 2 :vertex-colors false :alpha-test 0}))))

(deftest case-23-material-props-opaque-flagged-but-opacity-under-1-stays-transparent
  (let [r (md/material-props {:name "glass" :opacity 0.35 :transparent false})]
    (is (true? (:transparent r)))
    (is (false? (:depthWrite r)))
    (is (= 0.35 (:opacity r)))))

(deftest case-24-material-props-opaque-paint
  (is (= {:name "booth-red" :color 0xffffff :roughness 0.7 :metalness 0 :emissive 0
          :emissiveIntensity 1 :opacity 1 :transparent false :depthWrite true
          :side 0 :vertexColors false :alphaTest 0}
         (md/material-props {:name "booth-red" :color 0xffffff :roughness 0.7 :metalness 0
                              :opacity 1 :transparent false}))))

(deftest case-25-material-props-defaults
  (is (= {:name "" :color 0xffffff :roughness 1 :metalness 0 :emissive 0
          :emissiveIntensity 1 :opacity 1 :transparent false :depthWrite true
          :side 0 :vertexColors false :alphaTest 0}
         (md/material-props {}))))

(deftest case-26-material-props-bad-values
  (is (= {:name "" :color 0xffffff :roughness 1 :metalness 0 :emissive 0
          :emissiveIntensity 1 :opacity 1 :transparent false :depthWrite true
          :side 0 :vertexColors false :alphaTest 0}
         (md/material-props {:name 7 :color "red" :roughness 2 :metalness -1 :emissive 1.5
                              :emissive-intensity -1 :opacity 1.5 :side 5 :alpha-test 2}))))

(deftest case-27-material-props-nan
  (let [r (md/material-props {:roughness js/NaN :opacity js/NaN})]
    (is (= 1 (:roughness r)))
    (is (= 1 (:opacity r)))
    (is (false? (:transparent r)))))

(deftest case-28-material-props-fully-clear
  (let [r (md/material-props {:opacity 0})]
    (is (= 0 (:opacity r)))
    (is (true? (:transparent r)))
    (is (false? (:depthWrite r)))))

(deftest case-29-material-props-flagged-transparent-at-full-opacity
  (let [r (md/material-props {:opacity 1 :transparent true})]
    (is (true? (:transparent r)))
    (is (false? (:depthWrite r)))))

(deftest case-30-material-props-lamp-glow-emissive-kept
  (let [r (md/material-props {:name "lamp" :color 0xfff4d6 :emissive 0xffd28a
                               :emissive-intensity 2 :roughness 0.4})]
    (is (= 0xffd28a (:emissive r)))
    (is (= 2 (:emissiveIntensity r)))
    (is (= 0.4 (:roughness r)))
    (is (false? (:transparent r)))))

(deftest case-31-material-props-vertex-colours
  (is (true? (:vertexColors (md/material-props {:vertex-colors true}))))
  (is (false? (:vertexColors (md/material-props {:vertex-colors "yes"})))))

;; 5.4 the booth item

(deftest case-32-shipped-booth
  (let [item (p/phone-booth-item WE)]
    (is (= [:phone-booth :box] (:key item)))
    (is (= {:url URL :materials M} (:model item)))
    (is (= 3 (count (:parts item))))
    (is (= :sign (:label item)))
    (is (= "" (:text item)))
    (is (near? [4.9 4.16 -6.6] (:label-at item)))
    (is (near? [4.9 2.785 -6.6] (:center item)))))

(deftest case-33-jacked-out-with-the-model
  (let [item (p/phone-booth-item (w/apply-event WE jo T0))]
    (is (= [:phone-booth :box] (:key item)))
    (is (= {:url URL :materials M} (:model item)))
    (is (some #(= (c/fill % {:text "Dinner!"}) (:text item))
              (:jack-out-household c/hal-lines)))))

(deftest case-34-shipped-data
  (is (= #{:at :yaw :look :model} (set (keys (:phone-booth WE)))))
  (is (= {:url URL :materials M} (get-in WE [:phone-booth :model])))
  (is (nil? (get-in WE [:phone-booth :model :materials "glass"]))))

(deftest case-35-second-booth
  (let [item (p/phone-booth-item BOX)]
    (is (= [:phone-booth :box] (:key item)))
    (is (= 90 (:yaw item)))
    (is (= {:url "/m.glb" :materials {}} (:model item)))
    (is (near? [1 4.3 3] (:label-at item)))
    (is (near? [1 3 3] (:center item)))))

(deftest case-36-bad-model-falls-back-to-the-look-alone
  (doseq [bad [{:url "m.glb"} {:url "/m.glb" :materials "x"} "x"]]
    (let [with-bad (p/phone-booth-item (assoc-in BOX [:phone-booth :model] bad))]
      (is (false? (contains? with-bad :model)))
      (is (= (dissoc (p/phone-booth-item BOX) :model) with-bad)))))

(deftest case-37-no-model
  (is (false? (contains? (p/phone-booth-item (update BOX :phone-booth dissoc :model)) :model))))

(deftest case-38-only-the-booth-carries-a-model-this-round
  (is (false? (contains? (p/workshop-item (assoc-in WE [:workshop :model] {:url "/m.glb"})) :model))))

;; 5.5 model-label-at

(deftest case-39-model-label-at-raised-above-the-shipped-model
  (is (near? [4.9 4.51 -6.6] (p/model-label-at (p/phone-booth-item WE) 2.5))))

(deftest case-40-model-label-at-second-booth
  (is (near? [1 4.8 3] (p/model-label-at (p/phone-booth-item BOX) 2.5))))

(deftest case-41-model-label-at-never-lowered
  (is (near? [1 4.3 3] (p/model-label-at (p/phone-booth-item BOX) 1.0))))

(deftest case-42-model-label-at-any-item
  (is (near? [0 2.3 0] (p/model-label-at {:at [0 0 0] :label-at [0 1.3 0]} 2))))

(deftest case-43-model-label-at-bad-top
  (doseq [top [nil js/NaN "2.5"]]
    (is (near? [1 4.3 3] (p/model-label-at (p/phone-booth-item BOX) top)))))

(deftest case-44-model-label-at-no-label-or-bad-at
  (is (nil? (p/model-label-at {:at [0 0 0]} 2)))
  (is (= [0 1.3 0] (p/model-label-at {:label-at [0 1.3 0]} 2))))

;; 5.6 clicks still reach the booth

(deftest case-45-model-mesh-name-still-resolves
  (is (= [:phone-booth :box] (pk/key-of-name (str (pr-str [:phone-booth :box]) "/" 12))))
  (is (= [:phone-booth :box] pk/booth-key)))

(deftest case-46-hit-chain-from-a-model-mesh
  (is (= [:phone-booth :box]
         (pk/pick-key [["[:phone-booth :box]/7" "[:phone-booth :box]" "pm-mirrors"]]))))
