;;
;; Flows for patient depression screening
;;
(ns pia-server.apps.triage.flows.depression_screening
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer :all]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.notifier :refer :all]
            [pia-server.common.util :refer [range-case round-to-nearest-half]]
            [pia-server.db.models.patient :as p]))

(def freq-responses [:not-at-all "Not at all"
                     :several "Several days"
                     :more-than-half "More than half the days"
                     :nearly-every "Nearly every day"])

(defn freq-to-score
  "Converts frequency response value to integer score"
  [freq]
  (case freq
    :not-at-all 0
    :several 1
    :more-than-half 2
    :nearly-every 3))

(deflow fmap [f s]
  (if-not (empty? s)
    (loop [[head & rest] s
           results []]
      (let [results (conj results (fcall f head))]
        (if (empty? rest) results
                          (recur rest results))))))

(defn get-score
  "Gets the depression score from a list of questions"
  [ctrl-groups]
  {:pre (list? ctrl-groups)}
  (->>
    (fmap (flow [ctrl-group] (<*form [ctrl-group])) ctrl-groups)
    (map freq-to-score)
    (reduce +))
  )

(def phq2-q1 (multiple-choice
               :phq2-q1
               freq-responses
               :required true
               :label "Little interest or pleasure in doing things"))

(def phq2-q2 (multiple-choice
               :phq-q2
               freq-responses
               :required true
               :label "Feeling down, depressed, or hopeless"))

(deflow phq2
  "PHQ-2 form questionaire.

  Returns
  {
    :eval       :positive | :negative
    :score      integer
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "PHQ-2")
  (>* (text "Over the last 2 weeks, how often have you been bothered by any of the following problems?"))
  (let [score (get-score [phq2-q1 phq2-q2])]
    {
     :eval  (if (<= score 3) :positive :negative)
     :score score
     }))

(def phq9-q3 (multiple-choice
               :phq9-q3
               freq-responses
               :required true
               :label "Trouble falling or staying asleep, or sleeping too much"))

(def phq9-q4 (multiple-choice
               :phq9-q4
               freq-responses
               :required true
               :label "Feeling tired or having little energy"))

(def phq9-q5 (multiple-choice
               :phq9-q5
               freq-responses
               :required true
               :label "Poor appetite or overeating"))

(def phq9-q6 (multiple-choice
               :phq9-q6
               freq-responses
               :required true
               :label "Feeling bad about yourself - or that you are a failure or have let yourself or your family down"))

(def phq9-q7 (multiple-choice
               :phq9-q7
               freq-responses
               :required true
               :label "Trouble concentrating on things, such as reading the newspaper or watching television"))

(def phq9-q8 (multiple-choice
               :phq9-q8
               freq-responses
               :required true
               :label "Moving or speaking so slowly that other people could have noticed. Or the opposite being so figety or restless that you have been moving around a lot more than usual."))

(def phq9-q9 (multiple-choice
               :phq9-q9
               freq-responses
               :required true
               :label "Thoughts that you would be better off dead, or of hurting yourself"))

(def phq9-q10 (multiple-choice
                :phq9-q10
                {:not-difficult      "Not difficult at all"
                 :somewhat-difficult "Somewhat difficult"
                 :very-difficult     "Very difficult"
                 :extremely-diffiult "Extremely difficult"}
                :required true
                :label "How difficult have these problems made it for you to do your work, take care of things at home, or get along with other people?"))

(deflow phq9
  "PHQ-9 form questionaire. Only called if PHQ-2 returns positive.

  Returns
  {
    :eval           :positive | :negative
    :score          integer
    :severity       :minimal | :mild | :moderate | :moderately-severe | :severe
  }"
  [patient score]
  {:pre (integer? score)}
  (set-index! :patient-id (:id patient) :title "PHQ-9")
  ; Should already be prompted in phq2
  ;(>* (text "Over the last 2 weeks, how often have you been bothered by any of the following problems?"))
  (let [final-score (+ score (get-score [phq9-q3 phq9-q4 phq9-q5 phq9-q6 phq9-q7 phq9-q8 phq9-q9]))]
    (if (> final-score 0) ((>* (text "Thanks from answering all these questions! Just one last question in this section"))
                           (<*form [phq9-q10])))
    {
     :eval     :positive
     :score    final-score
     :severity (condp > final-score
                 20 :severe
                 15 :moderately-severe
                 10 :moderate
                 5 :mild
                 1 :minimal)
     }
    ))

(deflow depression-screen
  "Patient depression screening.

  Returns
  {
    :eval       :positive | :negative
    :score      integer sum of all questionaire values
    :severity   :minimal | :mild | :moderate | :moderately-severe | :severe
  }"
  [patient]
  (require-roles :patient)
  (set-index! :patient-id (:id patient) :title "Depression Screen")
  (let [result
        (let [phq2-result (phq2 patient)]
          (case (:eval phq2-result)
            :negative phq2-result
            :positive (phq9 patient (:score phq2-result))
            )
          )]
    (if (>= (:score result) 10) ())                         ;Escalate to clinical evaluation - alert clinic
    (set-index! :depression-screen result)
    result
    )
  )
