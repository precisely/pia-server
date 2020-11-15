;;
;; A quick and dirty single threaded timer for expiring Run suspensions.
;;
;; It also contains facilities for advancing time for demo purposes.
;;
(ns pia-server.expiry-monitor
  (:require
    [longterm :as lt]
    [clojure.tools.logging :as log]
    [pia-server.db :refer [get-expired-runs]]
    [clojure.core.async :refer [go-loop <! timeout chan]]))

(defn start
  ([jrs] (start jrs 1))
  ([jrs interval-seconds]
   (log/info "Expiry monitor: starting")
   (go-loop []
     (doseq [run (get-expired-runs jrs)]
       (try
         (log/info "Expiry monitor: expiring run" (:id run))
         (lt/expire-run! run)
         (catch Exception e
           (println "Expiry monitor: Failed to expire run %s" run))))
     (<! (timeout (* interval-seconds 1000)))
     (recur))))
