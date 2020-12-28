(ns scripts
  (:require main
            [pia-server.db-runs :as db-runs]))

(defn create-db []
  (db-runs/start-connection-pool!)
  (db-runs/migrate!))
