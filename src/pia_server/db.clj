(ns pia-server.db
  (:require [longterm :refer [set-runstore!]]
            [longterm.runstore :refer [IRunStore RunStates] :as rs]
            [next.jdbc :as jdbc]
            [envvar.core :refer [env]]
            hikari-cp.core
            [next.jdbc.types :refer [as-other]]
            [next.jdbc.connection :as connection])
  (:import (com.zaxxer.hikari HikariDataSource)
           (java.time LocalDateTime)))

(def datasource-options {:auto-commit        true
                         :read-only          false
                         :connection-timeout 30000
                         :validation-timeout 5000
                         :idle-timeout       600000
                         :max-lifetime       1800000
                         :minimum-idle       10
                         :maximum-pool-size  10
                         :pool-name          "db-pool"
                         :classname          "org.postgresql.Driver"
                         :dbtype             "postgresql"
                         :adapter            "postgresql"
                         :username           (get @env :db-username "pia")
                         :password           (get @env :db-password "")
                         :database-name      (get @env :db-name "pia-runstore")
                         :server-name        (get @env :db-server-name "localhost")
                         :port-number        (Integer/parseInt (get @env :db-port "5432"))
                         :register-mbeans    false})

(declare run-from-record)

(defmacro with-connection [[conn jdbc-rs] & body]
  `(let [~conn (:connection-pool ~jdbc-rs)]
     ; (with-open [^HikariDataSource ~conn @cp#]
     ~@body))

(defn is-run-state? [state] (some #(= % state) RunStates))

(defn state-to-pg-enum [state]
  {:pre [(is-run-state? state)]}
  (as-other (.getName state)))

(defrecord JDBCRunstore [connection-pool]
  IRunStore
  (rs-get [jrs run-id]
    (run-from-record
      (with-connection [conn jrs]
        (jdbc/execute-one! conn ["SELECT * FROM runs WHERE id = ?;" run-id]))))

  (rs-create! [jrs state]
    {:pre [(is-run-state? state)]}
    (with-connection [conn jrs]
      (run-from-record (jdbc/execute-one! conn ["INSERT INTO runs (state) VALUES (?) RETURNING runs.*;"
                                                (state-to-pg-enum state)]))))
  (rs-update! [jrs run]
    (with-connection [conn jrs]
      (let [updated-at (LocalDateTime/now)]
        (jdbc/execute-one!
          conn
          [(str "UPDATE runs "
                "SET state = ?, result = ?, response = ?, stack = ?, updated_at = ? "
                "WHERE id = ?; ")
           (state-to-pg-enum (:state run))
           (pr-str (:result run))
           (pr-str (:response run))
           (pr-str (:stack run))
           updated-at
           (:id run)])
        (assoc run :updated_at updated-at))))

  (rs-unsuspend! [jrs run-id]
    (with-connection [conn jrs]
      (let [updated-at (LocalDateTime/now)]
        (run-from-record
          (jdbc/execute-one!
            conn
            [(str "UPDATE runs "
                  "SET state = 'running', updated_at = ?  WHERE id = ? AND state = 'suspended'"
                  "RETURNING runs.*;")
             updated-at run-id]))))))

(defonce connection-pool (connection/->pool HikariDataSource datasource-options))
(defn make-runstore [] (JDBCRunstore. connection-pool))

(defn run-from-record [rec]
  (if rec
    (let [run (longterm.runstore/->Run
                (:runs/id rec)
                (if-let [it (:runs/stack rec)] (read-string it))
                (keyword (:runs/state rec))
                (if-let [it (:runs/result rec)] (read-string it))
                (if-let [it (:runs/response rec)] (read-string it)))]
      (assoc run
        :created_at (:runs/created_at rec)
        :updated_at (:runs/updated_at rec)))))

(defn create-db! []
  (jdbc/execute! connection-pool ["
      CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";
      DO $$ BEGIN
        CREATE TYPE RUNSTATES AS ENUM ('running', 'suspended', 'complete', 'error');
      EXCEPTION
        WHEN duplicate_object THEN null;
      END $$;
      CREATE TABLE IF NOT EXISTS runs (
      id uuid DEFAULT uuid_generate_v4(),
      state RUNSTATES,
      stack TEXT DEFAULT '()',
      result TEXT,
      response TEXT,
      error TEXT,
      error_info TEXT,
      created_at TIMESTAMP  NOT NULL  DEFAULT current_timestamp,
      updated_at TIMESTAMP  NOT NULL  DEFAULT current_timestamp,
      PRIMARY KEY (id)
      );"]))

;; HELPERS for debugging
(defn exec! [& args] (jdbc/execute! connection-pool (vec args)))

(defn uuid [] (java.util.UUID/randomUUID))
(defn delete-db! []
  (exec! "drop table if exists runs; drop type if exists Runstates;"))

