(ns pia-server.flows.sleep
  (:require [longterm :refer :all]
            [pia-server.flows.components :refer :all]
            ))

;; Assesses the patient's post-exertional malaise
;; Dropout bucket is fatigue-iof-6m-pem
;; There are two UX strategies we could use here
;; 1) The questions in these flows end if any receive freq and severity of 2 or more (this is what is present here)
;; 2) Ask all question and compute score thresholds after, which provides more symptom tracking data that could be useful
;; later

(defn sleep-freqs [q]
  (*> q, (num-slider 0 "None of the time" 4 "All of the time" "Frequency in the last 6 months" 1)
      ))

(defn sleep-sevs [q]
  (*> q, (num-slider 0 "None of the time" 4 "All of the time" "Frequency in the last 6 months" 1)
      ))


(def sleep-questions {
                      '"Feeling unrefreshed after you wake up in the morning" "How severe is this feeling?",
                      '"Need to nap daily"                                    "Can you provide how severe this urge is?",
                      '"Problems falling asleep"                              "Please rate how severe these problems are",
                      '"Problems staying asleep"                              "And now how severe is this",
                      '"Waking up early in the morning (e.g. 3 am)"           "Can you rate how severe this is?",
                      '"Sleep during day and stay awake during night"         "What is the severity of this problem?"
                      }
  )



(deflow no-sleep []
        (*> "Thanks for taking the time to answer these questions until this point",
            "Your sleep issues appear to be within normal ranges right now, but you should still keep track of your symptoms",
            "To help you with that, you can come back here to answer some more questions when you see a notification",
            "Let's make sure you recover your energy!"))


[(deflow sleep []
         (*> "This part of the test will focus on your sleep symptoms, which are very important.",
             "In the next statements, rate the frequency and severity you felt the problems in the last 6 months.")
         (loop [
                [question & questions] sleep-questions
                ]
           (let [
                 _ (sleep-freqs (key question))
                 freq (<*)
                 _ (sleep-sevs (val question))
                 sev (<*)
                 ]
             (if (and (>= freq 2) (>= sev 2))
               (*> "Based on these responses, your sleep problems indicate a possibly serious issue",
                   "Also, when examining your answers from previous sections, your overall risk for developing chronic fatigue is high",
                   "There are two more short sections to complete now, and we urge you to finish this assessment so we can make as informed of a referral as possible.")
               (if (rest questions)
                 (recur (first questions) (rest questions))
                 (no-sleep)
                 )))
           ))]