(ns pia-server.db
  (:require [longterm :refer [set-runstore!]]
            [longterm.runstore :refer [IRunStore] :as rs]
            [longterm.signals :refer [suspend-signal?]]
            [longterm.util :refer [in?]]
            [next.jdbc :as jdbc]
            [envvar.core :refer [env]]
            hikari-cp.core
            [next.jdbc.types :refer [as-other]]
            [next.jdbc.connection :as connection])
  (:import (com.zaxxer.hikari HikariDataSource)
           (java.time LocalDateTime)
           (java.util UUID)
           (longterm.runstore Run)))

(declare query-run-with-next)

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
                         :username           (get @env :db-username (System/getProperty "user.name"))
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

(defn is-run-state? [state] (some #(= % state) rs/RunStates))

(defn to-pg-enum [k]
  {:pre [(or (nil? k) (keyword? k))]}
  (if k
    (as-other (.getName k)))) ; as-other is a dynamically defined fn, so IDE may produce a warning here: ignore!

(defrecord JDBCRunstore [connection-pool]
  IRunStore
  (rs-get [jrs run-id]
    (query-run-with-next jrs run-id))

  (rs-create! [jrs state]
    {:pre [(is-run-state? state)]}
    (with-connection [conn jrs]
      (run-from-record
        (jdbc/execute-one!
          conn ["INSERT INTO runs (state) VALUES (?) RETURNING runs.*;"
                (to-pg-enum state)]))))
  (rs-update! [jrs run]
    (with-connection [conn jrs]
      (let [updated-at (LocalDateTime/now)
            run        (assoc run :updated-at updated-at)
            suspend    (:suspend run)]
        (run-from-record
          (jdbc/execute-one!
            conn
            [(str "UPDATE runs "
               "SET start_form = ?, stack = ?, state = ?, result = ?, response = ?, "
               "suspend_permit = ?, suspend_expires = ?, suspend_default = ?, "
               "return_mode = ?, parent_run_id = ?, next_id = ?, "
               "error = ?, "
               "updated_at = ? "
               "WHERE id = ? "
               "RETURNING runs.*;")
             (pr-str (:start-form run))
             (pr-str (:stack run))
             (to-pg-enum (:state run))
             (pr-str (:result run))
             (pr-str (:response run))
             (pr-str (:permit suspend))
             (:expires suspend)
             (pr-str (:default suspend))
             (to-pg-enum (:return-mode run))
             (:parent-run-id run)
             (:next-id run)
             (pr-str (:error run))
             updated-at
             (:id run)])))))

  (rs-acquire! [jrs run-id permit]
    (println "Attempting to acquire run " run-id "(permit " permit ")")
    (with-connection [conn jrs]
      (let [updated-at (LocalDateTime/now)]
        (run-from-record
          (jdbc/execute-one!
            conn
            [(str "UPDATE runs "
               "SET state = 'running', updated_at = ?  WHERE id = ? and suspend_permit = ? AND state = 'suspended'"
               "RETURNING runs.*;")
             updated-at, run-id, (str permit)]))))))

(defn query-run-with-next [jrs run-id]
  (let [runs (map run-from-record
               (with-connection [conn jrs]
                 (jdbc/execute!
                   conn [(str
                           "SELECT root.* "
                           "FROM runs root "
                           "LEFT JOIN runs next ON next.id = root.next_id "
                           "WHERE root.id = ? "
                           "ORDER BY root.id = ? DESC;")
                         run-id])))]
    (case (count runs)
      0 nil
      1 (first runs)
      2 (let [run (first runs)]
          (assert (-> run :id (= run-id)))
          (assert (-> (second runs) :id (not= run-id)))
          (assoc run :next (second run)))
      (throw (Exception. "query-run-with-next sanity check failed")))))

(defonce connection-pool (connection/->pool HikariDataSource datasource-options))
(defn make-runstore [] (JDBCRunstore. connection-pool))

(defn read-field [rec field]
  (if-let [val (field rec)] (read-string val)))

(defn or-nil? [o p]
  (or (nil? o) (p o)))

(defn run-from-record
  "Returns a Clojure Run record from a database record"
  [db-rec]
  {:post [(-> % :id uuid?)
          (-> % :start-form (or-nil? list?))
          (-> % :state keyword?)
          (-> % :response vector?)
          (-> % :suspend suspend-signal?)
          (in? rs/ReturnModes (-> % :return-mode))
          (-> % :parent-run-id (or-nil? uuid?))
          (-> % :response vector?)
          (-> % :error (or-nil? #(instance? Exception %)))
          (-> % :next (or-nil? #(rs/run-in-state? % :any)))
          (-> % :next-id (or-nil? uuid?))]}
  (if db-rec
    (let [run (longterm.runstore/map->Run
                {:id            (:runs/id db-rec)
                 :start-form    (read-field :runs/start-form db-rec)
                 :stack         (read-field :runs/stack db-rec)
                 :state         (keyword (str (:runs/state db-rec)))
                 :result        (read-field :runs/result db-rec)
                 :response      (or (read-field :runs/response db-rec) [])
                 :suspend       (longterm.signals/make-suspend-signal
                                  (read-field :runs/suspend-permit db-rec)
                                  (:runs/suspend-expires db-rec)
                                  (read-field :runs/suspend-default db-rec))
                 :return-mode   (read-field :runs/return-mode db-rec)
                 :parent-run-id (:runs/next-id db-rec)
                 :run-response  (or (read-field :runs/run-response db-rec) [])
                 :error         (read-field :runs/error db-rec)
                 :next          (:next db-rec)
                 :next-id       (or (:runs/next-id db-rec) (:runs/id db-rec))})]
      (assoc run
        :created_at (:runs/created_at db-rec)
        :updated_at (:runs/updated_at db-rec)))))

(defn create-db! []
  (jdbc/execute! connection-pool ["
      CREATE EXTENSION IF NOT EXISTS \"uuid-ossp\";
      DO $$ BEGIN
        CREATE TYPE RUN_STATES AS ENUM ('running', 'suspended', 'complete', 'error');
      EXCEPTION
        WHEN duplicate_object THEN null;
      END $$;
      DO $$ BEGIN
      CREATE TYPE RETURN_MODES AS ENUM ('block', 'redirect');
      EXCEPTION
      WHEN duplicate_object THEN null;
      END $$;
      CREATE TABLE IF NOT EXISTS runs (
      id UUID DEFAULT uuid_generate_v4(),
      start_form TEXT,
      state RUN_STATES,
      stack TEXT DEFAULT '()',
      result TEXT,
      response TEXT,
      suspend_permit TEXT,
      suspend_expires TIMESTAMP,
      suspend_default TEXT,
      return_mode RETURN_MODES,
      parent_run_id UUID,
      full_response TEXT,
      next_id UUID,
      error TEXT,
      created_at TIMESTAMP  NOT NULL  DEFAULT current_timestamp,
      updated_at TIMESTAMP  NOT NULL  DEFAULT current_timestamp,
      PRIMARY KEY (id)
      );"])
  (println "Database created"))

;; HELPERS for debugging
(defn exec! [& args] (jdbc/execute! connection-pool (vec args)))

(defn uuid [] (UUID/randomUUID))
(defn delete-db! []
  (if (-> datasource-options :server-name (= "localhost"))
    (exec! "drop table if exists runs; drop type if exists Runstates;")
    (println "Refusing to dropdatabase when datasource-options :server-name is not localhost")))

