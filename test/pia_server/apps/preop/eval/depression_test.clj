(ns pia-server.apps.preop.eval.depression-test
  (:require [clojure.test :refer :all]
            [rapids :refer :all]
            [pia-server.apps.preop.common :refer :all]
            [rapids.language.test :refer [with-test-env branch keys-match flush-cache!] :as rt]
            [rapids.active-doc :as adoc]
            [pia-server.common.controls.form :refer :all]
            [java-time :as t]
            [pia-server.common.docs :refer :all]))

(deftest _obtain-phq2-test
  (branch [doc (retrieve-patient-doc :preop-depression patient-id :schema depression-schema)
           main (start!)]
    "Asks questions"
    (is (= :running (:state main)))
    (branch [main (continue! (:id main) :input {:q1 true :q2 :a})]
      "Answers questions"
      (is (= :complete (:state main)))
      (is (= {:q1 true :q2 :a} (:result main)))
      (branch [q3   (number :q3)
               main (start! <*ask [doc [:test] [q3]])
               main (continue! (:id main) :input {:q3 1})
               data (adoc/get-data doc [:test :questions])]
        "Merges answers"
        (is (= :complete (:state main)))
        (is (contains? data :q1))
        (is (contains? data :q2))
        (is (contains? data :q3))))))

(deftest _obtain-phq9-test
  (branch [doc  (adoc/create!)
           q1   (yesno :q1)
           q2   (multiple-choice :q2 [:a :b])
           main (start! <*ask [doc [:test] [q1 q2]])]
    "Asks questions"
    (is (= :running (:state main)))
    (branch [main (continue! (:id main) :input {:q1 true :q2 :a})]
      "Answers questions"
      (is (= :complete (:state main)))
      (is (= {:q1 true :q2 :a} (:result main)))
      (branch [q3   (number :q3)
               main (start! <*ask [doc [:test] [q3]])
               main (continue! (:id main) :input {:q3 1})
               data (adoc/get-data doc [:test :questions])]
        "Merges answers"
        (is (= :complete (:state main)))
        (is (contains? data :q1))
        (is (contains? data :q2))
        (is (contains? data :q3))))))

(deftest obtain-depression-test
  (branch [doc  (adoc/create!)
           q1   (yesno :q1)
           q2   (multiple-choice :q2 [:a :b])
           main (start! <*ask [doc [:test] [q1 q2]])]
    "Asks questions"
    (is (= :running (:state main)))
    (branch [main (continue! (:id main) :input {:q1 true :q2 :a})]
      "Answers questions"
      (is (= :complete (:state main)))
      (is (= {:q1 true :q2 :a} (:result main)))
      (branch [q3   (number :q3)
               main (start! <*ask [doc [:test] [q3]])
               main (continue! (:id main) :input {:q3 1})
               data (adoc/get-data doc [:test :questions])]
        "Merges answers"
        (is (= :complete (:state main)))
        (is (contains? data :q1))
        (is (contains? data :q2))
        (is (contains? data :q3))))))