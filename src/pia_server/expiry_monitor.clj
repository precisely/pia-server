;;
;; A quick and dirty single threaded timer for expiring Run suspensions.
;;
;; It also contains facilities for advancing time for demo purposes.
;;
(ns pia-server.expiry-monitor
  (:require
    [longterm :as lt]
    [taoensso.timbre :as log]
    [pia-server.db :refer [get-expired-run-ids]]
    [clojure.core.async :refer [go-loop <! timeout chan]]
    [pia-server.db :as db]))

(defn start
  ([] (start 30))
  ([interval-seconds]
   (let [interval-seconds (or interval-seconds 30)]
     (log/info "Expiry monitor: starting")
     (go-loop []
       (db/with-transaction [jrs]
         (doseq [run-id (get-expired-run-ids jrs)]
           (try
             (log/debug "Expiry monitor: expiring run" run-id)
             (lt/expire-run! run-id)
             (catch Exception e
               (log/error "Expiry monitor: run " run-id ": " e)))))
       (<! (timeout (* interval-seconds 1000)))
       (recur)))))
