;;
;; Flows which the lab interacts with
;;
(ns pia-server.apps.anticoagulation.flows.lab
  (:require [rapids :refer :all]
            [pia-server.shared.roles :refer [require-roles]]
            [pia-server.db.models.exports :refer :all]))

(defn- send-lab-orders
  "Calls the lab API, sends fax, etc."
  [lab patient orders]
  (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
  (println "LAB ORDER:" lab
           {:tracking-num (current-run :id)
            :patient      patient
            :orders       orders}))

(def LabStatus #{"received" "processing" "failed" "success"})

(defn lab-status-error [status]
  (throw (ex-info (str "Invalid lab status provided. Continue data should be a map with 'status' and"
                       " optional 'result' keys. The status key should be one of " (seq LabStatus)) {:type :input-error :status status})))

(deflow start-labwork
  "Returns a run which takes input from the lab. The run provides a :sample status value:
   :sample - one of \"waiting\" \"received\" \"processing\" \"failed\" or \"success\"
   When \"success\" or \"failed\" is reached, the run completes.
   If the labwork status is \"success\", the return value is the lab results.
   If the labwork status is \"failed\", the return value represents the failure reason."
  [lab patient & orders]
  (start!
    (flow []
      (require-roles :lab)
      (set-status! :patient-id (:id patient))
      (send-lab-orders lab patient orders)
      (loop
        [{status :status result :result} (<*)]

        (if-not (LabStatus status)
          (lab-status-error status)
          (set-status! :sample status))

        (cond
          (#{"failed" "success"} status) result
         :else (recur (<*)))))))

;; send order to lab
;; notify patient
;; remind patient
;;   - allow patient to cancel reminder
;; if lab order doesn't complete