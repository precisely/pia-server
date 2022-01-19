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
            [pia-server.apps.anticoagulation.flows.patient :as patient]
            [pia-server.shared.ux.form :refer :all]
            [pia-server.shared.flows.pharmacy :as s-pharmacy]
            [pia-server.shared.ux.medical :refer [display-patient-labs]]
            [pia-server.shared.roles :refer [require-roles]]
            [pia-server.shared.flows.patient :refer [start-patient-reminders]]
            [pia-server.db.models.exports :refer :all]))


(defn valid-bloodwork? [patient results]
  (let [{baseline-inr     :baseline-inr,
         creatinine-level :creatinine-level,
         cbc-platelets    :cbc-platelets} results
        disease-conditions (-> patient :diseases)           ; hypertension, congestive-heart-failure
        age                (-> patient :age)
        medications        (-> patient :medications)]
    ;;
    ;; some calculation here...
    ;;
    true))

;; This section is to demonstrate a doctor manually overriding the lab test results
;;
;;(defn make-inputs-for-lab-tests
;;  "Creates <*form control inputs corresponding to a list of lab tests. This allows a doctor to manually input
;;  lab test values."
;;  [labtests]
;;  {:pre [(every? keyword? labtests)]}
;;  (map (fn [test] (number test :label (name test)))))       ;; TODO: need units
;;
;;(deflow manually-determine-initiation-dosage
;;  "When the doctor must review the patient and labwork manually. The result is a dosage or nil."
;;  [patient labwork]
;;  (display-patient-labs patient labwork)
;;  (>* "Please enter the initial warfarin dosage for the patient")
;;  (<*form (number :dosage :label "Dosage in mg/mL")))

(deflow determine-target-inr [patient labwork]
  ;; in future, this analyzes the patient (possibly the genetics and other things to determine a target therapeutic range
  2)

(deflow obtain-labwork
  "Obtains results for the patient for the given tests. Reminds the patient until the labwork is complete.
  Returns the results.

  Sets :status => {
    :lab {:initial-tests RUNID}
    :patient {:reminder {:labwork RUNID}}
  }"
  [patient tests]
  (let [lab              (block! (patient/start-pick-lab-for-orders patient tests))
        _                (println ">>>>>>>>>>\nLab selected: " lab)
        labwork-run      (start-labwork lab patient tests)
        patient-reminder (start-patient-reminders
                           patient "Please go get your labwork done"
                           :until #(not= (-> labwork-run :status :sample) "waiting")
                           :cancelable true)]
    (set-status!
      [:runs :lab :initial-tests] (:id labwork-run)
      [:runs :patient :labwork-reminder] (:id patient-reminder))
    (block! labwork-run)))

(deflow obtain-warfarin-prescription
  "Start a prescription for warfarin to the patient. Returns a run."
  [patient strength]
  (let [prescription-phase (s-pharmacy/start-prescription patient
                                                          :drug "warfarin",
                                                          :strength strength,
                                                          :unit :pills,
                                                          :frequency :as-directed,
                                                          :route :po,
                                                          :dosage :as-directed)]
    (set-status! [:runs :pharmacy :warfarin-prescription]
                 (:id prescription-phase))
    (block! prescription-phase)))

(deflow obtain-maintenance-dosage [patient target-inr]
  (let [run (start! patient/initiation-phase patient target-inr)]
    (set-status! [:runs :patient :initiation-phase] (:id run))
    (block! run)))

(defn start-maintenance-phase [patient maintenance-dosage]
  (let [run (start! patient/maintenance-phase patient maintenance-dosage)]
    (set-status! [:runs :patient :maintenance-phase] (:id run))))

(deflow anticoagulation [patient-id]
  (require-roles :doctor)
  (let [patient (get-patient patient-id)
        labwork (obtain-labwork patient [:iron :cbc :kidney])]
    (if-let [target-inr (determine-target-inr patient labwork)]
      (let [
            ;; get the initiation phase prescription - 1mg size
            _                  (obtain-warfarin-prescription patient 1)
            ;; start the initiation phase
            maintenance-dosage (obtain-maintenance-dosage patient target-inr)
            ;; get the maintenance prescription
            _                  (obtain-warfarin-prescription patient maintenance-dosage)]
        (start-maintenance-phase patient maintenance-dosage)))))

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
