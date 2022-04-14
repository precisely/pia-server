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

(def liver-function-test (f/select :liver-function-tests [:high :normal :low]))
(def anemia-test (f/select :anemia [:abnormal :normal]))
(def vkorc1-test (f/select :vkorc1 [:normal :sensitive :insensitive]))
(def cyp2c9-test (f/select :cyp2c9 [:normal :sensitive :insensitive]))

(deflow lab-monitor
  "Waits for results for lab for patient for the given orders.

  A final flow may be provided. This can "
  ([lab patient orders] (lab-monitor lab patient orders nil))
  ([lab patient orders final-flow]
   (require-roles :lab)
   (set-index! :patient-id (:id patient)
               :lab-id (:id lab)
               :sample :waiting)

   (send-lab-orders lab patient orders)

   (let [result (<*form `[~(f/select :status [:failed :success])
                          ~@orders])]
     (if final-flow (fcall final-flow result))
     result)))

;; idea for how to set more precise / dependent constraints
;; problem: partitioner treats closures as suspending expressions
#_(fn [constraints]
    `[:or
      [:map [:status [:= :failed]]]
      [:map [:status [:= :success]]
       ~@(remove #(-> % first (= :status)) constraints)]])
;; send order to lab
;; notify patient
;; remind patient
;;   - allow patient to cancel reminder
;; if lab order doesn't complete