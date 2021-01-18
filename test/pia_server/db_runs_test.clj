(ns pia-server.db-runs-test
  (:refer-clojure :exclude [select update])
  (:require [clojure.test :as test]
            [pia-server.db.runs :refer :all]
            [pia-server.test :refer :all]
            [rapids.run :as r]
            hikari-cp.core
            [rapids.runstore :as rs]))

;; (test/run-tests)

(deftest ^:db write-run
  (testing "can create a run in the database"
    (let [[run, rec] (r/make-test-run :id :error)
          db-rec (with-transaction [jrs] (rs/rs-create! jrs rec))
          db-run (r/run-from-record db-rec)]
      (is (= (dissoc run :id) (dissoc db-run :id)))

      (testing "new created run should have an id"
        (is (-> run :id nil? not))
        (is (-> db-run :id nil? not)))))

  (testing "can continue a run"
    ))
