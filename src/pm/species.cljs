(ns pm.species
  "File index for the shipped species: a map of {kind edn-text}. This is the
   one place in src/ allowed to name a species (`shadow.resource/inline`
   needs literal paths), and it does nothing else."
  (:require [shadow.resource :as rc]))

(def texts
  {:horse (rc/inline "creatures/horse.edn")
   :dog   (rc/inline "creatures/dog.edn")})
