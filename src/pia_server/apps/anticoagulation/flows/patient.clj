;;
;; Flows which the patient interacts with
;;
(ns pia-server.apps.anticoagulation.flows.patient
  (:require [rapids :refer :all]
            [pia-server.shared.ux.basic :refer :all]
            [pia-server.shared.notifier :refer :all]))

(deflow start-pick-lab-for-orders
  "In future, this might launch an interaction with the patient to determine the right lab. This might just request
  a map picker/list control be shown, for example, with labs which provide the appropriate bloodwork.
  Extra points for a flow which "
  ;; QUESTION: what if multiple tests are requested and no lab provides all coverage? What if some labs provide all
  ;; coverage (but they're further away) while closer labs provide partial coverage?
  [patient orders]
  ;; in future, initiate the interaction:
  #_(notify patient "Please pick a lab")
  (start! (flow []
            (set-status! :roles [:patient] :patient-id (:id patient))
            ;; for now, just return a default lab...
            {:id "lc-9876", :name "Labcorp Lab, 123 Main Street"})))

(defn pills-from-dosage
  "Given two numbers representing units-per-time (the dosage) and unit-per-pill,
  the amount of the drug per pill, returns the number of pills per unit time"
  [unit-per-time unit-per-pill]
  [:pre [(number? unit-per-time) (number? unit-per-pill)]]
  (/ unit-per-time unit-per-pill))

(deflow initiation-phase
  ([patient initial-dosage units-per-pill days]
   (set-status! :roles [:patient])
   (loop [day    1
          dosage initial-dosage]
     (let [pills (pills-from-dosage dosage units-per-pill)]
       (>* (text "It's day" day " of " days)
           (text "Time to take your dose of coumadin (" pills ") pills."))
       (>* "Please confirm you took your dose.")
       (case (<*buttons [{:id :yes, :text (str "Yes, " pills " pills")}
                         {:id :no, :text "No, there was a problem"}])
         :no ))))
  ([patient initial-dosage units-per-pill]
   (initiation-phase patient initial-dosage units-per-pill 5)))

(deflow adverse-reaction-questions
  []
  )

(defn start-patient-initiation-phase [patient dosage]
  (start! initiation-phase patient dosage))

(deflow start-maintenance-loop [])