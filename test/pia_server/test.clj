(ns pia-server.test
  (:require [pia-server.db-runs :refer [migrate! *connection-pool* datasource-options make-runstore]]
            [clojure.test :as test]
            [potemkin :refer [import-vars]]
            [rapids :refer [with-runstore]]
            [envvar.core :refer [env keywordize]]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [pia-server.util :as util]
            [taoensso.timbre :as log])
  (:import (com.zaxxer.hikari HikariDataSource)))

(import-vars [clojure.test deftest is testing])

(defn jdbc-url []
  (let [indirect-var (if (@env :db-env-var-test-runstore)
                       :db-env-var-test-runstore
                       :db-env-var-runstore)
        real-var     (-> @env
                         (get indirect-var)
                         keywordize)
        db-val       (get @env
                          real-var
                          ;; default if no vars are set
                          (str "postgres://"
                               (System/getProperty "user.name")
                               "@localhost:5432/test_pia_runstore"))]
    (util/heroku-db-url->jdbc-url db-val)))

(defonce test-connection-pool
         (let [options (assoc datasource-options :jdbcUrl (jdbc-url))]
           (connection/->pool HikariDataSource options)))

;; No connection pooling really necessary for test runs.
(defonce ^:dynamic *connection* (jdbc/get-connection test-connection-pool))

(defn fixture-test-db [f]
  (log/with-level :warn
    (binding [*connection-pool* test-connection-pool]
      (with-runstore [(make-runstore *connection*)]
        (migrate!)
        (f)))))

(defn fixture-reset [f]
  (f)
  (jdbc/execute! *connection* ["truncate table runs;"]))

(defn use-test-db []
  (test/use-fixtures :once fixture-test-db)
  (test/use-fixtures :each fixture-reset))

(defmacro deftest [name & body]
  `(do
     (use-test-db)
     (test/deftest ~name ~@body)))

(alter-meta! #'deftest merge (meta #'test/deftest))