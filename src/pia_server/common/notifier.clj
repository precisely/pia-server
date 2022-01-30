(ns pia-server.common.notifier
  (:require [rapids :refer :all]))

(defn notify [entity message & {:keys [run-id]
                                :or   {run-id (current-run :id)}}]
  {:pre [(map? entity) (contains? entity :id) (contains? entity :type)]}
  (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
  (println "SENDING NOTIFICATION => " (:type entity) (:id entity)
           (str {:message message :run-id run-id})))