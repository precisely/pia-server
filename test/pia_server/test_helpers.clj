(ns pia-server.test-helpers
  (:require [pia-server.db-runs :refer [migrate! *connection-pool* make-runstore datasource-options]]
            [rapids :refer [with-runstore]]
            [rapids.runstore :refer [tx-begin! tx-rollback!]]
            [next.jdbc.connection :as connection])
  (:import (com.zaxxer.hikari HikariDataSource)))

(defonce test-connection-pool
         (let [options (reduce #(update %1 (first %2) (second %2))
                  datasource-options
                  [[:dbname #(str "test_" %)]
                   [:maximum-pool-size (constantly 2)]
                   [:pool-name #(str "test_" %)]])]
           (connection/->pool HikariDataSource options)))

(defmacro with-test-db [& body]
  `(binding [*connection-pool* test-connection-pool]
     (let [connection# *connection-pool* #_ (jdbc/get-connection test-connection-pool)]
       (with-runstore [(make-runstore connection#)]
         (migrate!)
         (try
           (tx-begin!)
            ~@body
           (tx-rollback!))))))