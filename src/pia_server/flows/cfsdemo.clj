(ns pia-server.flows.cfsdemo
  (:require [longterm :refer :all]
            [pia-server.flows.components :refer :all]
            [pia-server.flows.general-fatigue :refer :all]
            [pia-server.flows.iof :refer :all]
            [pia-server.flows.six-months :refer :all]
            ))

(deflow foo-remote []
        (*> "hello")
        (let [value (<* :permit :foo :expires (-> 2 minutes from-now) :default "default-suspend-value")]
          (*> (str value " world!"))
          :result))

(deflow fatigue-iof-6m []
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


(deflow timeout [at-time]
        (<* :expires at-time))
;(deflow repeating-checkin
;        "Keeps running checkin-flow until until-date with a delay of delay.
;        checkin-flow should return nil if the checkin should continue, otherwise, it returns a value
;        which will be returned by repeating-checkin"
;        [delay until-date checkin-flow]
;        (loop []
;          (when (< (now) until-date) ;; haven't reached until-date
;            (<! (start! (timeout (java-time/plus (now) delay))))
;            (ifit [result (fcall checkin-flow)]
;                  result
;                  (recur)))))

;;Vector that contains the ordered fatigue modules
(def fatigue-flows [general iof six-months])


(deflow map-home [flow]
        (fcall flow))


(deflow home [given-flows]
        (welcome)
        (loop [
               results []
               [flow & flows] given-flows
               ]
          (println flow)
          (let [new-results (conj results (fcall flow))]
            (if (rest flows)
                (recur flows new-results))
          )
        ))

