(ns pia-server.apps.preop.eval.common
  (:require [malli.core :as m]))

(def decision-schema [:map
                      [:level [:number {:min 0, :max 5}]]
                      [:score [:maybe number?]]
                      [:description [:maybe string?]]
                      [:comments [:maybe string?]]])

(defn dsn
  "Preop eval decision value

  Args:
  level         clinical decision tier
  score         module score
  description
  comments"
  [level & {:keys [score desc comments]}]
  {:pre [(number? level)
         (or (nil? score) (number? score))
         (or (nil? desc) (string? desc))
         (or (nil? comments) (string? comments))]}
  {:post [(m/validate decision-schema %)]}
  {:level       level
   :score       score
   :description desc
   :comments    comments})