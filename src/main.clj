(ns pia-server.main
  (:require [ring.adapter.jetty :as jetty]
            [envvar.core :as envvar :refer [env]])
  (:require [pia-server.core :as pia])
  (:gen-class))


;;; TODO: Consider using the Component framework. Things which need to be
;;; managed:
;;;
;;; - application server
;;; - database connection pool


(defonce ^{:dynamic true} *server* (atom nil))

(defn start [app & {:keys [port join?]
                    :or {port 8080, join? false}}]
  (reset! *server* (jetty/run-jetty app {:port port, :join? join?})))

(defn stop []
  (when-not (nil? @*server*)
    (.stop @*server*)
    (reset! *server* nil)))

(defn -main [& args]
  (let [port (get @env :port 8080)]
    (start pia/app :port port, :join? true)))
