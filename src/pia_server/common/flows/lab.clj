;;
;; Flows which the lab interacts with
;;
(ns pia-server.common.flows.lab
  (:require [rapids :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.controls.form :as f :refer [<*form]]
            [pia-server.db.models.exports :refer :all]))

(defn- send-lab-orders
  "Calls the lab API, sends fax, etc."
  [lab patient orders]
  (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
  (println "LAB ORDER:" lab
           {:tracking-num (current-run :id)
            :patient      patient
            :orders       orders}))

(defn normalize-lab-order [order]
  (cond
    (keyword? order) {:type order}
    (map? order) (do (assert (keyword? (:type order)) "Lab order ")
                     order)
    :else (throw (ex-info "Invalid lab order" {:type :input-error :order order}))))

(defn orders-to-form-elements [orders]
  (let [norders (map normalize-lab-order orders)]
    `[~(f/multiple-choice :status [:waiting :failed :success :received])
      ~@(map #(f/number (:type %) :label (or (:text %) (name (:type %))))
             norders)]))

(deflow lab-monitor [lab patient orders]
  (require-roles :lab)
  (set-index! :patient-id (:id patient) :sample :waiting)
  (send-lab-orders lab patient orders)

  (loop
    [data (<*form (orders-to-form-elements orders))]

    (cond
      (#{:failed :success} (:status data)) data
      :else (recur (<*)))))


;; send order to lab
;; notify patient
;; remind patient
;;   - allow patient to cancel reminder
;; if lab order doesn't complete