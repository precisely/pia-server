(ns pia-server.apps.triage.drugs.alcohol-test
  (:require [rapids :refer :all :as rapids]
            [rapids.implementations.in-memory-storage :refer [->in-memory-storage]]
            [java-time :refer [advance-clock! mock-clock with-clock]]
            [rapids.language.test :refer [with-test-env branch keys-match flush-cache!] :as rt]
            [clojure.test :refer :all]
            [pia-server.apps.triage.flows.drugs.alcohol :refer :all]
            [pia-server.apps.triage.flows.common :refer :all]
            [pia-server.db.models.patient :as patient-model]
            [rapids.support.debug :as d]
            [clojure.core.match.regex]))

(deftest AlcoholTest
  (branch [patient (assoc-in (patient-model/generate-patient 0) [:sex] :male)
           main (start! alcohol [patient])
           main-id (:id main)]
    "Male patient"
    (is (= :running (:state main)))
    (branch [main (continue! main-id :input {:initial-q1 0})
             result (-> main :index :drugs :alcohol)
             decision (:decision result)]
      "No alcohol"
      (is (= :complete (:state main)))
      (is (= (dsc 1 nil "No alcohol consumption") decision)))
    (branch [main (continue! main-id :input {:initial-q1 7})]
      "Drinks alcohol every day"
      (is (= :running (:state main)))
      (branch [main (continue! main-id :input {:initial-q2 2})
               result (-> main :index :drugs :alcohol)
               decision (:decision result)]
        "2 drinks a day"
        (is (= :complete (:state main)))
        (is (= (dsc 1 nil "Low-risk alcohol consumption") decision)))

      (branch [main (continue! main-id :input {:initial-q2 3})
               result (-> main :index :drugs :alcohol)]
        "3 drinks a day"

        (is (= :running (:state main)))
        (branch [main (continue! main-id :input {:taps2-q2 false
                                                 :taps2-q3 false
                                                 :taps2-q4 false})
                 result (-> main :index :drugs :alcohol)
                 decision (:decision result)]
          "TAPS2 Score = 1"
          (is (= :complete (:state main)))
          (is (= (dsc 1 1 "Low-risk alcohol consumption") decision)))

        (branch [main (continue! main-id :input {:taps2-q2 true
                                                 :taps2-q3 false
                                                 :taps2-q4 false})
                 result (-> main :index :drugs :alcohol)
                 decision (:decision result)]
          "TAPS2 Score = 2"
          (is (= :complete (:state main)))
          (is (= (dsc 2 2 "Moderate-risk alcohol consumption") decision)))

        (branch [main (continue! main-id :input {:taps2-q2 true
                                                 :taps2-q3 true
                                                 :taps2-q4 true})
                 result (-> main :index :drugs :alcohol)
                 decision (:decision result)]
          "TAPS2 Score = 4"
          (is (= :complete (:state main)))
          (is (= (dsc 3 4 "High-risk alcohol consumption") decision))
          )
        )
      )
    )

  (branch [patient (assoc-in (patient-model/generate-patient 0) [:sex] :female)
           main (start! alcohol [patient])
           main-id (:id main)]
    "Female patient"
    (is (= :running (:state main)))
    (branch [main (continue! main-id :input {:initial-q1 0})
             result (-> main :index :drugs :alcohol)
             decision (:decision result)]
      "No alcohol"
      (is (= :complete (:state main)))
      (is (= (dsc 1 nil "No alcohol consumption") decision)))
    ;
    (branch [main (continue! main-id :input {:initial-q1 7})]
      "Drinks alcohol every day"
      (is (= :running (:state main)))

      (branch [main (continue! main-id :input {:initial-q2 1})
               result (-> main :index :drugs :alcohol)
               decision (:decision result)]
        "1 drink a day"
        (is (= :complete (:state main)))
        (is (= (dsc 1 nil "Low-risk alcohol consumption") decision)))

      (branch [main (continue! main-id :input {:initial-q2 2})
               result (-> main :index :drugs :alcohol)
               decision (:decision result)]
        "2 drinks a day"

        (branch [main (continue! main-id :input {:taps2-q2 false
                                                 :taps2-q3 false
                                                 :taps2-q4 false})
                 result (-> main :index :drugs :alcohol)
                 decision (:decision result)]
          "TAPS2 Score = 1"
          (is (= :complete (:state main)))
          (is (= (dsc 1 1 "Low-risk alcohol consumption") decision)))

        (branch [main (continue! main-id :input {:taps2-q2 true
                                                 :taps2-q3 false
                                                 :taps2-q4 false})
                 result (-> main :index :drugs :alcohol)
                 decision (:decision result)]
          "TAPS2 Score = 2"
          (is (= :complete (:state main)))
          (is (= (dsc 2 2 "Moderate-risk alcohol consumption") decision)))

        (branch [main (continue! main-id :input {:taps2-q2 true
                                                 :taps2-q3 true
                                                 :taps2-q4 true})
                 result (-> main :index :drugs :alcohol)
                 decision (:decision result)]
          "TAPS2 Score = 4"
          (is (= :complete (:state main)))
          (is (= (dsc 3 4 "High-risk alcohol consumption") decision)))
        )
      )
  ))




