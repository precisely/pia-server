(ns pia-server.apps.anticoagulation-test
  (:require [rapids :refer :all :as rapids]
            [rapids.implementations.in-memory-storage :refer [->in-memory-storage]]
            [clojure.test :refer :all]
            [pia-server.apps.anticoagulation.flows.main :refer :all]
            [pia-server.db.models.patient :as patient-model]))


(defmacro with-test-storage [& body]
  `(with-storage (->in-memory-storage)
                 ~@body))

(defmacro with-test-env
  "Provides a storage and an active connection"
  [& body]
  {:pre [(not (vector? (first body)))]}
  `(with-test-storage
     (ensure-cached-connection
       ~@body)))

(deftest AntiCoagulationTest
  (with-test-env
    (testing "main flow"
      (let [patient                       (patient-model/update-patient! {:id               999,
                                                                          :type             :patient
                                                                          :name             "Bob Smith"
                                                                          :email            "bob@bobmail.com"
                                                                          :age              55 ;; yeah, yeah, this should be bday, but this is a demo
                                                                          :sex              :male
                                                                          :race             :white
                                                                          :genetic-variants {}
                                                                          :diseases         {
                                                                                             :heart-disease  true,
                                                                                             :kidney-disease false,
                                                                                             :hypertension   false,
                                                                                             }
                                                                          :blood-pressure   123
                                                                          :phone            "1-555-555-5555"})
            main                          (start! anticoagulation 999)
            lab-tests-id                  (-> main :status :runs :lab :initial-tests)
            patient-lab-test-reminders-id (-> main :status :runs :patient :lab-test-reminder)]
        (is (= :running (:state main)))
        (is (= (-> main :status :runs)))
        (is (uuid? lab-tests-id))
        (is (uuid? patient-lab-test-reminders-id))
        (flush-cache)
        (testing "lab tests"
          (let [lab-run (get-run! lab-tests-id)]
            ))))))