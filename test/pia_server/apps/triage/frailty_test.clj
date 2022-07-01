(ns pia-server.apps.triage.frailty-test
  (:require [rapids :refer :all :as rapids]
            [rapids.implementations.in-memory-storage :refer [->in-memory-storage]]
            [java-time :refer [advance-clock! mock-clock with-clock]]
            [rapids.language.test :refer [with-test-env branch keys-match flush-cache!] :as rt]
            [clojure.test :refer :all]
            [pia-server.apps.triage.flows.frailty :refer :all]
            [pia-server.db.models.patient :as patient-model]
            [rapids.support.debug :as d]
            [clojure.core.match.regex]))

(deftest FrailtyTest
  (branch [patient (patient-model/generate-patient 0)
           main (start! frailty [(:id patient)])
           main-id (:id main)]
    "Main flow"
    (is (= :running (:state main)))
    (branch [main (continue! main-id :input {:self-rated-q1 :good
                                             :self-rated-q2 :rarely})
             result (-> main :index :frailty)
             cfs (:cfs result)
             decision (:decision result)
             self-rated (:self-rated result)
             badl (:badl result)
             iadl (:iadl result)]
      "Does not need frailty assessment"
      (is (= nil cfs))
      (is (= 0 decision))
      (is (not (= nil self-rated)))
      (is (= nil badl))
      (is (= nil iadl))
      (is (= :complete (:state main))))
    (branch [main (continue! main-id :input {:self-rated-q1 :poor
                                             :self-rated-q2 :rarely})
             result (-> main :index :frailty)
             self-rated (:self-rated result)]
      "Needs frailty assessment"
      (is (not (= nil self-rated)))
      (branch [main (continue! main-id :input {:badl-q1 :needs-help
                                               :badl-q2 :needs-help
                                               :badl-q3 :needs-help
                                               :badl-q4 :needs-help
                                               :badl-q5 :needs-help})
               result (-> main :index :frailty)
               cfs (:cfs result)
               decision (:decision result)
               self-rated (:self-rated result)
               badl (:badl result)
               iadl (:iadl result)]
        "Severe frailty"
        (is (= 7 cfs))
        (is (= 3 decision))
        (is (not (= nil self-rated)))
        (is (not (= nil badl)))
        (is (= 5 (:score badl)))
        (is (= nil iadl))
        (is (= :complete (:state main))))
      (branch [main (continue! main-id :input {:badl-q1 :without-help
                                               :badl-q2 :without-help
                                               :badl-q3 :without-help
                                               :badl-q4 :without-help
                                               :badl-q5 :without-help})
               result (-> main :index :frailty)
               badl (:badl result)]
        "Needs IADL screen"
        (is (not (= nil badl)))
        (is (= 0 (:score badl)))
        (branch [main (continue! main-id :input {:iadl-q1 :needs-help
                                                 :iadl-q2 :needs-help
                                                 :iadl-q3 :needs-help
                                                 :iadl-q4 :needs-help
                                                 :iadl-q5 :needs-help
                                                 :iadl-q6 :needs-help})
                 result (-> main :index :frailty)
                 cfs (:cfs result)
                 decision (:decision result)
                 self-rated (:self-rated result)
                 badl (:badl result)
                 iadl (:iadl result)]
          "Passes IADL screen"
          (is (= :complete (:state main)))
          (is (= 6 cfs))
          (is (= 3 decision))
          (is (not (= nil self-rated)))
          (is (not (= nil badl)))
          (is (not (= nil iadl)))
          (is (= 6 (:score iadl))))
        (branch [main (continue! main-id :input {:iadl-q1 :without-help
                                                 :iadl-q2 :without-help
                                                 :iadl-q3 :without-help
                                                 :iadl-q4 :without-help
                                                 :iadl-q5 :without-help
                                                 :iadl-q6 :without-help})
                 result (-> main :index :frailty)
                 cfs (:cfs result)
                 decision (:decision result)
                 self-rated (:self-rated result)
                 badl (:badl result)
                 iadl (:iadl result)]
          "Low frailty test"
          (is (= :complete (:state main)))
          (is (= 4 cfs))
          (is (= 2 decision))
          (is (not (= nil self-rated)))
          (is (not (= nil badl)))
          (is (not (= nil iadl)))
          )))
    ))