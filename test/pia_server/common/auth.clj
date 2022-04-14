(ns pia-server.common.auth
  (:require [clojure.test :refer :all]
            [pia-server.common.auth :refer :all]))

(deftest kcv-test
  (let [s (kcv [:id :a :b] := 1)
        t [:map [:id [:map [:a [:map [:b [:= 1]]]]]]]]
    (println (m/schema? t)))
  (is (= [:map [:id [:map [:a [:map [:b [:= 1]]]]]]] (kcv [:id :a :b] := 1))))

(defn _string_condition
  "Fake condition function for testing"
  [data & args]
  true)

(deftest is-condition-valid-test
  "Schema"
  (let [data {:id "wow"
              :num 3}]
    (is (true? (is-condition-valid data (kcv [:id] := "wow"))))
    (is (false? (is-condition-valid data (kcv [:num] :< 2)))))

  ;"Keyword"
  ;(binding [*current-user* {:id "user" :roles [:patient]}]
  ;  "Clinic user")
  ;"String tests"
  )
