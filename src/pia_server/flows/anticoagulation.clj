(ns pia-server.flows.anticoagulation
  (:require [rapids :refer :all]))

(def +patients+ (atom {123 {:id             123,
                            :name           "Bob Smith"
                            :email          "bob@bobmail.com"
                            :age            55
                            :diseases       {
                                             :heart-disease  true,
                                             :kidney-disease false,
                                             :hypertension   false,
                                             }
                            :blood-pressure 123
                            :phone          "1-555-555-5555"}}))

(defn get-patient [pid]
  (get @+patients+ pid))

(defn validate-lab-result [lr] lr)

(defn save-lab-result [p lr]
  (let [new-p (update p :lab-results #(if % (conj % lr) [lr]))]
    (swap! +patients+ assoc (:id p) new-p)))

(defn call-lab-api [order]
  (println "LAB ORDER:" order))

(deflow get-baseline-inr [patient]
  )

(deflow get-blood-order [patient]
  (call-lab-api {:tracking-num (current-run :id)
                 :orders       [{:type :creatinine-clearance}]})
  (let [lab-result (<*)]
    (validate-lab-result lab-result)
    (save-lab-result patient lab-result)
    (println "save-lab-result:" lab-result)
    lab-result))

(defn get-disease-conditions [patient])
(defn get-patient-age [patient])
;; Version 0: curl precisely/api/runs/{current-run-id} -X '{"iron"
;; Version 1: curl https://precise.ly/callbacks/labcorp/ -X '{"accession-number" 123123, }'

(defn get-patient-medications [patient])
(defn get-patient-schedule [patient])

(deflow anticoagulation [pid]
  (let [patient            (get-patient pid)
        blood-order        (<<! (start! get-blood-order patient))
        {baseline-inr        :baseline-inr,
         creatinine-level :creatinine-level
         cbc-platelets :cbc-platelets} blood-order
        disease-conditions (get-disease-conditions patient) ; hypertension, congestive-heart-failure
        age                (get-patient-age patient)
        medications (get-patient-medications patient)
        upcoming-surgeries (get-patient-schedule patient)]
    (print "blood-order-run" blood-order)



    ()))
