;;
;; The main flow which orchestrates anticoagulation.
;; This flow interacts with the healthcare team UI,
;; and provides top-level administrative functionality
;;
;; Healthcare staff interact with the flows defined here.
;; I.e., :roles [:doctor]
;;
(ns pia-server.apps.anticoagulation.flows.main
  (:require [rapids :refer :all]
            [pia-server.apps.anticoagulation.flows.lab :refer [start-labwork]]
            [pia-server.apps.anticoagulation.flows.patient
             :refer [start-patient-initiation-phase start-pick-lab-for-orders start-patient-reminders]]
            [pia-server.shared.ux.form :refer :all]
            [pia-server.shared.ux.medical :refer [display-patient-labs]]
            [pia-server.shared.flows.patient :refer [start-patient-reminders]]
            [pia-server.db.models.exports :refer :all]))

(deflow get-baseline-inr [patient clinic]
  #_#_#_(let [inr-lab (get-inr-lab clinic)])
      (notify clinic patient :initial-inr)
      (notify-patient patient clinic "You "))

(defn calculate-initiation-dosage [patient labwork]
  ;; Gareth - some function here, please
  1)

(defn valid-bloodwork? [patient results]
  (let [{baseline-inr     :baseline-inr,
         creatinine-level :creatinine-level
         cbc-platelets    :cbc-platelets} results
        disease-conditions (-> patient :diseases)           ; hypertension, congestive-heart-failure
        age                (-> patient :age)
        medications        (-> patient :medications)]
    ;;
    ;; some calculation here...
    ;;
    true))

(defn make-inputs-for-lab-tests
  "Creates <*form control inputs corresponding to a list of lab tests. This allows a doctor to manually input
  lab test values."
  [labtests]
  {:pre [(every? keyword? labtests)]}
  (map (fn [test] (number test :label (name test)))))       ;; TODO: need units

(defn convert-form-result-to-lab-result
  "Converts the result of a form"
  [result]
  result)

(deflow manually-determine-initiation-dosage
  "When the doctor must review the patient and labwork manually. The result is a dosage or nil."
  [patient labwork]
  (display-patient-labs patient labwork)
  (>* "Please enter the initial coumadin dosage for the patient")
  (<*form (number :dosage :label "Dosage in mg/mL")))

(deflow determine-initial-dosage
  "Determines the initial dosage."
  [patient labwork]
  (let [initial-dosage (if (valid-bloodwork? patient labwork)
                         (calculate-initiation-dosage patient labwork)
                         (manually-determine-initiation-dosage patient labwork))]
    (if-not initial-dosage
      (raise (->interruption :stop :message "Unable to determine initial dosage. Program stopped."))
      initial-dosage)))

(deflow order-coumadin-prescription
  [patient dosage]
  )

(deflow initiation-loop (start! start-patient-initiation-phase patient initiation-dosage)
  (set-status! [:patient :protocol :initiation] (:id initiation-run))
  (block! initiation-run))

(deflow obtain-labwork
  "Obtains results for the patient for the given tests. Reminds the patient until the labwork is complete.
  Returns the results.

  Sets :status => {
    :lab {:initial-tests RUNID}
    :patient {:reminder {:labwork RUNID}}
  }"
  [patient tests]
  (let [lab              (block! (start-pick-lab-for-orders patient tests))
        labwork-run      (start-labwork lab patient tests)
        patient-reminder (start-patient-reminders
                           patient "Please go get your labwork done"
                           :until #(not= (-> labwork-run :status :sample) "waiting"))]
    (set-status!
      [:lab :initial-tests] labwork-run
      [:patient :reminder :labwork] (:id patient-reminder))
    (block! labwork-run)))

(deflow anticoagulation [patient-id clinic-id]
  (set-status! :roles [:doctor])
  (let [patient (get-patient patient-id)
        clinic  (get-clinic clinic-id)
        labwork (obtain-labwork patient [:iron :cbc :kidney])]
    (if-let [dosage (determine-initial-dosage patient labwork)]
      (let [prescription    (prescribe-coumadin patient dosage)
            maintenance-run (start-maintenance-loop patient dosage)])
      (set-status! [:patient :protocol :maintenance]
                   (:id (start-maintenance-loop patient dosage))))))


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
