(ns main
  (:require [ring.adapter.jetty :as jetty]
            [clojure.java.io :as io]
            [clojure.core.async :as async]
            [envvar.core :refer [env]]
            [pia-server.app :as pia]
            [pia-server.db.core :refer [jdbc-url]]
            [taoensso.timbre :as log]
            [rapids.implementations.postgres-storage :as rapids-pg]
            [rapids :refer :all :as rapids])
  ;[pia-server.db.runs :as db-runs]
  ;[pia-server.db.hl7 :as db-hl7]
  (:gen-class))

;;; Bootstrap core.async SSE support:
(extend-type clojure.core.async.impl.channels.ManyToManyChannel
  ring.core.protocols/StreamableResponseBody
  (write-body-to-stream [channel response output-stream]
    (let [writer (io/writer output-stream)]
      (async/go
        (try
          (loop []
            (when-let [value (async/<! channel)]
              (doto writer (.write value) (.flush))
              (recur)))
          (catch java.io.IOException ex
            (log/debug ex))
          (finally
            (async/close! channel)
            (.close output-stream)))))))

;;; TODO: Consider using the Component framework. Things which need to be
;;; managed:
;;;
;;; - application server
;;; - database connection pool

(rapids/set-storage! (rapids-pg/->postgres-storage {:jdbcUrl (jdbc-url :rapids-storage)}))

(defonce ^:dynamic *server* (atom nil))

(defn start
  ([] (start #'pia/app))
  ([app & {:keys [port join? expiry-seconds level]
           :or   {port 8080, join? false, expiry-seconds 10, level :info}}]
   (log/set-level! level)
   (log/info (str "Starting pia-server at http://localhost:" port))
   (rapids-pg/postgres-storage-migrate!)
   (log/info (str "Starting expiry monitor with timeout of " expiry-seconds " seconds"))
   (start-expiry-monitor! :delay expiry-seconds)
   (reset! *server* (jetty/run-jetty app {:port port, :join? join?, :async? true}))))

(defn stop []
  (when-not (nil? @*server*)
    (.stop @*server*)
    (reset! *server* nil))
  (stop-expiry-monitor!))

(defn -main [& args]
  (let [port (read-string (get @env :port 8080))]
    (start #'pia/app :port port, :join? true)))
