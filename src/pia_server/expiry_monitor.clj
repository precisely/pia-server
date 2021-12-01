;;
;; A quick and dirty single threaded timer for expiring Run suspensions.
;;
;; It also contains facilities for advancing time for demo purposes.
;;
(ns pia-server.expiry-monitor
  (:require
    [rapids :as r]
    [taoensso.timbre :as log]
    [clojure.stacktrace :as stacktrace]
    [clojure.core.async :refer [go-loop <! timeout]]))

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
       (go-loop []
         (if *interval*
           (do
             (try
               (r/ensure-cached-connection
                 (doseq [run (r/get-expired-runs)
                         :let [run-id (:id run)]]
                   (try
                     (log/debug "Expiry monitor: expiring run" run-id)
                     (r/expire-run! run)
                     (catch Exception e
                       (log/error "Expiry monitor: run " run-id ": " e)
                       (stacktrace/print-stack-trace e)))))
               (catch Exception e
                 (log/error "Expiry monitor: failed while retrieving expired runs:" e)
                 (stacktrace/print-stack-trace e)))
             (<! (timeout (* *interval* 1000)))
             (recur))
           ;; ELSE:
           (log/debug "Expiry monitor: stopped")))))))

(defn stop []
  (log/debug "Expiry monitor: requesting stop")
  (alter-var-root #'*interval* (constantly nil)))
