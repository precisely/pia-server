(ns pia-server.apps.triage.flows.drugs.recreation.cannabis
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer :all]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.notifier :refer :all]
            [pia-server.common.util :refer [range-case round-to-nearest-half]]
            [pia-server.db.models.patient :as p]
            [pia-server.apps.triage.flows.common :refer :all]))

(def taps2-q1 (yesno
                :taps2-q1
                :required true
                :label "In the PAST 3 MONTHS, did you use marijuana (hash, weed)?"))

(def taps2-q2 (yesno
                :taps2-q2
                :required true
                :label "In the PAST 3 MONTHS, have you had a strong desire or urge to use marijuana at least once a week or more often?"))

(def taps2-q3 (yesno
                :taps2-q3
                :required true
                :label "In the PAST 3 MONTHS, has anyone expressed concern about your use of marijuana?"))

(deflow taps2-cannabis
  "TAPS-2 cannabis questions.

  Returns
  {
    :responses      map between question id and answer
    :score          integer score
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "TAPS-2: Cannabis")
  (let [q1-result (form-value (<*form [taps2-q1]))
        responses (conj (if q1-result (<*form [taps2-q2 taps2-q3]) {})  [:taps2-q1 q1-result])
        score (->> responses
                   (vals)
                   (map #(if % 1 0))
                   (reduce +))
        results {:responses responses
                 :score     score}]
    (set-index! [:drugs :recreation :cannabis] results)
    results))