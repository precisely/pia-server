
(ns pia-server.db.create
  (:use [korma.db])
  (:use [korma.core])
  (:require [clojure.java.jdbc :as sql]))

(def dbspec  {:classname   "org.h2.Driver"
              :subprotocol "h2"
              :subname "~/db/myapp"
              :user     "sa"
              :password ""})

(defdb mydb dbspec)
(defentity factoid)




(defn invoke-with-connection [f]
  (sql/with-connection
    mydb
    (sql/transaction
      (f))))

;; .
;; .
;; .
(invoke-with-connection create-tables)