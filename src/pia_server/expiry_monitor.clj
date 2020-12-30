;;
;; A quick and dirty single threaded timer for expiring Run suspensions.
;;
;; It also contains facilities for advancing time for demo purposes.
;;
(ns pia-server.expiry-monitor
  (:require
    [rapids :as lt]
    [taoensso.timbre :as log]
    [pia-server.db-runs :refer [get-expired-run-ids]]
    [clojure.core.async :refer [go-loop <! timeout chan]]
    [pia-server.db-runs :as db-runs]))

(def ^:dynamic *interval* nil)
(defn start
  ([] (start 30))
  ([interval-seconds]
   (if *interval*
     (do
       (log/warn "Changing expiry monitor interval from" *interval* "to" interval-seconds)
       (alter-var-root *interval* interval-seconds))
     (let [interval-seconds (or interval-seconds 30)]
       (alter-var-root #'*interval* (constantly interval-seconds))
       (log/info "Expiry monitor: starting")
       (db-runs/start-connection-pool!)
       (go-loop []
         (db-runs/with-transaction [jrs]
           (doseq [run-id (get-expired-run-ids jrs)]
             (try
               (log/debug "Expiry monitor: expiring run" run-id)
               (lt/expire-run! run-id)
               (catch Exception e
                 (log/error "Expiry monitor: run " run-id ": " e)))))
         (if *interval*
           (do (<! (timeout (* *interval* 1000)))
               (recur))
           (log/debug "Expiry monitor: stopped")))))))

(defn stop []
  (log/debug "Expiry monitor: requesting stop")
  (alter-var-root #'*interval* (constantly nil)))