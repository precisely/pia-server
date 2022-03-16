;;
;; Flows for patient prescription screening
;;
(ns pia-server.apps.triage.flows.drugs.prescription
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer :all]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.notifier :refer :all]
            [pia-server.common.util :refer [range-case round-to-nearest-half]]
            [pia-server.db.models.patient :as p]
            [pia-server.apps.triage.flows.common :refer :all]))

(def initial-q1 (multiple-choice
                  :initial-q1
                  {:never             "Never"
                   :daily             "Daily"
                   :weekly            "Weekly"
                   :monthly           "Monthly"
                   :less-than-monthly "Less than monthly"}
                  :required true
                  :label (str "In the past 12 months, how often have you used any prescription medications just for the"
                              " feeling, more than prescribed or that were not prescribed for you (including opiate"
                              " pain relievers, medications for anxiety or sleeping, medications for ADHD)?")))


(deflow initial-screen
  "Asks if the patient has inappropriately used prescription medication.

  Returns frequency-used"
  [patient]
  (set-index! :patient-id (:id patient) :title "Prescription Medication")
  (let [frequency-used (form-value (<*form [initial-q1]))]
    (set-index! [:drugs :prescription :frequency-used] frequency-used)
    frequency-used))

(def taps2-opioid-q1 (yesno
                :taps2-opioid-q1
                :required true
                :label (str "In the past 3 months, did you use a prescription opiate pain reliever (for example,"
                            " Percocet, Vicodin) not as prescribed or that was not prescribed for you?")))

(def taps2-opioid-q2 (yesno
                :taps2-opioid-q2
                :required true
                :label "In the past 3 months, have you tried and failed to control, cut down or stop using an opiate pain reliever?"))

(def taps2-opioid-q3 (yesno
                :taps2-opioid-q3
                :required true
                :label "In the past 3 months, has anyone expressed concern about your use of an opiate pain reliever?"))

(deflow taps2-opioid
  "TAPS-2 opioid questions.

  Returns
  {
    :responses      map between question id and answer
    :score          integer score
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "Opioid TAPS-2")
  (let [q1-result (form-value (<*form [taps2-opioid-q1]))
        responses (conj (if q1-result (<*form [taps2-opioid-q2 taps2-opioid-q3]) {})  [:taps2-opioid-q1 q1-result])
        score (->> responses
                   (vals)
                   (map #(if % 1 0))
                   (reduce +))
        results {:responses responses
                 :score     score}]
    (set-index! [:drugs :prescription :taps2-opioid] results)
    results))

(def taps2-sedative-q1 (yesno
                :taps2-sedative-q1
                :required true
                :label "In the past 3 months, did you use a medication for anxiety or sleep (for example, Xanax, Ativan, or Klonopin) not as prescribed or that was not prescribed for you?"))

(def taps2-sedative-q2 (yesno
                :taps2-sedative-q2
                :required true
                :label "In the past 3 months, have you had a strong desire or urge to use medications for anxiety or sleep at least once a week or more often?"))

(def taps2-sedative-q3 (yesno
                :taps2-sedative-q3
                :required true
                :label "In the past 3 months, has anyone expressed concern about your use of medication for anxiety or sleep?"))

(deflow taps2-sedative
  "TAPS-2 sedative questions.

  Returns
  {
    :responses      map between question id and answer
    :score          integer score
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "Sedative TAPS-2")
  (let [q1-result (form-value (<*form [taps2-sedative-q1]))
        responses (conj (if q1-result (<*form [taps2-sedative-q2 taps2-sedative-q3]) {})  [:taps2-sedative-q1 q1-result])
        score (->> responses
                   (vals)
                   (map #(if % 1 0))
                   (reduce +))
        results {:responses responses
                 :score     score}]
    (set-index! [:drugs :prescription :taps2-sedative] results)
    results))

(def taps2-stimulant-q1 (yesno
                :taps2-stimulant-q1
                :required true
                :label "In the past 3 months, did you use a medication for ADHD (for example, Adderall, Ritalin) not as prescribed or that was not prescribed for you?"))

(def taps2-stimulant-q2 (yesno
                :taps2-stimulant-q2
                :required true
                :label "In the past 3 months, did you use a medication for ADHD (for example, Adderall, Ritalin) at least once a week or more often?"))

(def taps2-stimulant-q3 (yesno
                :taps2-stimulant-q3
                :required true
                :label "In the past 3 months, has anyone expressed concern about your use of a medication for ADHD (for example, Adderall or Ritalin)?"))

(deflow taps2-stimulant
  "TAPS-2 stimulant questions.

  Returns
  {
    :responses      map between question id and answer
    :score          integer score
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "Stimulant TAPS-2")
  (let [q1-result (form-value (<*form [taps2-stimulant-q1]))
        responses (conj (if q1-result (<*form [taps2-stimulant-q2 taps2-stimulant-q3]) {})  [:taps2-stimulant-q1 q1-result])
        score (->> responses
                   (vals)
                   (map #(if % 1 0))
                   (reduce +))
        results {:responses responses
                 :score     score}]
    (set-index! [:drugs :prescription :taps2-stimulant] results)
    results))

(deflow prescription-medication
  "Patient prescription medication screening.

  Returns
  {
    :decision       decision value
    :screening      output of screening
    :taps2          output of taps-2
  }"
  [patient]
  (require-roles :patient)
  (set-index! :patient-id (:id patient) :title "Prescription Medication Screen")
  (let [sex (:sex patient)
        screening-result (initial-screen patient)
        days-per-week (:days-per-week screening-result)
        drinks-per-day (:drinks-per-day screening-result)
        taps2-result (when (and (>= days-per-week 1)
                                (>= drinks-per-day (case sex
                                                     :male 3
                                                     :female 2)))
                       (taps2 patient :skip-q1 true))
        decision (cond
                   (= days-per-week 0) (dsc 1 nil "No alcohol consumption")
                   (<= drinks-per-day (case sex :male 2 :female 1)) (dsc 1 nil "Low-risk alcohol consumption")
                   (not (nil? taps2-result)) (let [score (:score taps2-result)]
                                               (condp <= score
                                                 3 (dsc 3 score "High-risk alcohol consumption")
                                                 2 (dsc 2 score "Moderate-risk alcohol consumption")
                                                 0 (dsc 1 score "Low-risk alcohol consumption"))))
        result {:decision  decision
                :screening screening-result
                :taps2     taps2-result}]
    (set-index! [:drugs :alcohol :decision] decision)
    result)
  )