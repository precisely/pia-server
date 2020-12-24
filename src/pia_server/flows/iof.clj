(ns pia-server.flows.iof
  (:require [rapids :refer :all]
            [pia-server.chat :refer :all]
            ))

;; Assesses the impairment of function and the length of time experiencing the fatigue
;; Two dropout buckets exist here, fatigue and fatigue-iof

(defn iof-nslider [q]
  (*> q, (number-slider 0 "No difficulty" 10 "Very difficult"
                        :title "In the last 7 days")))

(deflow no-iof []
  (*> "Thanks for taking the time to answer these question about your impairment of function (IOF)",
      "Your level of impairment appears to be within normal ranges right now, but you should still keep track of your symptoms.",
      "To help you with that, when you see a notification from us, please come back for another chat",
      "Let's make sure you recover your energy!")
  )


(deflow iof []
  (*> "Next, you can measure your impairment of function (IOF) from fatigue")
  (*> "To do this, rate how difficult each of the following activities were for you in the last 7 days")
  (*> "For activities you did not perform in the last 7 days, try to give your best rating for the last time you did it")
  (let [
        _             (iof-nslider "Brushing or combing your hair")
        brushing-hair (<*)
        _             (iof-nslider "Walking continuously for 20 minutes")
        walking       (<*)
        _             (iof-nslider "Preparing a homemade meal")
        cooking       (<*)
        _             (iof-nslider "Vaccuuming, scrubbing, or sweeping floors")
        cleaning      (<*)
        _             (iof-nslider "Lifting and carrying a bag full of groceries")
        lifting       (<*)
        _             (iof-nslider "Climbing one flight of stairs")
        stairs        (<*)
        _             (iof-nslider "Changing the bed sheets")
        sheets        (<*)
        _             (iof-nslider "Sitting in a chair for 45 minutes")
        sitting       (<*)
        _             (iof-nslider "Go shopping for groceries")
        shopping      (<*)
        ]
    (if (> (/ (+ brushing-hair walking cooking cleaning lifting stairs sheets sitting shopping) 3) 8)
      (*> "Your answers so far show that your impairment from fatigue is a problem")
      (no-iof)
      )
    ))

