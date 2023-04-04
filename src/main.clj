(ns main
  (:require [pia-server.server.core :as pia]
            [pia-server.db.core :refer [jdbc-url]]

            rapids
            [rapids.implementations.postgres-storage :as rapids-pg]

            [envvar.core :refer [env]]
            [taoensso.timbre :as log]

            [clojure.string :as str]
            [clojure.java.io :as io]
            [clojure.core.async :as async])
  (:gen-class)                ; required to run this as a standalone jar
  (:import (clojure.core.async.impl.channels ManyToManyChannel)
           (java.io IOException)))

;;; Bootstrap core.async SSE support:
(extend-type ManyToManyChannel
  ring.core.protocols/StreamableResponseBody
  (write-body-to-stream [channel response output-stream]
    (let [writer (io/writer output-stream)]
      (async/go
        (try
          (loop []
            (when-let [value (async/<! channel)]
              (doto writer (.write value) (.flush))
              (recur)))
          (catch IOException ex
            (log/debug ex))
          (finally
            (async/close! channel)
            (.close output-stream)))))))

(defn env-read
  ([key default reader]
   (-> (get @env key default) reader)))

(defn -main [& args]
  (let [port           (env-read :port "8080" read-string)
        log-level      (env-read :log-level "info" #(-> % str/lower-case keyword))
        expiry-seconds (env-read :expiry-seconds "10" read-string)]
    (pia/start
      :port port
      :join? true
      :level log-level
      :expiry-seconds expiry-seconds)))
