(ns pia-server.common.rapids-ext
  (:require [rapids :refer :all]))

(deflow wait-for
  "Wait for all passed runs to complete. Returns the results of the completed runs.

  (wait-for! run1 [run2 :expires (2 days) :default :expired] run3)
  => [run1-result, run2-result-or-:default, run3-result]"
  [& blockers]
  (if (empty? blockers)
    []
    (loop [[blocker & blockers] blockers
           results []]
      (let [[run & {:keys [expires default]}] (if (sequential? blocker) blocker [blocker])
            results (conj results (block! run :expires expires :default default))]
        (if (empty? blockers)
          results
          (recur blockers results))))))