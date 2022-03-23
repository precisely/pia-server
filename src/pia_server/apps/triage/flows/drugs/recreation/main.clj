(ns pia-server.apps.triage.flows.drugs.recreation.main
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer :all]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.notifier :refer :all]
            [pia-server.common.util :refer [range-case round-to-nearest-half]]
            [pia-server.db.models.patient :as p]
            [pia-server.apps.triage.flows.common :refer :all]
            [pia-server.apps.triage.flows.drugs.recreation.cannabis :refer [taps2-cannabis]]
            [pia-server.apps.triage.flows.drugs.recreation.stimulants :refer [taps2-stimulants]]
            [pia-server.apps.triage.flows.drugs.recreation.heroin :refer [taps2-heroin]]))

(def q1 (multiple-choice
          :q1
          {:never "Never"
           :daily "Daily"
           :weekly "Weekly"
           :monthly "Monthly"
           :less-than-monthly "Less than monthly"}
          :required true
          :label "In the past 12 months, how often have you used recreational drugs (including marijuana, cocaine or crack, heroin, methamphetamine (crystal meth), hallucinogens, ecstasy/MDMA)?"))

(def q2 (yesno
          :q2
          :required true
          :label "Do you take any other recreational drugs or substances that you would like your doctor to be aware of before surgery?"))

(deflow recreation
  "Patient recreational drugs screening.

  Returns
  {
    :responses      map between question id and answer
    :score          integer score
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "TAPS-2: Cannabis")
  (let [frequency (form-value (<*form [q1]))
        needs-tap2 (not (= :never frequency))
        uses-other-substances (form-value (<*form [q2]))

        cannabis (when needs-tap2 (taps2-cannabis patient))
        stimulants (when needs-tap2 (taps2-stimulants patient))
        heroin (when needs-tap2 (taps2-heroin patient))

        score (->> responses
                   (vals)
                   (map #(if % 1 0))
                   (reduce +))
        results {:responses responses
                 :score     score}]
    (set-index! [:drugs :recreation :cannabis :main] results)
    results))