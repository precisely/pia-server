(ns pia-server.apps.triage.drugs.tobacco-test
  (:require [rapids :refer :all :as rapids]
            [rapids.implementations.in-memory-storage :refer [->in-memory-storage]]
            [java-time :refer [advance-clock! mock-clock with-clock]]
            [rapids.language.test :refer [with-test-env branch keys-match flush-cache!] :as rt]
            [clojure.test :refer :all]
            [pia-server.apps.triage.flows.drugs.tobacco :refer :all]
            [pia-server.apps.triage.flows.common :refer :all]
            [pia-server.db.models.patient :as patient-model]
            [rapids.support.debug :as d]
            [clojure.core.match.regex]
            [java-time :as jt]))

(deftest TobaccoTest
  (branch [patient (patient-model/generate-patient 0)
           main (start! tobacco [patient])
           main-id (:id main)]
    "Main flow"
    (is (= :running (:state main)))

    (branch [main (continue! main-id :input {:has-used false})
             result (-> main :index :drugs :tobacco)
             decision (:decision result)
             has-used (-> result :has-used)]
      "Has not used tobacco"
      (is (= :complete (:state main)))
      (is (= false has-used))
      (is (= (dsc 1 nil "Lifetime non-smoker") decision)))

    (branch [main (continue! main-id :input {:has-used true})
             result (-> main :index :drugs :tobacco)
             has-used (-> result :has-used)]
      "Has used tobacco"
      (is (= :running (:state main)))
      (is (= true has-used))

      (branch [main (continue! main-id :input {:tobacco-q1 true})
               result (-> main :index :drugs :tobacco)]
        "Currently uses tobacco"

        (branch [main (continue! main-id :input {:tobacco-q2 :cigarettes})
                 result (-> main :index :drugs :tobacco)]
          "Uses cigarettes"

          (branch [main (continue! main-id :input {:tobacco-q3 5})
                   result (-> main :index :drugs :tobacco)]
            "Smokes 5 packs a day"

            (branch [main (continue! main-id :input {:tobacco-q4 2})
                     result (-> main :index :drugs :tobacco)]
              "Smoked 5 packs for 2 years"
              (branch [main (continue! main-id :input {:taps2-q1 true})
                       result (-> main :index :drugs :tobacco)]
                "TAPS-2 q1 - Smoked a cigarette containing tobacco"

                (branch [main (continue! main-id :input {:taps2-q2 false
                                                         :taps2-q3 false})
                         result (-> main :index :drugs :tobacco)
                         decision (:decision result)
                         pack-year (-> result :screening :pack-year)]
                  "Taps Score 1"
                  (is (= :complete (:state main)))
                  (is (= 10 pack-year))
                  (is (= (dsc 3 1 "current smoker") decision)))

                (branch [main (continue! main-id :input {:taps2-q2 true
                                                         :taps2-q3 false})
                         result (-> main :index :drugs :tobacco)
                         decision (:decision result)
                         pack-year (-> result :screening :pack-year)]
                  "Taps Score 2"
                  (is (= :complete (:state main)))
                  (is (= 10 pack-year))
                  (is (= (dsc 3 2 "current smoker, high risk") decision)))))))

        (branch [main (continue! main-id :input {:tobacco-q2 :chewing-tobacco})
                 result (-> main :index :drugs :tobacco)
                 decision (:decision result)]
          "Doesn't use cigarettes"
          (is (= :complete (:state main)))
          (is (= (dsc 2 nil "non-cigarette tobacco user") decision))))

      (branch [main (continue! main-id :input {:tobacco-q1 false})
               result (-> main :index :drugs :tobacco)]
        "Does not currently use tobacco"

        (branch [main (continue! main-id :input {:tobacco-q2 :cigarettes})
                 result (-> main :index :drugs :tobacco)]
          "Used cigarettes"

          (branch [main (continue! main-id :input {:tobacco-q3 5})
                   result (-> main :index :drugs :tobacco)]
            "Smoked 5 packs a day"

            (branch [main (continue! main-id :input {:tobacco-q4 4})
                     result (-> main :index :drugs :tobacco)]
              "Smoked 5 packs for 4 years"

              (branch [main (continue! main-id :input {:tobacco-q5 2000})
                       result (-> main :index :drugs :tobacco)
                       decision (:decision result)]
                "Quit in 2000"
                (is (= (dsc 3 nil "former smoker, moderate risk") decision))))

            (branch [main (continue! main-id :input {:tobacco-q4 3})
                     result (-> main :index :drugs :tobacco)]
              "Smoked 5 packs for 3 years"

              (branch [main (continue! main-id :input {:tobacco-q5 2000})
                       result (-> main :index :drugs :tobacco)
                       decision (:decision result)]
                "Quit in 2000"
                (is (= (dsc 2 nil "former smoker, low risk") decision))))

            (branch [main (continue! main-id :input {:tobacco-q4 1})
                     result (-> main :index :drugs :tobacco)]
              "Smoked 5 packs for 1 years"

              (branch [main (continue! main-id :input {:tobacco-q5 (str (jt/value (jt/year)))})
                       result (-> main :index :drugs :tobacco)
                       decision (:decision result)]
                "Quit in current year"
                (is (= :complete (:state main)))
                (is (= (dsc 3 nil "former smoker, moderate risk") decision))))))

        (branch [main (continue! main-id :input {:tobacco-q2 :pipe})
                 result (-> main :index :drugs :tobacco)]
          "Never used cigarettes"

          (branch [main (continue! main-id :input {:tobacco-q5 2020})
                   result (-> main :index :drugs :tobacco)
                   decision (:decision result)]
            "Quit in 2020"
            (is (= :complete (:state main)))
            (is (= (dsc 2 nil "former non-cigarette tobacco user") decision))))))))



