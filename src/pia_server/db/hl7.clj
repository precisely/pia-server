(ns pia-server.db.hl7
  (:refer-clojure :exclude [select update])
  (:require [clojure.core :as clj]
            [clojure.string :as str]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [pia-server.db.core :as db]
            [taoensso.timbre :as log])
  (:import (com.zaxxer.hikari HikariDataSource)))

(def datasource-options
  {:auto-commit        false
   :read-only          false
   :connection-timeout 30000
   :validation-timeout 5000
   :idle-timeout       600000
   :max-lifetime       1800000
   :minimum-idle       10
   :maximum-pool-size  10
   :pool-name          "db-hl7-pool"
   :classname          "org.postgresql.Driver"
   :jdbcUrl            (db/jdbc-url :hl7)
   :register-mbeans    false})

;; reset: (alter-var-root #'*connection-pool* (fn [_] (connection/->pool HikariDataSource datasource-options)))
(defonce ^:dynamic *connection-pool*
  (connection/->pool HikariDataSource datasource-options))

(def migration-conf
  {:store :database
   :migration-dir "migrations/hl7"
   :migration-table-name "schema_migrations_hl7"})

(defn migrate! []
  (with-open [c (jdbc/get-connection *connection-pool*)]
    (migratus/migrate (assoc migration-conf :db {:connection c})))
  (log/info "HL7 database migrated"))

(defn execute-one! [stmt params]
  (with-open [c (jdbc/get-connection *connection-pool*)
              ps (jdbc/prepare c [stmt params])]
    (jdbc/execute-one! ps)))
