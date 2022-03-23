;;
;; Flows for patient alcohol screening
;;
(ns pia-server.apps.triage.flows.drugs.alcohol
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer :all]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.notifier :refer :all]
            [pia-server.common.util :refer [range-case round-to-nearest-half]]
            [pia-server.db.models.patient :as p]
            [pia-server.apps.triage.flows.common :refer :all]))

(def initial-q1 (scale
                  :initial-q1
                  :min 0
                  :max 7
                  :required true
                  :label "On average, how many days a week do you consume an alcoholic beverage (including wine, beer, and hard liquor)?"))

(def initial-q2 (number
                  :initial-q2
                  :min 1
                  :max 100
                  :required true
                  :label "How many drinks do you usually have on days when you drink?"))

(deflow initial-screen
  "Asks if the patient drinks alcohol weekly.

  Returns
  {
    :responses          map between question id and answer
    :days-per-week      number of days a week
    :drinks-per-day     number of drinks per day
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "Alcohol Consumption")
  (let [days-per-week (form-value (<*form [initial-q1]))
        drinks-per-day (when (>= days-per-week 1) (form-value (<*form [initial-q2])))
        result {:responses      {:initial-q1 days-per-week
                                 :initial-q2 drinks-per-day}
                :days-per-week  days-per-week
                :drinks-per-day drinks-per-day}]
    (set-index! [:drugs :alcohol :screening] result)
    result))

(def taps2-q1 (yesno
                :taps2-q1
                :required true
                :label "In the PAST 3 MONTHS, did you have a drink containing alcohol?"))

(defn taps2-q2
  [sex]
  {:pre [(contains? #{:male :female} sex)]}
  (yesno
    :taps2-q2
    :required true
    :label (str "In the PAST 3 MONTHS, did you have " (case sex
                                                        :male 5
                                                        :female 4) " or more drinks containing alcohol in a day?")))

(def taps2-q3 (yesno
                :taps2-q3
                :required true
                :label "In the PAST 3 MONTHS, have you tried and failed to control, cut down or stop drinking?"))

(def taps2-q4 (yesno
                :taps2-q4
                :required true
                :label "In the PAST 3 MONTHS, has anyone expressed concern about your drinking?"))

(deflow taps2
  "Asks the patient TAPS-2 alcohol questions.

  Returns
  {
    :responses      map between question id and answer
    :score          integer score
  }"
  [patient & {:keys [skip-q1]
              :or   {skip-q1 false}}]
  {:pre [(boolean? skip-q1)]}
  (set-index! :patient-id (:id patient) :title "Alcohol TAPS-2")
  (let [sex (:sex patient)
        q1-result (if skip-q1 true (form-value (<*form [taps2-q1])))
        responses (conj (if q1-result (<*form [(taps2-q2 sex) taps2-q3 taps2-q4]) {}) [:taps2-q1 q1-result])
        score (->> responses
                   (vals)
                   (map #(if % 1 0))
                   (reduce +))
        result {:responses responses
                :score     score}]
    (set-index! [:drugs :alcohol :taps2] result)
    result))

(deflow alcohol
  "Patient alcohol screening.

  Returns
  {
    :decision       decision value
    :screening      output of screening
    :taps2          output of taps-2
  }"
  [patient]
  (require-roles :patient)
  (set-index! :patient-id (:id patient) :title "Alcohol Screen")
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