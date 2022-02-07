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
            [pia-server.common.flows.lab :refer [lab-monitor]]
            [pia-server.apps.anticoagulation.flows.patient :as patient]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.flows.pharmacy :as common-pharmacy]
            [pia-server.common.controls.medical :refer [display-patient-labs]]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.flows.patient :as common-patient]
            [pia-server.db.models.exports :refer :all]
            [pia-server.db.models.patient :as p]))


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

  Sets :index => {
    :lab {:initial-tests RUNID}
    :patient {:reminder {:labwork RUNID}}
  }"
  [patient tests]
  {:pre [(p/patient? patient)]}
  ;; TODO: start the lab monitor from within the patient run
  (let [lab              (block! (start! common-patient/pick-lab
                                         [patient tests]
                                         :index {:patient-id (:id patient)
                                                  :title      "Choose lab for anticoagulation bloodwork"}))
        _                (println ">>>>>>>>>>\nLab selected: " lab)
        labwork-run      (start! lab-monitor [lab patient tests] :index {:title      "Anticoagulation lab monitor"
                                                                          :patient-id (:id patient)})
        patient-reminder (start! common-patient/send-reminders
                                 [patient "Please go get your labwork done"
                                  :until #(not= (-> labwork-run :index :sample) "waiting")
                                  :cancelable true]
                                 :index {:title "Anticoagulation labwork"})]
    (set-index!
      [:overview :phase] "Labwork"
      [:runs :lab :initial-tests] (:id labwork-run)
      [:runs :patient :labwork-reminder] (:id patient-reminder))
    (block! labwork-run)))

(defn start-run! [role action flow & args]
  (let [run (start! flow args)]
    (set-index! [:runs role action] (:id run))
    run))

(deflow obtain-warfarin-prescription
  "Start a prescription for warfarin to the patient. Returns a run."
  [patient strength]
  (let [prescription-phase (start! common-pharmacy/order-prescription
                                   [patient
                                    :drug "warfarin",
                                    :strength strength,
                                    :unit :pills,
                                    :frequency :as-directed,
                                    :dispense 100,
                                    :route :po,
                                    :dosage :as-directed]
                                   :index {:title      "Anticoagulation prescription"
                                            :patient-id (:id patient)})]
    (set-index!
      [:overview :phase] "Prescription"
      [:runs :pharmacy :warfarin-prescription] (:id prescription-phase))
    (block! prescription-phase)))

(deflow obtain-maintenance-dosage [patient target-inr]
  (let [dosage-pool (->pool)
        run         (start! patient/initiation-phase
                            [patient target-inr dosage-pool]
                            :index {:title      "Anticoagulation initiation phase"
                                     :patient-id (:id patient)})]
    (set-index!
      [:overview :phase] "Initiation"
      [:runs :patient :initiation-phase] (:id run))

    ;; retrieve dosage until the dosage-pool returns nil
    (loop [dosage (take-out! dosage-pool)]
      (when dosage
        (set-index! [:overview :dosage] dosage)
        (recur (take-out! dosage-pool))))))

(defn start-maintenance-phase [patient maintenance-dosage]
  (let [run (start! patient/maintenance-phase
                    [patient maintenance-dosage]
                    :index {:title      "Anticoagulation maintenance phase"
                             :patient-id (:id patient)})]
    (set-index! [:runs :patient :maintenance-phase] (:id run))))

(defn check-for-existing-anticoagulation-run [patient-id]
  (let [[run] (find-runs [[:state :eq :running]
                          [[:index :patient-id] :eq patient-id]
                          [:id :not-eq (current-run :id)]
                          [[:index :roles] :? "doctor"]] :limit 2)]
    (if run
      (throw (ex-info "Anticoagulation therapy already in progress for patient"
                      {:type :input-error
                       :patient-id patient-id
                       :run-id (:id run)})))))

(deflow anticoagulation [patient-id]
  (require-roles :doctor)
    (set-index! :patient-id patient-id, :title "Anticoagulation therapy")
  (check-for-existing-anticoagulation-run patient-id)
  (let [patient (get-patient patient-id)
        _       (if (not (p/patient? patient)) (throw (ex-info "Patient not found" {:type :input-error :id patient-id})))
        labwork (obtain-labwork patient [{:type :iron} {:type :cbc} {:type :kidney}])]
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
;; clinic interface monitors patient index
;; initiation loop
;; maintenance loop
;; interrupt the flow
