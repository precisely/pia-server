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

(deflow lab-monitor [lab patient orders]
  (require-roles :lab)
  (set-index! :patient-id (:id patient)
              :lab-id (:id lab)
              :sample :waiting)

  (send-lab-orders lab patient orders)

  (<*form `[~(f/multiple-choice :status [:failed :success])
            ~@orders]
          #_(fn [constraints]
            `[:or
              [:map [:status [:= :failed]]]
              [:map [:status [:= :success]]
               ~@(remove #(-> % first (= :status)) constraints)]])))


;; send order to lab
;; notify patient
;; remind patient
;;   - allow patient to cancel reminder
;; if lab order doesn't complete