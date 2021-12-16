;;
;; Flows which the lab interacts with
;;
(ns pia-server.apps.anticoagulation.flows.lab
  (:require [rapids :refer :all]
            [pia-server.db.models.exports :refer :all]))

(defn call-lab-api [order]
  (let [lab-request-id (rapids.support.util/new-uuid)]
    (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    (println "LAB ORDER:" (assoc order :id lab-request-id))))

(defn validate-lab-result [lr] lr)

(defn save-lab-result [p lr]
  (update-patient! p :lab-results conj lr))

(deflow order-labs [patient & orders]
  (let [lab-request (call-lab-api {:tracking-num (current-run :id)
                                   :orders       orders})
        lab-result  (<*)]
    (validate-lab-result lab-result)
    (save-lab-result patient lab-result)
    (println "save-lab-result:" lab-result)
    lab-result))