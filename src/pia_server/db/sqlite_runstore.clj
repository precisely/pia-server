(ns pia-server.db.sqlite-runstore
  (:require [longterm :refer :all]
            [korma.db :as k]
            [clojure.java.io :as io]))

(k/defdb sqlitedb (k/sqlite3 {:db "sqlite.db"}))
