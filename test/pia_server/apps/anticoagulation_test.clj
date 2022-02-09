(ns pia-server.apps.anticoagulation-test
  (:require [rapids :refer :all :as rapids]
            [rapids.implementations.in-memory-storage :refer [->in-memory-storage]]
            [java-time :refer [advance-clock! mock-clock with-clock]]
            [rapids.language.test :refer [with-test-env branch keys-match flush-cache!] :as rt]
            [clojure.test :refer :all]
            [pia-server.apps.anticoagulation.flows.main :refer :all]
            [pia-server.db.models.patient :as patient-model]
            [rapids.support.debug :as d]
            [clojure.core.match.regex]))

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
  (let [clock    (mock-clock)
        advance! (fn [t]
                   (flush-cache!)
                   (advance-clock! clock t)
                   (find-and-expire-runs! 100)
                   true)]
    (with-clock clock
      (branch [patient                      (make-patient)
               main                         (start! anticoagulation [(:id patient)])
               main-id                      (:id main)
               lab-tests-id                 (-> main :index :runs :lab :initial-tests)
               genetic-tests-id             (-> main :index :runs :lab :initial-tests)
               patient-labwork-reminders-id (-> main :index :runs :patient :labwork-reminder)]
        "Main flow"
        (is (= :running (:state main)))
        (is (uuid? lab-tests-id))
        (is (uuid? patient-labwork-reminders-id))
        (println "Runs:\n"
                 {:main-id          main-id
                  :lab-tests-id     lab-tests-id
                  :lab-reminders-id patient-labwork-reminders-id})
        #_(testing "patient interface shows a cancel button"
            (keys-match (d/print-result (get-run patient-labwork-reminders-id))
              :state :running
              :output [{:type :buttons, :buttons [{:id :cancel}]} & _]))

        (branch [lab-run               (get-run lab-tests-id)
                 lab-run               (continue! lab-tests-id :input {:status               :success
                                                                       :anemia               :normal
                                                                       :liver-function-tests :normal}
                                                  :permit (-> lab-run :output :permit))
                 main                  (get-run main-id)
                 pharmacy-prescribe-id (-> main :index :runs :pharmacy :warfarin-prescription)]
          "Deliver lab test results"
          (println "Runs:"
                   {:pharmacy-prescribe-id pharmacy-prescribe-id})
          (is (= :complete (:state lab-run)))
          (is (uuid? pharmacy-prescribe-id))

          (branch [pharmacy-run  (continue! pharmacy-prescribe-id :input :delivered)
                   main-run      (get-run main-id)
                   init-phase-id (-> main-run :index :runs :patient :initiation-phase)
                   init-phase    (get-run init-phase-id)]
            "Lab produces result"
            (println "Runs:"
                     {:init-phase-id init-phase-id})
            (is (= :complete (:state pharmacy-run)))
            (is (uuid? init-phase-id))
            (keys-match init-phase
              :state :running
              :output [_ {:type :form :elements [{:type :number :id :inr} & _]}])

            (branch [init-phase-run (continue! init-phase-id :input {:inr 1.5})
                     init-phase-id  (:id init-phase-run)]
              "The patient inputs a normal INR level"
              (keys-match init-phase-run
                :state :running
                :output [_ _ _ {:type :buttons :buttons [{:id :yes} {:id :problem} & _]}])

              (branch [init-phase-run (continue! init-phase-id :input :yes)]
                "The patient responds they did take their dose."
                (keys-match init-phase-run
                  :state :running
                  :output [{:type :text :text #".*I'll check in tomorrow.*"}])

                (is (advance! (hours 25)))

                (keys-match (get-run init-phase-id)
                  :state :running
                  :output [{:text #".*Please use your INR test.*"}
                           {:type :form :elements [{:id :inr}]}])))))))))