(ns pia-server.flows.iof
  (:require [longterm :refer :all]
            [pia-server.flows.components :refer :all]
            ))

(defn iof-nslider [q]
  (*> q, (num-slider 0 "No difficulty" 10 "Very difficult" "In the last 7 days" 1)
      ))

(deflow no-iof []
        (*> "Thanks for taking the time to answer these question about your impairment of function (IOF)",
            "Your level of impairment appears to be within normal ranges right now, but you should still keep track of your symptoms.",
            "To help you with that, when you see a notification from us, please come back for another chat",
            "Let's make sure you recover your energy!")
        "end")

(deflow iof []
        (*> "This is the iof flow"))

