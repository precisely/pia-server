(ns pia-server.flows.cfsdemo-test
  (:require [clojure.test :refer :all]
            [pia-server.flows.cfsdemo :refer :all]
            [rapids :refer :all]
            ))


(deftest welcome-test
  (testing "Can start the welcome flow"
    (println "Starting now")
    (let [run (start! welcome)]
      (println "Run started")
      (is (run-in-state? run :suspended))
      (println "This is the welcome-test :response " (:response run))
      )))
