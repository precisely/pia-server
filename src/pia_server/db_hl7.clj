(ns pia-server.db-hl7
  (:refer-clojure :exclude [select update])
  (:require [clojure.core :as clj]
            [clojure.string :as str]
            [envvar.core :refer [env keywordize]]
            [migratus.core :as migratus]
            [next.jdbc :as jdbc]
            [next.jdbc.connection :as connection]
            [pia-server.util :as util]
            [taoensso.timbre :as log])
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
   :pool-name          "db-hl7-pool"
   :classname          "org.postgresql.Driver"
   :jdbcUrl            (util/heroku-db-url->jdbc-url
                        (get @env
                             ;; resolve Heroku indirection
                             (-> @env
                                 (get :db-env-var-hl7)
                                 keywordize)
                             ;; default if no vars are set
                             (str "postgres://"
                                  (System/getProperty "user.name")
                                  "@localhost:5432/hl7")))
   :register-mbeans    false})

(def ^:dynamic *connection-pool*)

(defn start-connection-pool! []
  (if (bound? #'*connection-pool*)
      *connection-pool*
      (alter-var-root #'*connection-pool*
                      (fn [_] (connection/->pool HikariDataSource datasource-options)))))

(defn migration-conf []
  {:store :database
   :migration-dir "migrations/hl7"
   :migration-table-name "schema_migrations_hl7"
   :db {:connection (jdbc/get-connection *connection-pool*)}})

(defn migrate! []
  (migratus/migrate (migration-conf))
  (log/info "HL7 database migrated"))
