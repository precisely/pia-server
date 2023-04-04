(ns pia-server.db.component
  (:require [com.stuartsierra.component :as component]
            [rapids.implementations.postgres-storage :as rapids-pg]
            [pia-server.db.core :refer [jdbc-url]]))

(defrecord Database [storage]
  component/Lifecycle
  (start [this]
    (rapids-pg/postgres-storage-migrate!)
    (assoc this :storage storage))
  (stop [this]
    (when-not (nil? (:db this))
      (.close (:db this)))))

(defn ->database
  ([] (->database (rapids-pg/->postgres-storage {:jdbcUrl (jdbc-url :pia-storage)})))
  ([storage] (component/using (Database. storage) [])))