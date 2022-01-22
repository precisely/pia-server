(ns pia-server.apps.anticoagulation-test
  (:require [rapids :refer :all :as rapids]
            [rapids.implementations.in-memory-storage :refer [->in-memory-storage]]
            [java-time :refer [advance-clock! mock-clock with-clock]]
            [rapids.language.test :refer [with-test-env branch keys-match flush-cache!] :as rt]
            [clojure.test :refer :all]
            [pia-server.apps.anticoagulation.flows.main :refer :all]
            [pia-server.db.models.patient :as patient-model]))

(defn make-patient []
  (patient-model/update-patient! {:id               999,
                                  :type             :patient
                                  :name             "Bob Smith"
                                  :email            "bob@bobmail.com"
                                  :age              55      ;; yeah, yeah, this should be bday, but this is a demo
                                  :sex              :male
                                  :race             :white
                                  :genetic-variants {}
                                  :diseases         {
                                                     :heart-disease  true,
                                                     :kidney-disease false,
                                                     :hypertension   false,
                                                     }
                                  :blood-pressure   123
                                  :phone            "1-555-555-5555"}))

(deftest AntiCoagulationTest
  (let [clock (mock-clock)
        advance! (fn [t] (advance-clock! clock t))]
    (branch [patient                       (make-patient)
             main                          (start! anticoagulation (:id patient))
             lab-tests-id                  (-> main :status :runs :lab :initial-tests)
             patient-lab-test-reminders-id (-> main :status :runs :patient :lab-test-reminder)]
      "Main flow"
      (is (= :running (:state main)))
      (is (uuid? lab-tests-id))
      (is (uuid? patient-lab-test-reminders-id))

      (branch [lab-run     (get-run! lab-tests-id)]
        "Deliver lab tests"
        (keys-match lab-run
          :state :running
          :response [[{:type :buttons, :buttons [{:id :cancel}]}]])

        (branch [lab-run (continue! lab-tests-id :data {:status "success", :result {:kidney 1}})]
          "Lab produces result"
          )))))