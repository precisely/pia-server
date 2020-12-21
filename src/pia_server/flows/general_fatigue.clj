(ns pia-server.flows.general-fatigue
  (:require [rapids :refer :all]
            [pia-server.chat :refer :all]))

;; A module to do an assessment of the patient's general fatigue
;; Those that drop out are bucketed as no-severe-fatigue

(defn general-nslider [q]
  (:result (survey {}
             (number-slider :result "In the last 7 days" 1 7
               :min-text "Strongly disagree"
               :max-text "Strongly agree"))))

(deflow no-severe-fatigue []
  (*> "Thanks for taking that time to answer the fatigue questions.",
    "Your fatigue levels appear to be within normal ranges right now, but you should still keep track of your symptoms.",
    "To help you with that, you can come back here to answer some more questions when you see a notification",
    "Let's make sure you recover your energy!")
  "end")

(deflow general []
  (*> "Now, let's chat about fatigue you've been feeling since last week.")
  (let [
        _ (general-nslider "Is your motivation lower when you've been fatigued?")
        motivation (<*)
        _ (general-nslider "Does exercise bring on your fatigue?")
        exercise (<*)
        _ (general-nslider "Are you easily fatigued?")
        ease (<*)
        _ (general-nslider "Does the fatigue interfere with your physical functioning?")
        physf (<*)
        _ (general-nslider "Do you have frequent problems caused by your fatigue?")
        problems (<*)
        _ (general-nslider "Is sustained physical functioning prevented by your fatigue?")
        sustain (<*)
        _ (general-nslider "Does your fatigue interfere with carrying out certain duties and responsbilities?")
        interfere (<*)
        _ (general-nslider "Would you rank fatigue among your three most disabling symptoms?")
        rank (<*)
        _ (general-nslider "Does your fatigue interfere with your work, family, or social life?")
        socials (<*)]
    (if (< (+ motivation exercise ease physf problems sustain interfere rank socials) 36)
      (no-severe-fatigue)
      (*> "It sounds like you have some serious fatigue issues.",
        "This means there could be problems in your recovery that we need to look at more closely.")
      )))
