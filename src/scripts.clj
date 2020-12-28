(ns scripts
  (:require main
            [pia-server.db-runs :as db-runs]
            [pia-server.db-hl7 :as db-hl7]))

(defn create-db []
  (db-runs/start-connection-pool!)
  (db-runs/migrate!)
  (db-hl7/start-connection-pool!)
  (db-hl7/migrate!))
