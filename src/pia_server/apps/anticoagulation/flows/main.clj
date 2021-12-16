;;
;; The main flow which orchestrates anticoagulation.
;; This flow interacts with the healthcare team UI,
;; and provides top-level administrative functionality.
;;
(ns pia-server.apps.anticoagulation.flows.main
  (:require [rapids :refer :all]
            [pia-server.apps.anticoagulation.flows.lab :refer [order-labs]]
            [pia-server.db.models.exports :refer :all]))

(defn notify [entity message & {:keys [run-id]
                                :or   {run-id (current-run :id)}}]
  (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
  (println "SENDING NOTIFICATION => " entity ": "
           {:message message :run-id run-id}))

(deflow get-baseline-inr [patient clinic]
  #_#_#_(let [inr-lab (get-inr-lab clinic)])
      (notify clinic patient :initial-inr)
      (notify-patient patient clinic "You "))



(defn get-disease-conditions [patient]                      ; could involve other processes
  (:diseases patient))

(defn get-patient-age [patient]                             ; could be a question
  (:age patient))

;; Version 0: curl https://precisely/api/runs/{current-run-id} -X '{"iron"
;; Version 1: curl https://precise.ly/callbacks/labcorp/ -X '{"accession-number" 123123, }'

(defn get-patient-medications [patient])
(defn get-patient-schedule [patient])

(deflow anticoagulation [pid]
  (let [patient            (get-patient pid)
        blood-order        (<<! (start! order-labs patient [:iron :cbc]))
        {baseline-inr     :baseline-inr,
         creatinine-level :creatinine-level
         cbc-platelets    :cbc-platelets} blood-order
        disease-conditions (get-disease-conditions patient) ; hypertension, congestive-heart-failure
        age                (get-patient-age patient)
        medications        (get-patient-medications patient)
        upcoming-surgeries (get-patient-schedule patient)]
    (print "blood-order-run" blood-order)))

;; onboarding
;; (+) get blood work done
;; pharmacy order
;;   - stdout
;;   - patient notified
;;   - patient nagged
;; doctor escalation (steps)
;; clinic interface monitors patient status
;; initiation loop
;; maintenance loop
;; interrupt the flow
