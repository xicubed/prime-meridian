(ns pm.fixtures)

(def lines-v0
  "One-variant line table holding the estate's pre-content strings, so tests
   written before the content round keep their exact expected texts."
  {:mirrors {:flora   {:hungry ["I'm hungry"]   :low ["Worms left: {stock}"] :stock-sign ["Worms: {stock}"]}
             :juniper {:hungry ["Hay, please?"] :low ["Bales left: {bales}"] :stock-sign ["Bales: {bales}"]}}})
