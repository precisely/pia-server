(ns scripts
  (:require main
            [pia-server.db :as db]))

(defn create-db []
  (db/start-connection-pool!)
  (db/migrate!))
