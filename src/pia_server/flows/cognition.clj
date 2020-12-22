(ns pia-server.flows.cognition
  (:require [rapids :refer :all]
            [pia-server.flows.form :refer :all]
            ))

;; Assesses the patient's cognitive impairment
;; Dropout bucket is fatigue-iof-6m-pem-sleep HOWEVER all patients should still be provided the orthostatic test and given referrals due
;; to their "likely CFS" state
;; Also, patients that show significant cognitive impairment are bucketed as fatigue-iof-6m-pem-sleep-ci
;; There are two UX strategies we could use here
;; 1) The questions in these flows end if any receive freq and severity of 2 or more (this is what is present here)
;; 2) Ask all question and compute score thresholds after, which provides more symptom tracking data that could be useful
;; later

(defn cognition-freqs [q]
  (*> q, (num-slider 0 "None of the time" 4 "All of the time" "Frequency in the last 6 months" 1)
      ))

(defn cognition-sevs [q]
  (*> q, (num-slider 0 "None of the time" 4 "All of the time" "Frequency in the last 6 months" 1)
      ))


(def cognition-questions {
                          '"Problems remembering things"                                          "How severe is this?",
                          '"Difficulty paying attention for a long period of time"                "Can you rate the severity of this difficulty?",
                          '"Difficulty finding the right word to say or expressing thoughts"      "Please rate how severe these problems are",
                          '"Difficulty understanding things"                                      "And now how severe is this?",
                          '"Only able to focus on one thing at a time"                            "Can you rate this inability?",
                          '"Unable to focus vision"                                               "How severe is this inability?",
                          '"Unable to focus attention"                                            "Please give the severity of this",
                          '"Loss of depth perception"                                             "How bad is this loss?",
                          '"Slowness of thought"                                                  "How would you rate the severity of this slowness?",
                          '"Absent-mindedness or forgetfulness"                                   "Can you give the severity of this problem?",
                          '"Feeling disoriented"                                                  "How severe is this feeling?",
                          '"Slowed speech"                                                        "Please rate how bad this slowness is",
                          '"Difficulty reading (dyslexia) after mild physical or mental activity" "How you describe the severity of this difficulty?"
                          }
  )



(deflow no-cognition []
        (*> "Thanks for taking the time to answer these questions until this point",
            "Your cognitive issues appear to be within normal ranges right now, but you should still keep track of your symptoms",
            "To help you with that, you can come back here to answer some more questions when you see a notification",
            "Let's make sure you recover your energy!"))


(deflow cognition []
        (*> "Let's now take a look at your cognitive impairment symptoms",
            "We'll use the same type of answers as before - please rate the frequency and severity you felt the problems in the last 6 months")
        (loop [
               [question & questions] cognition-questions
               ]
          (let [
                _ (cognition-freqs (key question))
                freq (<*)
                _ (cognition-sevs (val question))
                sev (<*)
                ]
            (if (and (>= freq 2) (>= sev 2))
              (*> "Based on these responses, your cognitive fatigue also indicates the need for a referral",
                  "Now let's move on to the last (short) part. Almost done!"
                  )
              (if (rest questions)
                (recur (first questions) (rest questions))
                (*> "Your cognitive impairment is in normal ranges, however you are still at a high risk of developing chronic fatigue",
                    "Now let's move on to the last (short) part. Almost done!")
                )))
              ))