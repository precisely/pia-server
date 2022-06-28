(ns pia-server.common.auth_test
  (:require [clojure.test :refer :all]
            [pia-server.common.auth :refer :all]
            [rapids.language.test :refer [with-test-env branch keys-match flush-cache!] :as rt]
            [rapids.active-doc :as adoc]
            [rapids :refer :all]
            [malli.core :as m]))

(deftest kcv-test
  (is (= [:map [:id [:map [:a [:map [:b [:= 1]]]]]]] (kcv [:id :a :b] := 1))))

(deftest _is-condition-valid-test
  "Schema"
  (let [data {:id  "wow"
              :num 3}]
    (is (true? (_is-condition-valid data (kcv [:id] := "wow"))))
    (is (false? (_is-condition-valid data (kcv [:num] :< 2)))))

  "Function"
  (let [data {:id  "foo"
              :bar 3}
        f    (fn [d a b c] (and (= "foo" (:id d)) a b c))]
    (is (_is-condition-valid data [f true true true]))
    (is (false? (_is-condition-valid data [f true false true])))))

(defn _function-ex
  [data patient-id]
  (assert-role :clinic)
  (= (:patient-id data) patient-id))

(defn _function2-ex
  [data clinic-uid]
  (assert-role :clinic)
  (= (:clinic data) clinic-uid))


(def _schema-ex {:patient {:user [(kcv [:id] := "a")]}
                 :clinic  {:doc   [[_function-ex "a"]
                                   [_function2-ex "b"]]
                           :index [(kcv [:c] := "c")]}
                 :admin   {:index [(kcv [:a] := "a")
                                   (kcv [:b] := "b")]}})

(deftest check-auth-test
  "With schema as argument"
  (branch [main (adoc/create!
                  :data {:patient-id "a" :clinic "b"}
                  :index {:a "a" :b "b" :c "c" :type :doc})]
    "Document with correct data"
    (binding [*current-user* {:id "a" :roles [:patient]}]
      "Patient"
      (is (check-auth main _schema-ex)))
    (binding [*current-user* {:id "b" :roles [:clinic]}]
      "Clinic"
      (is (check-auth main _schema-ex)))
    (binding [*current-user* {:id "c" :roles [:admin]}]
      "Admin"
      (is (check-auth main _schema-ex))))

  (branch [main (adoc/create!)]
    "Document with incorrect data"
    (binding [*current-user* {:id "b" :roles [:patient]}]
      "Invalid Patient"
      (is (false? (check-auth main _schema-ex))))
    (binding [*current-user* {:id "a" :roles [:clinic]}]
      "Invalid Clinic"
      (is (false? (check-auth main _schema-ex))))
    (binding [*current-user* {:id "c" :roles [:admin]}]
      "Invalid Admin"
      (is (false? (check-auth main _schema-ex))))))

(def _schema-ex2 {:patient {:user [(kcv [:id] := "b")]}
                  :clinic  {}})

(deftest grant-run-test
  (branch [main (adoc/create! :index {:a "a" :b "b" :type :doc})]
    "Document with index data"
    (binding [*current-user* {:id "a" :roles [:patient]}]
        "Patient A can't start run"
        (is (false? (try (grant-run main _schema-ex2 `_schema-ex) (catch Exception _ false)))))

    (binding [*current-user* {:id "b" :roles [:patient]}]
      "Patient B can start run"
      (grant-run main _schema-ex2 `_schema-ex)
      (is (= _schema-ex (get-run-auth main)))

      (binding [*current-user* {:id "b" :roles [:patient]}]
        "Patient B can't continue run"
        (is (false? (check-auth main))))

      (binding [*current-user* {:id "a" :roles [:patient]}]
        "Patient A can continue run"
        (is (check-auth main _schema-ex))))))