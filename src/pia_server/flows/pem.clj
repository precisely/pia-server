(ns pia-server.flows.pem
  (:require [rapids :refer :all]
            [pia-server.chat.survey :refer :all]
            ))

;; Assesses the patient's post-exertional malaise
;; Dropout bucket is fatigue-iof-6m
;; There are two UX strategies we could use here
;; 1) The questions in these flows end if any receive freq and severity of 2 or more (this is what is present here)
;; 2) Ask all question and compute score thresholds after, which provides more symptom tracking data that could be useful
;; later


(defn pem-freqs [q]
  (*> q, (rating "pemFreq" "Frequency in the last 6 months" 0 4 :min-text "None of the time" :max-text "All of the time")))

(defn pem-sevs [q]
  (*> q, (rating "pemSev" "Severity in the last 6 months" 0 4 :min-text "No issue" :max-text "Very severe")))


(def pem-questions {
                    '"A dead, heavy feeling after starting exercise"                       "How severe was this feeling?",
                    '"Next day soreness or fatigue after non-strenuous, everyday exercise" "And can you rate how severe this was?",
                    '"Mentally tired after the slightest effort"                           "Please rate how severe this mental tiredness is",
                    '"Minimum exercise makes you physically tired"                         "Can you rate how severe this physical tiredness was?",
                    '"Physically drained or sick after mild activity"                      "How severe is that feeling?"
                    }
  )



(deflow no-pem []
        (*> "Thanks for taking the time to answer these questions until this point",
            "Your post-exertional malaise appear to be within normal ranges right now, but you should still keep track of your symptoms.",
            "To help you with that, you can come back here to answer some more questions when you see a notification",
            "Let's make sure you recover your energy!"))


;(deflow pem []
;        (*> "Let's now look at how often and how much you feel tired after activities. This is called post-exertional malaise (PEM)",
;            "In the next statements, rate the frequency and severity you felt in the last 6 months.")
;        (loop [
;               [question & questions] pem-questions
;               ]
;          (let [
;                _ (pem-freqs (key question))
;                freq (<*)
;                _ (pem-sevs (val question))
;                sev (<*)
;                ]
;            (if (and (>= freq 2) (>= sev 2))
;              (*> "After your answers so far, your post-exertional malaise shows serious signs of long-term issues",
;                  "You would benefit greatly from completing the next questions, and overall you are almost finished with the assessment")
;              (if (rest questions)
;                (recur (first questions) (rest questions))
;                (no-pem)
;                )))
;              ))