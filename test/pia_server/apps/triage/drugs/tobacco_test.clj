(ns pia-server.apps.triage.drugs.tobacco-test
  (:require [rapids :refer :all :as rapids]
            [rapids.implementations.in-memory-storage :refer [->in-memory-storage]]
            [java-time :refer [advance-clock! mock-clock with-clock]]
            [rapids.language.test :refer [with-test-env branch keys-match flush-cache!] :as rt]
            [clojure.test :refer :all]
            [pia-server.apps.triage.flows.drugs.tobacco :refer :all]
            [pia-server.db.models.patient :as patient-model]
            [rapids.support.debug :as d]
            [clojure.core.match.regex]))

(deftest TobaccoTest
  (branch [patient (patient-model/generate-patient 0)
           main (start! tobacco [(:id patient)])
           main-id (:id main)]
    "Main flow"
    (is (= :running (:state main)))))