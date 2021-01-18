(ns pia-server.test
  (:require [clojure.test :as test]
            [pia-server.db.core :as db]
            [pia-server.db.runs :as db-runs]
            [pia-server.db.hl7 :as db-hl7]
            [potemkin :refer [import-vars]]
            [rapids :refer [with-runstore]]
            [envvar.core :as envvar :refer [env keywordize]]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [taoensso.timbre :as log])
  (:import (com.zaxxer.hikari HikariDataSource)))

(import-vars [clojure.test deftest is testing])


(defonce test-db-runs-connection-pool
  (let [options (assoc db-runs/datasource-options
                       :jdbcUrl (get @env :testdb-runstore
                                     (str "jdbc:postgresql://localhost:5432/test_pia_runstore"
                                          "?user=" (System/getProperty "user.name")
                                          "&password=")))]
    (connection/->pool HikariDataSource options)))

(defonce test-db-hl7-connection-pool
  (let [options (assoc db-hl7/datasource-options
                       :jdbcUrl (get @env :testdb-hl7
                                     (str "jdbc:postgresql://localhost:5432/test_hl7"
                                          "?user=" (System/getProperty "user.name")
                                          "&password=")))]
    (connection/->pool HikariDataSource options)))

;; No connection pooling really necessary for test runs.
(defonce ^:dynamic *cxn-runs* (jdbc/get-connection test-db-runs-connection-pool))
(defonce ^:dynamic *cxn-hl7* (jdbc/get-connection test-db-hl7-connection-pool))


(defn fixture-test-db [f]
  (log/with-level :warn
    (binding [db-runs/*connection-pool* test-db-runs-connection-pool
              db-hl7/*connection-pool* test-db-hl7-connection-pool]
      (with-runstore [(db-runs/make-runstore *cxn-runs*)]
        (db-runs/migrate!)
        (db-hl7/migrate!)
        (f)))))

(defn fixture-reset [f]
  (f)
  (jdbc/execute! *cxn-runs* ["truncate table runs;"])
  ;; TODO: Add *cxn-hl7* truncates here as needed:
  ;; ...
  )

(defn use-test-db []
  (test/use-fixtures :once fixture-test-db)
  (test/use-fixtures :each fixture-reset))

(defmacro deftest [name & body]
  `(do
     (use-test-db)
     (test/deftest ~name ~@body)))

(alter-meta! #'deftest merge (meta #'test/deftest))
