(ns main
  (:require [ring.adapter.jetty :as jetty]
            [envvar.core :refer [env]]
            [pia-server.app :as pia]
            [taoensso.timbre :as log]
            [pia-server.db.runs :as db-runs]
            [pia-server.db.hl7 :as db-hl7]
            [pia-server.expiry-monitor :as expiry-monitor])
  (:gen-class))

;;; TODO: Consider using the Component framework. Things which need to be
;;; managed:
;;;
;;; - application server
;;; - database connection pool

(defonce ^{:dynamic true} *server* (atom nil))

(defn start
  ([] (start #'pia/app))

  ([app & {:keys [port join? expiry-seconds]
          :or   {port 8080, join? false, expiry-seconds 10}}]
  (log/info (str "Starting pia-server at http://localhost:" port))
  (db-runs/migrate!)
  (db-hl7/migrate!)
  (log/info (str "Starting expiry monitor with timeout of " expiry-seconds " seconds"))
  (expiry-monitor/start expiry-seconds)
  (reset! *server* (jetty/run-jetty app {:port port, :join? join?}))))

(defn stop []
  (when-not (nil? @*server*)
    (.stop @*server*)
    (reset! *server* nil))
  (expiry-monitor/stop))

(defn -main [& args]
  (let [port (read-string (get @env :port 8080))]
    (start #'pia/app :port port, :join? true)))
