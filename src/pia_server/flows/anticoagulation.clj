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
  (let [lab-request-id (rapids.support.util/new-uuid)]
    (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    (println "LAB ORDER:" (assoc order :id lab-request-id))))

(defn notify [entity message & {:keys [run-id]
                                :or   {run-id   (current-run :id)}}]
   (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
   (println "SENDING NOTIFICATION => " entity ": "
            {:message message :run-id run-id}))

(deflow get-baseline-inr [patient clinic]
  #_#_#_(let [inr-lab (get-inr-lab clinic)])
  (notify clinic patient :initial-inr)
  (notify-patient patient clinic "You "))

(deflow order-labs [patient & orders]
  (let [lab-request (call-lab-api {:tracking-num (current-run :id)
                                   :orders       orders})
        lab-result  (<*)]
    (validate-lab-result lab-result)
    (save-lab-result patient lab-result)
    (println "save-lab-result:" lab-result)
    lab-result))

(defn get-disease-conditions [patient]                      ; could involve other processes
  (:diseases patient))

(defn get-patient-age [patient]                             ; could be a question
  (:age patient))

;; Version 0: curl https://precisely/api/runs/{current-run-id} -X '{"iron"
;; Version 1: curl https://precise.ly/callbacks/labcorp/ -X '{"accession-number" 123123, }'

(defn get-patient-medications [patient])
(defn get-patient-schedule [patient])

(deflow anticoagulation [pid]
  (let [patient            (get-patient pid)
        blood-order        (<<! (start! order-labs patient [:iron :cbc]))
        {baseline-inr        :baseline-inr,
         creatinine-level :creatinine-level
         cbc-platelets :cbc-platelets} blood-order
        disease-conditions (get-disease-conditions patient) ; hypertension, congestive-heart-failure
        age                (get-patient-age patient)
        medications (get-patient-medications patient)
        upcoming-surgeries (get-patient-schedule patient)]
    (print "blood-order-run" blood-order)))

;; onboarding
;; (+) get blood work done
;; pharmacy order
;;   - stdout
;;   - patient notified
;;   - patient nagged
;; doctor escalation (steps)
;; clinic interface monitors patient status
;; initiation loop
;; maintenance loop
;; interrupt the flow
