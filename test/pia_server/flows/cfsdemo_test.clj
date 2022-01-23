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
      (is (run? run))
      (is (= :running (:state run)))

      #_(println "This is the welcome-test :output " (:output run)))))
