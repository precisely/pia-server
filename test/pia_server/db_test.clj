(ns pia-server.db-test
  (:refer-clojure :exclude [select update])
  (:require [pia-server.db :refer :all]
            [clojure.test :refer :all]
            [clojure.core :as clj]
            [rapids.run :as r]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            hikari-cp.core
            [honeysql.core :as sql]
            [honeysql.helpers :refer :all]
            [rapids.runstore :as rs]
            [pia-server.db :as db])
  (:import (com.zaxxer.hikari HikariDataSource)
           (java.util.logging Logger Level)))

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

(defonce test-connection-pool
  (let [options (reduce #(clj/update %1 (first %2) (second %2))
                  datasource-options
                  [[:dbname #(str "test_" %)]
                   [:maximum-pool-size (constantly 2)]
                   [:pool-name #(str "test_" %)]])]
    (connection/->pool HikariDataSource options)))

(defmacro with-test-db [& body]
  `(binding [*connection-pool* test-connection-pool]
     (let [connection# (jdbc/get-connection test-connection-pool)]
       (rapids/with-runstore [(make-runstore connection#)]
         (db/create-db!)
         ~@body
         (db/delete-db!)))))

(deftest ^:db write-run
  (testing "can create a run to the database"
    (with-test-db
      (let [[run, rec] (r/make-test-run :id :error)
            db-rec (with-transaction [jrs]
                     (rs/rs-create! jrs rec))
            db-run (r/run-from-record db-rec)]
        (is (= (dissoc run :id) (dissoc db-run :id)))

        (testing "new created run should have an id"
          (is (-> run :id nil?))
          (is (-> db-run :id nil? not))))))

  (testing "can continue a run"
    ))
