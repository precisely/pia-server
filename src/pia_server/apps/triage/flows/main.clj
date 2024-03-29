;;
;; The main flow which orchestrates prehab triage.
;; This flow interacts with the healthcare team UI,
;; and provides top-level administrative functionality
;;
;; Healthcare staff interact with the flows defined here.
;; I.e., :roles [:doctor]
;;
(ns pia-server.apps.triage.flows.main
  (:require [rapids :refer :all]
            [pia-server.common.flows.lab :refer [lab-monitor] :as lab]
            [pia-server.db.models.lab :as lab-db]
            [pia-server.apps.anticoagulation.flows.patient :as patient]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.flows.pharmacy :as common-pharmacy]
            [pia-server.common.controls.medical :refer [display-patient-labs]]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.flows.patient :as common-patient]
            [pia-server.db.models.exports :refer :all]
            [pia-server.db.models.patient :as p]
            [pia-server.common.controls.form :as f]
            [pia-server.apps.triage.flows.depression :refer [depression]]
            [pia-server.apps.triage.flows.frailty :refer [frailty]]))

(deflow depression-flow [patient-id]
  (let [patient (get-patient patient-id)
        _ (if (not (p/patient? patient)) (throw (ex-info "Patient not found" {:type :input-error :id patient-id})))]
    (depression patient))
  )

(deflow frailty-flow [patient-id]
  (let [patient (get-patient patient-id)
        _ (if (not (p/patient? patient)) (throw (ex-info "Patient not found" {:type :input-error :id patient-id})))]
    (frailty patient))
  )