(ns pia-server.db.core
  (:require [longterm :refer [set-runstore!]]
            [longterm.runstore :refer [IRunStore]]
            [next.jdbc :as jdbc]
            [environ.core :refer [env]]
            [hikari-cp.core :as hk]
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
                         ;:dbtype             "postgresql"
                         ;:adapter            "postgresql"
                         :username           (env :db-username)
                         :password           (env :db-password)
                         :database-name      (env :db-name)
                         :server-name        (env :db-server-name)
                         :port-number        (Integer/parseInt (env :db-port "5432"))
                         :register-mbeans    false})

(def connection-pool (delay (connection/->pool HikariDataSource
                                               datasource-options)))

(declare run-from-record)

(defmacro with-connection [[conn jdbc-rs] & body]
  `(let [cp# (:connection-pool ~jdbc-rs)]
     (with-open [^HikariDataSource ~conn @cp#]
       ~@body)))

(defrecord JDBCRunstore [connection-pool]
  IRunStore
  (rs-get [jrs run-id]
    (with-connection [conn jrs]
      (jdbc/execute-one! conn ["SELECT * FROM runs WHERE id = ?;" run-id])))

  (rs-create! [jrs state]
    (with-connection [conn jrs]
      (run-from-record (jdbc/execute-one! conn ["INSERT INTO runs (state, stack) VALUES (?, ?) RETURNING runs.*;"
                                                (.getName state) "[]"]))))
  (rs-update! [jrs run]
    (with-connection [conn jrs]
      (let [updated-at (LocalDateTime/now)]
        (jdbc/execute-one!
          conn
          [(str "UPDATE runs "
                "SET status = ?, result = ?, response = ?, stack = ?, updated_at = ? "
                "WHERE id = ?; ")
           (.getName (:state run))
           (prn-str (:result run))
           (prn-str (:response run))
           (prn-str (:stack run))
           updated-at
           (:id run)])
        run)))

  (rs-unsuspend! [jrs run-id]
    (with-connection [conn jrs]
      (let [updated-at (LocalDateTime/now)]
        (run-from-record
          (jdbc/execute-one!
            conn
            [(str "UPDATE runs "
                  "SET status = 'running', updated_at = ?  WHERE id = ? AND STATUS = 'suspended'"
                  "RETURNING runs.*;")
             updated-at run-id]))))))

(set-runstore! (JDBCRunstore. connection-pool))

(defn run-from-record [rec]
  (assoc (longterm.runstore/->Run
           (:id rec)
           (read (:stack rec))
           (keyword (:state rec))
           (read (:result rec))
           (read (:response rec)))
    :created_at (:created_at rec)
    :updated_at (:updated_at rec)))

;(extend JDBCRunstore
;
;  {:rs-get        (fn [jrs run-id]
;                    (with-connection [conn jrs]
;                      (jdbc/execute-one! conn ["SELECT * FROM runs WHERE id = ?;" run-id])))
;
;   :rs-create!    (fn [jrs state]
;                    (with-connection [conn jrs]
;                      (run-from-record (jdbc/execute-one! conn ["INSERT INTO runs (state, stack) VALUES (?, ?) RETURNING runs.*;"
;                                                                (.getName state) "[]"]))))
;   :rs-update!    (fn [jrs run]
;                    (with-connection [conn jrs]
;                      (let [updated-at (LocalDateTime/now)]
;                        (jdbc/execute-one!
;                          conn
;                          [(str "UPDATE runs "
;                                "SET status = ?, result = ?, response = ?, stack = ?, updated_at = ? "
;                                "WHERE id = ?; ")
;                           (.getName (:state run))
;                           (prn-str (:result run))
;                           (prn-str (:response run))
;                           (prn-str (:stack run))
;                           updated-at
;                           (:id run)])
;                        run)))
;
;   :rs-unsuspend! (fn [jrs run-id]
;                    (with-connection [conn jrs]
;                      (let [updated-at (LocalDateTime/now)]
;                        (run-from-record
;                          (jdbc/execute-one!
;                            conn
;                            [(str "UPDATE runs "
;                                  "SET status = 'running', updated_at = ?  WHERE id = ? AND STATUS = 'suspended'"
;                                  "RETURNING runs.*;")
;                             updated-at run-id])))))})

(defn create-db! []
  (with-open [^HikariDataSource connection @connection-pool]
    (jdbc/execute! connection ["
      CREATE TABLE IF NOT EXISTS runs (
      id UUID PRIMARY KEY,
      state ENUM('running', 'suspended', 'complete', 'error') DEFAULT 'suspended',
      stack TEXT DEFAULT '[]',
      result TEXT,
      response TEXT,
      error TEXT,
      error_info TEXT,
      created_at TIMESTAMP  NOT NULL  DEFAULT current_timestamp,
      updated_at TIMESTAMP  NOT NULL  DEFAULT current_timestamp
      );"])))
