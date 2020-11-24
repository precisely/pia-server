(ns pia-server.flows.cfsdemo
  (:require [longterm :refer :all]
            [pia-server.flows.components :refer :all]
            [pia-server.flows.general-fatigue :refer :all]
            [pia-server.flows.iof :refer :all]
            ))

(deflow foo-remote []
        (*> "hello")
        (let [value (<* :permit :foo :expires (-> 2 minutes from-now) :default "default-suspend-value")]
          (*> (str value " world!"))
          :result))

(deflow fatigue-iom-6m []
        (*> "This is the flow where people failing PEM go"))


(deflow welcome []
        (*> "Welcome to the Precisely app!")
        (*> "The purpose here is to track your symptoms and determine if you are developing long-term covid problems.")
        (*> "Specifically, the problems being tracked here can lead to Chronic Fatigue Syndrome (CFS)")
        (*> "You can assess your own risk for CFS by simply answering the next questions about how you feel.")
        (*> "It usually takes less than 10 minutes, or only seconds if you are showing no signs of CFS"))


(deflow ender [final-results]
        (*> "You have reached the end of this demo")
        (*> (str "Your final results were:\n" final-results)))

;;Vector that contains the ordered fatigue modules
(def fatigue-flows [general iof])


(deflow home []
        (welcome)
        (loop [modules fatigue-flows
               total-results []]
          (let [results (conj total-results (fcall (first modules)))]
            (if (or (rest modules) (not= "end" (last results)))
              (recur (rest modules) results)
              (fcall ender total-results)
              )
          ))
        )
