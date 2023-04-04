(ns pia-server.server.core
  (:require [pia-server.server.handler :as pia]

            [rapids.implementations.postgres-storage :as rapids-pg]
            rapids

            [ring.adapter.jetty :as jetty]
            [taoensso.timbre :as log]))

;;; TODO: Consider using the Component framework. Things which need to be
;;; managed:
;;;
;;; - application server
;;; - database connection pool
;;; - expiry monitor

(defonce ^:dynamic *server* (atom nil))

(defn start
  ([& {:keys [port join? expiry-seconds level server]
       :or   {server         #'pia/wrapped-handler,
              port           8080,
              join?          false,
              expiry-seconds 60,
              level          :info}}]
   (log/set-level! [[#{"com.zaxxer.hikari.pool"} ::error]
                    [#{"org.eclipse.jetty", "com.zaxxer.hikari"} :warn]
                    [#{"*"} level]])
   (log/info (str "Starting pia-server at http://localhost:" port))
   (rapids-pg/postgres-storage-migrate!)
   (log/info (str "Starting expiry monitor with timeout of " expiry-seconds " seconds"))
   (rapids/start-expiry-monitor! :delay expiry-seconds)
   (reset! *server* (jetty/run-jetty server {:port port, :join? join?, :async? true}))))

(defn stop []
  (when-not (nil? @*server*)
    (.interrupt @*server*)
    (reset! *server* nil))
  (rapids/stop-expiry-monitor!))
