(ns pia-server.flows.pem
  (:require [longterm :refer :all]
            [pia-server.flows.components :refer :all]
            ))

;; Assesses the patient's post-exertional malaise
;; Dropout bucket is fatigue-iof-6m
;; The questions in this module end if any receive freq and severity of 2 or more

(defn pem-slider-freq [q]
  (*> q, (num-slider 0 "No difficulty" 10 "Very difficult" "In the last 7 days" 1)
      ))

