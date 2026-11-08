#!/usr/bin/env bb
;; Print a species' MuJoCo body (MJCF XML): bb creature-mjcf <kind>
;; Reads resources/creatures/<kind>.edn through pm.creature, the pure cljc
;; namespace the game itself uses (species -> MJCF).

(require '[pm.creature :as c]
         '[clojure.java.io :as io])

(defn- usage! []
  (binding [*out* *err*]
    (println "usage: bb creature-mjcf <kind>  (no resources/creatures/<kind>.edn)"))
  (System/exit 1))

(let [kind (first *command-line-args*)
      file (when kind (io/file "resources/creatures" (str kind ".edn")))]
  (if (or (nil? kind) (not (.exists file)))
    (usage!)
    (try
      (print (c/mjcf (c/parse (slurp file)) c/origin))
      (catch Exception e
        (binding [*out* *err*] (println (ex-message e)))
        (System/exit 1)))))
