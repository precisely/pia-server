(ns pia-server.db.core
  (:require [korma.core :refer :all]
            [korma.db :refer [defdb]]
            [longterm :refer [set-runstore!]])
  (:import (longterm.runstore IRunStore IRun)))

(defrecord KormaRunStore [korma-db])
(extend KormaRunStore
  IRunStore
  {:rs-get (fn [krs run-id] (select))
   :rs-create! (fn [krs state])
   :rs-update! (fn [krs run])
   :rs-unsuspend! (fn [krs run-id])})

(defrecord KormaRun [run-entity])
(extend KormaRun
  IRun
  {:id (fn [kr] (:id kr))
   :state (fn [kr] (:state kr))
   :result (fn [kr] (:result kr))})
(defentity Run
  (pk :id)
  ;; it's used for relationships joins.
  (table :users) ;; by default the name of the symbol.
  ;; The line above is also unecessary.
  (database db) ;; if none is specified the last defdb
  ;; will be used. Also unnecessary.
  (entity-fields :id :state :response :result :stack :error :err-info :created_on :updated_on) ; default select



  ())

(extend
  DBConnection

(defn create-tables
  "Create a factoid table"
  [db]
  (sql/db-do-commands
    db
    (sql/create-table-ddl
      :run
      [[:id          "UUID" "IDENTITY" "NOT NULL" "PRIMARY KEY" "uuid_generate_v4()"]
       [:state       "ENUM" "('running','suspended','complete','error')"]
       [:response    "TEXT"]
       [:result      "TEXT"]
       [:stack       "TEXT"]
       [:error       "TEXT"] ; error message reportable to API consumer
       [:err-info    "TEXT"] ; detailed debugging info
       [:created_on  "TIMESTAMP" "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]
       [:updated_on  "TIMESTAMP" "NOT NULL" "DEFAULT CURRENT_TIMESTAMP"]])))
