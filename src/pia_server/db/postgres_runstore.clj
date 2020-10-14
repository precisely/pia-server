(ns pia-server.db.postgres_runstore
  (:require [longterm :refer :all]
            [korma.db :as k])
  (:import (longterm.runstore IRunStore IRun)))

(k/defdb PostgresRunStore (k/postgres {:db   "korma"
                       :user     "db"
                       :password "dbpass"}))

(extend
  PostgresRunstore
  IRunStore
  {:rs-get (fn [rs run-id])
   :rs-create! (fn [rs state])
   :rs-update! (fn [rs run])
   :rs-unsuspend! (fn [rs run-id])})