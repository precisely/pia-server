(ns pia-server.common.docs-test
  (:require [clojure.test :refer :all]
            [rapids :refer :all]
            [pia-server.common.docs :refer :all]
            [rapids.language.test :refer [with-test-env branch keys-match flush-cache!] :as rt]
            [rapids.active-doc :as adoc]))

(deftest retrieve-patient-doc-test
  ;(branch [patient-doc (retrieve-patient-doc :test 0)]
  ;  "Doc doesn't exist"
  ;  (is (not (nil? patient-doc))))
  (branch [type        :test
           patient-id  0
           data {:test {:foo "bar"}}
           doc         (adoc/create!
                         :data data
                         :index {:type       type
                                 :patient-id patient-id})
           patient-doc (retrieve-patient-doc type patient-id)]
    "Doc already exists"
    (is (= (:id doc) (:id patient-doc)))
    (is (= data (adoc/get-data patient-doc [:test])))
    ))