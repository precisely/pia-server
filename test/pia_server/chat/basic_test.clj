(ns pia_server.chat.basic_test
  (:require [pia-server.chat.basic :refer :all]
            [clojure.test :refer :all]))

(deftest Choices
  (testing "Creates a button choice by default"
    (is (= (make-choices :yes "Yes!" :no "No!")
          {:type :choices, :style :buttons,
           :choices [{:id :yes, :text "Yes!"},
                     {:id :no, :text "No!"}]})))

  (testing "allows :list style"
    (is (= (make-choices :list :yes "Yes!" :no "No!")
          {:type :choices, :style :list,
           :choices [{:id :yes, :text "Yes!"},
                     {:id :no, :text "No!"}]})))

  (testing "Checks for invalid style"
    (is (thrown? AssertionError (make-choices :foo :yes "Yes!" :no "No!")))))