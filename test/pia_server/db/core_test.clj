(ns pia-server.db.core-test
  (:require [clojure.test :as test :refer :all]
            [envvar.core :as envvar]
            [pia-server.db.core :refer :all]))


(use-fixtures :once
  (fn [f]
    (envvar/with-env [:herokudb-env-var-pia-runstore nil,
                      :herokudb-env-var-hl7 nil,
                      :db-pia-runstore nil,
                      :db-hl7 nil,
                      :testdb-pia-runstore nil,
                      :testdb-hl7 nil]
      (f))))


(deftest jdbc-urls
  (envvar/with-env [:herokudb-env-var-pia-runstore "HEROKU_POSTGRESQL_COLOR100_URL",
                    :heroku-postgresql-color100-url "postgres://alice:alicepwd123@db.example.com:8642/pia_runstore",
                    :db-pia-runstore "jdbc:postgresql://notdb.notexample.com:1346/not_pia_runstore?user=notalice&password=notpassword"
                    :db-hl7 "jdbc:postgresql://db.example.com:9142/hl7?user=alice&password=alicepwd123"]
    (testing "Heroku indirection"
      (is (= "jdbc:postgresql://db.example.com:8642/pia_runstore?user=alice&password=alicepwd123"
             (jdbc-url :pia-runstore))))
    (testing "JDBC variables"
      (is (= "jdbc:postgresql://db.example.com:9142/hl7?user=alice&password=alicepwd123"
             (jdbc-url :hl7))))
    (testing "Development defaults 1"
      (is (= (str "jdbc:postgresql://localhost:5432/somedb?user="
                  (System/getProperty "user.name")
                  "&password=")
             (jdbc-url :somedb)))))
  (testing "Development defaults 2"
    (is (= (str "jdbc:postgresql://localhost:5432/pia_runstore?user="
                (System/getProperty "user.name")
                "&password=")
           (jdbc-url :pia-runstore)))))
