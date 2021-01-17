(ns pia-server.db.runs
  (:refer-clojure :exclude [select update])
  (:require [clojure.core :as clj]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [rapids :refer [set-runstore!]]
            [rapids.runstore :refer [IRunStore] :as rs]
            [rapids.run :as r]
            [rapids.signals :refer [suspend-signal?]]
            [rapids.util :refer [in?]]
            [envvar.core :refer [env keywordize]]
            [next.jdbc :as jdbc]
            [next.jdbc.types :refer [as-other]]
            [next.jdbc.connection :as connection]
            hikari-cp.core
            [honeysql.core :as sql]
            [honeysql.helpers :refer :all]
            [honeysql.format :as fmt]
            [rapids :as lt]
            [clojure.spec.alpha :as s]
            [migratus.core :as migratus]
            [pia-server.util :as util])
  (:import (com.zaxxer.hikari HikariDataSource)
           (java.util UUID)))

(def datasource-options
  {:auto-commit        false
   :read-only          false
   :connection-timeout 30000
   :validation-timeout 5000
   :idle-timeout       600000
   :max-lifetime       1800000
   :minimum-idle       10
   :maximum-pool-size  10
   :pool-name          "db-runs-pool"
   :classname          "org.postgresql.Driver"
   :jdbcUrl            (util/heroku-db-url->jdbc-url
                        (get @env
                             ;; resolve Heroku indirection
                             (-> @env
                                 (get :db-env-var-runstore)
                                 keywordize)
                             ;; default if no vars are set
                             (str "postgres://"
                                  (System/getProperty "user.name")
                                  "@localhost:5432/pia_runstore")))
   :register-mbeans    false})

(def ^:dynamic *connection-pool*)

(defn start-connection-pool! []
  (if (bound? #'*connection-pool*)
      *connection-pool*
      (alter-var-root #'*connection-pool*
                      (fn [_] (connection/->pool HikariDataSource datasource-options)))))

(def migration-conf
  {:store :database
   :migration-dir "migrations/runstore"
   :migration-table-name "schema_migrations_runstore"})

(defn migrate! []
  (with-open [c (jdbc/get-connection *connection-pool*)]
    (migratus/migrate (assoc migration-conf :db {:connection c})))
  (log/info "Runstore database migrated"))

(declare query-run-with-next to-db-record from-db-record)

(declare from-db-record make-runstore)

(defmacro with-transaction
  "jrs will be bound to a JDBCRunstore object"
  [[jrs] & body]
  `(with-open [connection# (jdbc/get-connection *connection-pool*)]
     (let [~jrs (make-runstore connection#)]
       (rs/with-transaction [~jrs]
                            ~@body))))

(defn is-run-state? [state] (some #(= % state) r/RunStates))

(defn exec! [jrs stmt]
  (jdbc/execute! (:connection jrs) stmt))

(defn exec-one! [jrs stmt]
  (jdbc/execute-one! (:connection jrs) stmt))

(defhelper returning [m returns]
  (assoc m :returning returns))
(defmethod fmt/format-clause :returning [[op v] sqlmap]
  (str "RETURNING "
       (if (seqable? v)
         (str/join ", " (map fmt/to-sql v))
         (fmt/to-sql v))))

(defrecord JDBCRunstore [connection]
  IRunStore
  (rs-get [jrs run-id]
    (from-db-record (query-run-with-next jrs run-id)))

  (rs-create! [jrs record]
    ; {:pre [(is-run-state? state)]}
    (log/debug "Creating run in state" record)
    (let [stmt (-> (insert-into :runs)
                   (values [(to-db-record record)])
                   (returning :runs.*)
                   sql/format)]
      (from-db-record
        (exec-one! jrs stmt))))

  (rs-update! [jrs record expires]
    (log/debug "Updating run " record)
    (let [updated-at (lt/now)
          record     (to-db-record (assoc record :updated_at updated-at :suspend_expires expires))]
      (from-db-record
        (exec-one! jrs
                   (-> {}
                       (update :runs)
                       (sset (dissoc record :id))
                       (where [:= :id (:id record)])
                       (returning :runs.*)
                       sql/format)))))

  (rs-lock! [jrs run-id]
    (log/debug "Locking run " run-id)
    (let [stmt (->
                 (select :*)
                 (from :runs)
                 (lock :mode :update)
                 (where [:= :id run-id])
                 sql/format)]
      (from-db-record
        (exec-one! jrs stmt))))

  (rs-tx-begin! [jrs]
    (log/trace "Begin transaction")
    (exec-one! jrs ["BEGIN;"]))

  (rs-tx-commit! [jrs]
    (log/trace "Commit transaction")
    (exec-one! jrs ["COMMIT;"]))

  (rs-tx-rollback! [jrs]
    (log/trace "Rollback transaction")
    (exec-one! jrs ["ROLLBACK;"])))

(defn make-runstore [connection] (JDBCRunstore. connection))
(defn query-run-with-next [jrs run-id]
  (let [runs (map from-db-record

                  (exec! jrs
                         ;; need to use sql/call for ORDER-BY with expression for now:
                         ;; https://github.com/seancorfield/honeysql/issues/285
                         ;; also, ignore the linter errors in the following:
                         (-> (select :*)
                             (from [:runs :root])
                             (left-join [:runs :next] [:= :next.id :root.next_id])
                             (where [:= :root.id run-id])
                             (order-by [(sql/call := :root.id run-id) :desc])
                             sql/format)))]
    (case (count runs)
      0 nil
      1 (first runs)
      2 (let [run (first runs)]
          (assert (-> run :id (= run-id)))
          (assert (-> (second runs) :id (not= run-id)))
          (assoc run :next (second run)))
      (throw (Exception. "query-run-with-next sanity check failed")))))

(defn or-nil? [o p]
  (or (nil? o) (p o)))

(defn from-db-record
  "Removes the namespace from keys in the record returned by jdbc-next"
  [record]
  (into {} (map #(vector (keyword (name (first %))), (second %)) record)))

(defn to-db-record [record]
  "Properly encodes enum column values"
  (reduce #(clj/update %1 %2 as-other) record [:state :return_mode]))

(defn get-expired-run-ids
  ([jrs] (get-expired-run-ids jrs (lt/now)))
  ([jrs now]
   {:post [(s/assert (s/coll-of uuid?) %)]}
   (map :runs/id
        (exec! jrs
               (-> (select :id)
                   (from :runs)
                   (where [:< :suspend_expires now])
                   sql/format)))))

;; HELPERS for debugging
(defn uuid [] (UUID/randomUUID))
(defmacro log-errors [& body]
  `(try ~@body
        (catch Exception e#
          (log/error "While " '~body ":" e#))))

;; ???
(defn simple-test []
  (let [run     (r/make-test-run)
        run-rec (dissoc (r/run-to-record run) :result :error :state :stack :suspend :response :return_mode)
        stmt    (-> (insert-into :runs)
                    (values run-rec)
                    (returning [:runs.*])
                    sql/format)]
    (prn stmt)
    (with-open [conn (jdbc/get-connection *connection-pool*)]
      (from-db-record
        (jdbc/execute-one! conn stmt)))))
