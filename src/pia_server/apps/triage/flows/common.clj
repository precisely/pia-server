(ns pia-server.apps.triage.flows.common
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer :all]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.notifier :refer :all]
            [pia-server.common.util :refer [range-case round-to-nearest-half]]
            [pia-server.db.models.patient :as p]))

(deflow fmap [f s]
  (if-not (empty? s)
    (loop [[head & rest] s
           results []]
      (let [results (conj results (fcall f head))]
        (if (empty? rest) results
                          (recur rest results))))))

(defn form-value
  "Get value from form with one control group"
  [form-output]
  (nth (vals form-output) 0))


(defn dsc
  "Enforce schema for module decision value"
  [level score description]
  {:pre (number? level)
   (or (nil? score) (number? score))
   (or (nil? description) (string? description))}
  {:level       level
   :score       score
   :description description})