;;
;; Flows for patient tobacco screening
;;
(ns pia-server.apps.triage.flows.drugs.tobacco
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer :all]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.notifier :refer :all]
            [pia-server.common.util :refer [range-case round-to-nearest-half]]
            [pia-server.db.models.patient :as p]
            [pia-server.apps.triage.flows.common :refer :all]
            [rapids.active-doc :as adoc]))

(def has-used (multiple-choice
                :has-used
                {:yes "Yes"
                 :no  "No"}
                :required true
                :label "Do you currently or have you ever smoked or used tobacco?"))

(deflow initial-screen
  "Asks if the patient has smoked or used tobacco before.

  Returns bool"
  (let [responses (<*form [has-used])
        result (case (form-value responses)
                 :yes true
                 :no false)]
    (set-index! [:drugs :tobacco :has-used] result)
    result))

(deflow tobacco
  "Patient tobacco screening.

  Returns
  {
    :decision       [1,5]
    :has-used       bool
    :responses      output of questions
    :taps2          output of taps-2
  }"
  [patient]
  (require-roles :patient)
  (set-index! :patient-id (:id patient) :title "Tobacco Screen")
  (let [has-used (initial-screen patient)
        result {:score    score
                :severity severity
                :decision decision
                :phq2     phq2-result
                :phq9     phq9-result}]
    (adoc/set-data! patient-doc
                    [:depression :score] score
                    [:depression :severity] severity
                    [:depression :decision] decision)
    result))