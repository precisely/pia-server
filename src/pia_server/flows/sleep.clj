(ns pia-server.flows.sleep
  (:require [longterm :refer :all]
            [pia-server.flows.components :refer :all]
            ))

;; Assesses the patient's post-exertional malaise
;; Dropout bucket is fatigue-iof-6m
;; There are two UX strategies we could use here
;; 1) The questions in these flows end if any receive freq and severity of 2 or more
;; 2) Ask all question and compute score thresholds after, which provides more symptom tracking data that could be useful
;; later (This is what we'll do for now)

(defn pem-freqs [q]
  (*> q, (num-slider 0 "None of the time" 4 "All of the time" "Frequency in the last 6 months" 1)
      ))

(defn pem-sevs [q]
  (*> q, (num-slider 0 "None of the time" 4 "All of the time" "Frequency in the last 6 months" 1)
      ))

(defn pem-questions []
  (*> "Let's now look at how often and how much you feel tired after activities. This is called post-exertional malaise (PEM)",
      "In the next statements, rate the frequency and severity you felt in the last 6 months.")
  (let [
        _ (pem-freqs "A dead, heavy feeling after starting exercise")
        dead-freq (<*)
        _ (pem-sevs "How severe was this feeling?")
        dead-sev (<*)
        _ (pem-freqs "Next day soreness or fatigue after non-strenuous, everyday exercise")
        soreness-freq (<*)
        _ (pem-sevs "And can you rate how severe this was?")
        soreness-sev (<*)
        _ (pem-freqs "How often are you mentally tired after the slightest effort?")
        mental-freq (<*)
        _ (pem-sevs "Please rate how severe this mental tiredness is")
        mental-sev (<*)
        _ (pem-freqs "How frequently does a minimum amount of exercise make you physically tired?")
        physical-freq (<*)
        _ (pem-sevs ("Can you rate how severe this physical tiredness is?"))
        physical-sev (<*)
        _ (pem-freqs ("About how often are you physically drained or sick after mild activity?"))
        drained-freq (<*)
        _ (pem-sevs ("How severe is that feeling?"))
        drained-sev
        ]
    (*> "From your responses, its best if we continue collecting some more information from you."))
  (*> "Dead, heavy feeling after starting exercise"))

