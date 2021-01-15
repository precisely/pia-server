(ns pia-server.db-runs-test
  (:refer-clojure :exclude [select update])
  (:require [pia-server.db-runs :refer :all]
            [clojure.test :refer :all]
            [clojure.core :as clj]
            [rapids.run :as r]
            [envvar.core :refer [env keywordize]]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            hikari-cp.core
            [honeysql.core :as sql]
            [honeysql.helpers :refer :all]
            [rapids.runstore :as rs]
    ;; FIXME: :refer :all above and db here
            [pia-server.db-runs :as db]
            [pia-server.util :as util])
  (:import (com.zaxxer.hikari HikariDataSource)))

;(def log-level-map
;  {:off     Level/OFF,
;   :severe  Level/SEVERE,
;   :warning Level/WARNING,
;   :info    Level/INFO,
;   :config  Level/CONFIG,
;   :fine    Level/FINE,
;   :finer   Level/FINER,
;   :finest  Level/FINEST,
;   :all     Level/ALL})
;
;(defn set-logger-level [logger-name level]
;  (.setLevel (Logger/getLogger logger-name) (or (log-level-map level) level)))
;
;(defn get-logger-level [logger-name]
;  (.getLevel (Logger/getLogger logger-name)))
;
;(defmacro with-loggers-at-level [[loggers level] & body]
;  `(let [loggers#    ~loggers
;         pre-levels# (map get-logger-level ~loggers)]
;     (doseq [logger# ~loggers] (set-logger-level logger# ~level))
;     (try
;       ~@body
;       (finally
;         (prn (interleave loggers# pre-levels#))
;         #_(doseq [[logger# level#] (interleave loggers# pre-levels#)]
;             (if level# (set-logger-level logger# level#)))))))

(defn jdbc-url []
  (let [indirect-var (if (@env :db-env-var-test-runstore)
                         :db-env-var-test-runstore
                         :db-env-var-runstore)
        real-var (-> @env
                     (get indirect-var)
                     keywordize)
        db-val (get @env
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
  (binding [*connection-pool* test-connection-pool]
    (rapids/with-runstore [(make-runstore *connection*)]
      (db/migrate!)
      (f))))

(defn fixture-reset [f]
  (f)
  (jdbc/execute! *connection* ["truncate table runs;"]))

(use-fixtures :once fixture-test-db)
(use-fixtures :each fixture-reset)

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
