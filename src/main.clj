(ns main
    (:require [ring.adapter.jetty :as jetty]
      [envvar.core :refer [env]]
      [pia-server.app :as pia]
      [taoensso.timbre :as log]
      [pia-server.db :as db]
      [pia-server.expiry-monitor :as expiry-monitor])
    (:gen-class))

;;; TODO: Consider using the Component framework. Things which need to be
;;; managed:
;;;
;;; - application server
;;; - database connection pool

(defonce ^{:dynamic true} *server* (atom nil))

(defn start [app & {:keys [port join?]
                    :or   {port 8080, join? false}}]
      (log/info "Starting pia-server")
      (db/start-connection-pool!)
      (db/create-db!)
      (expiry-monitor/start 1000)
      (reset! *server* (jetty/run-jetty app {:port port, :join? join?})))

(defn stop []
      (when-not (nil? @*server*)
                (.stop @*server*)
                (reset! *server* nil)))

(defn -main [& args]
      (let [port (read-string (get @env :port 8080))]
           (start #'pia/app :port port, :join? true)))
