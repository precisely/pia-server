(ns pia-server.apps.triage.depression-test
  (:require [rapids :refer :all :as rapids]
            [rapids.implementations.in-memory-storage :refer [->in-memory-storage]]
            [java-time :refer [advance-clock! mock-clock with-clock]]
            [rapids.language.test :refer [with-test-env branch keys-match flush-cache!] :as rt]
            [clojure.test :refer :all]
            [pia-server.apps.triage.flows.depression :refer :all]
            [pia-server.db.models.patient :as patient-model]
            [rapids.support.debug :as d]
            [clojure.core.match.regex]))

(deftest DepressionTest
  (branch [patient (patient-model/generate-patient 0)
           main (start! depression [(:id patient)])
           main-id (:id main)]
    "Main flow"
    (is (= :running (:state main)))
    (branch [main (continue! main-id :input {:phq2-q1 :not-at-all
                                             :phq2-q2 :not-at-all})
             result (-> main :index :depression)
             score (:score result)
             severity (:severity result)
             decision (:decision result)
             phq2 (:phq2 result)
             phq9 (:phq9 result)]
      "Negative PHQ2 screen"
      (println result)
      (is (= 0 score))
      (is (= nil severity))
      (is (= 1 decision))
      (is (= :negative (:eval phq2)))
      (is (= nil phq9))
      (is (= :complete (:state main)))
      )
    (branch [main (continue! main-id :input {:phq2-q1 :more-than-half
                                             :phq2-q2 :nearly-every})
             result (-> main :index :depression)
             phq2 (:phq2 result)]
      "Positive PHQ2 Screen"
      (is (= :positive (:eval phq2)))
      (branch [_ (continue! main-id :input {:phq9-q3 :nearly-every
                                            :phq9-q4 :nearly-every
                                            :phq9-q5 :nearly-every
                                            :phq9-q6 :nearly-every
                                            :phq9-q7 :nearly-every
                                            :phq9-q8 :nearly-every
                                            :phq9-q9 :nearly-every})
               main (continue! main-id :input {:phq9-q10 :very-difficult})
               result (-> main :index :depression)
               score (:score result)
               severity (:severity result)
               decision (:decision result)
               phq2 (:phq2 result)
               phq9 (:phq9 result)]
        "Severe condition"
        (println result)
        (is (= 26 score))
        (is (= :severe severity))
        (is (= 3 decision))
        (is (= :positive (:eval phq2)))
        (is (= :positive (:eval phq9)))
        (is (= :complete (:state main)))
        ))
    ))