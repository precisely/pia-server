(ns pia-server.shared.notifier
  (:require [rapids :refer :all]))

(defn notify [entity message & {:keys [run-id]
                                :or   {run-id (current-run :id)}}]
  (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
  (println "SENDING NOTIFICATION => " (:type entity) (:id entity) ": "
           (str {:message message :run-id run-id})))