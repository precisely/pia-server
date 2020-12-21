(ns pia-server.handler-test
  (:require [clojure.test :refer :all]
            [pia-server.app :refer :all]
            [rapids :refer :all]))

(deftest foo-test
  (testing "Can start a flow"
    (let [run (start! foo)]
      (is (run-in-state? run :suspended))
      (is (= (:response run) '["hello"]))

      (testing "can continue a flow"
        (let [run (continue! (:id run) {:data "cruel" :permit "the-permit"} [])]
          (is (run-in-state? run :complete))
          (is (= (:response run) '["cruel world!"])))))))
