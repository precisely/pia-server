(ns pia-server.flows.six-months
  (:require [rapids :refer :all]
            [pia-server.flows.components :refer :all]
            ))

(defn time-choices [q]
  (*> q, (choices ["1 week" "1 month" "3 months" "6 months or more"] "Select the closest time")))

(deflow iof-no-time []
        (*> "Thanks answering these questions so far!",
            "You have some impairment from fatigue, and it's typical to feel this way for a short amount of time",
            "You could still be at risk for CFS, however, and by tracking these symptoms you can catch any serious indicators quickly",
            "To help you with that, when you see a notification from us, please come back for another chat",
            "Let's make sure you recover your energy!"))

(deflow six-months []
        (let [_ (time-choices "How long have you felt this way?")
              length (<*)]
          (if (not= length "6 months or more")
            (iof-no-time)
            (*> "Your significant impairment and the length of time you have felt this way are the first indicators of CFS",
                "It's important that you take just a few more moments to complete the rest of the questions"))
          )
        )
