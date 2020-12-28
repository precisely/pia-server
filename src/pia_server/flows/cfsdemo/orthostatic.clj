(ns pia-server.flows.cfsdemo.orthostatic
  (:require [rapids :refer :all]
            [pia-server.flows.form :refer :all]
            ))

;; Assesses the patient's post-exertional malaise
;; Dropout bucket is defined by the previous coginitive flows logic. There is no bucket specific for orthostatic intolerance right now
;; and this data is pretty much hardened
;; There are two UX strategies we could use here
;; 1) The questions in these flows end if any receive freq and severity of 2 or more (this is what is present here)
;; 2) Ask all question and compute score thresholds after, which provides more symptom tracking data that could be useful
;; later



(deflow orthostatic []
        (*> "For the final piece of information, there are a few questions about how you feel when sitting or standing in an upright position.",
            "Please answer them as best as you can, then we can finish up and help you make some decisions")
        (let [
              _ (*> "Do you experience fatigue when you stand up?")
              _ (num-slider 0 "Never" 4 "Always" "Fatigue when standing up" 1)
              when-standing (<*)
              _ (*> "Which fatigue severity statement do you relate to the most?")
              _ (choices [
                          "I do not experience fatigue when I stand up"
                          "I experience mild fatigue when I stand up"
                          "I experience moderate fatigue when I stand up and sometimes have to sit back down for relief"
                          "I experience severe fatigue when I stand up and frequently have to sit back down for relief"
                          "I experience severe fatigue when I stand up and almost always have to sit back down for relief"] "Select a sentence")
              severity-statement (<*)
              _ (*> "Now, for our last question, think about some of these conditions:",
                    "Prolonged standing, a meal, exertion (like walking), or heat exposure like a shower or hot day",
                    "After these circumstances, how often do you experience fatigue?")
              _ (choices [
                          "Never under any of these circumstances"
                          "Sometimes under these conditions"
                          "Often under these conditions"
                          "Usually under these conditions"
                          "Always when I stand up, the conditions do not matter"
                          ] "Choose the most relatable one")
              conditions-statement (<*)]
          (*> "Thank you again for providing these answers"
              "You've reached the end of the assessment!",
              )
          ))