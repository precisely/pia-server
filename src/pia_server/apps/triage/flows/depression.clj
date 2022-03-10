;;
;; Flows for patient depression screening
;;
(ns pia-server.apps.triage.flows.depression
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer :all]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.notifier :refer :all]
            [pia-server.common.util :refer [range-case round-to-nearest-half]]
            [pia-server.db.models.patient :as p]
            [pia-server.apps.triage.flows.common :refer :all]))

(def freq-responses {:not-at-all     "Not at all"
                     :several        "Several days"
                     :more-than-half "More than half the days"
                     :nearly-every   "Nearly every day"})

(defn freq-to-score
  "Converts frequency response value to integer score"
  [freq]
  (case freq
    :not-at-all 0
    :several 1
    :more-than-half 2
    :nearly-every 3))

(deflow get-score
  "Gets the depression score from a list of questions"
  [responses]
  {:pre (map? responses)}
  (->> responses
       (vals)
       (map freq-to-score)
       (reduce +)))

(def phq2-q1_ (multiple-choice
               :phq2-q1
               freq-responses
               :required true
               :label "Little interest or pleasure in doing things"))

(def phq2-q2_ (multiple-choice
               :phq2-q2
               freq-responses
               :required true
               :label "Feeling down, depressed, or hopeless"))

(deflow phq2_
  "PHQ-2 form questionaire.

  Returns
  {
    :eval       :positive | :negative
    :score      integer
    :responses  map between question id and answer
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "PHQ-2")
  (>* (text "Over the last 2 weeks, how often have you been bothered by any of the following problems?"))
  (let [responses (<*form [phq2-q1_ phq2-q2_])
        score (get-score responses)
        result
        {:eval      (if (>= score 3) :positive :negative)
         :score     score
         :responses responses}]
    (set-index! [:depression :phq2] result)
    result))

(def phq9-q3_ (multiple-choice
               :phq9-q3
               freq-responses
               :required true
               :label "Trouble falling or staying asleep, or sleeping too much"))

(def phq9-q4_ (multiple-choice
               :phq9-q4
               freq-responses
               :required true
               :label "Feeling tired or having little energy"))

(def phq9-q5_ (multiple-choice
               :phq9-q5
               freq-responses
               :required true
               :label "Poor appetite or overeating"))

(def phq9-q6_ (multiple-choice
               :phq9-q6
               freq-responses
               :required true
               :label "Feeling bad about yourself - or that you are a failure or have let yourself or your family down"))

(def phq9-q7_ (multiple-choice
               :phq9-q7
               freq-responses
               :required true
               :label "Trouble concentrating on things, such as reading the newspaper or watching television"))

(def phq9-q8_ (multiple-choice
               :phq9-q8
               freq-responses
               :required true
               :label "Moving or speaking so slowly that other people could have noticed. Or the opposite being so figety or restless that you have been moving around a lot more than usual."))

(def phq9-q9_ (multiple-choice
               :phq9-q9
               freq-responses
               :required true
               :label "Thoughts that you would be better off dead, or of hurting yourself"))

(def phq9-q10_ (multiple-choice
                :phq9-q10
                {:not-difficult       "Not difficult at all"
                 :somewhat-difficult  "Somewhat difficult"
                 :very-difficult      "Very difficult"
                 :extremely-difficult "Extremely difficult"}
                :required true
                :label "How difficult have these problems made it for you to do your work, take care of things at home, or get along with other people?"))

(deflow phq9_
  "PHQ-9 form questionaire. Only called if PHQ-2 returns positive.

  Returns
  {
    :eval           :positive | :negative
    :score          integer
    :difficulty     :not-difficult | :somewhat-difficult | :very-difficult | :extremely-difficult
    :responses      map between question id and answer
  }"
  [patient score]
  {:pre (integer? score)}
  (set-index! :patient-id (:id patient) :title "PHQ-9")
  ; Should already be prompted in phq2, ask again
  (>* (text "Over the last 2 weeks, how often have you been bothered by any of the following problems?"))
  (let [responses (<*form [phq9-q3_ phq9-q4_ phq9-q5_ phq9-q6_ phq9-q7_ phq9-q8_ phq9-q9_])
        final-score (+ score (get-score responses))
        result
        {:eval       :positive
         :score      final-score
         :difficulty (do
                       (>* (text "Thanks from answering all these questions! Just one last question in this section"))
                       (form-value (<*form [phq9-q10_])))
         :responses  responses}]
    (set-index! [:depression :phq9] result)
    result))

(deflow depression
  "Patient depression screening.

  Returns
  {
    :score          integer score
    :severity       nil | :minimal | :mild | :moderate | :moderately-severe | :severe
    :decision       [1,5]
    :phq2          output of phq-2
    :phq9          output of phq-9
  }"
  [patient]
  (require-roles :patient)
  (set-index! :patient-id (:id patient) :title "Depression Screen")
  (let [phq2-result (phq2_ patient)
        eval (= (:eval phq2-result) :positive)
        phq9-result (when eval (phq9_ patient (:score phq2-result)))
        score (if eval (:score phq9-result) (:score phq2-result))
        severity (condp <= score
                   20 :severe
                   15 :moderately-severe
                   10 :moderate
                   5 :mild
                   1 :minimal
                   0 nil)
        decision (condp <= score
                   10 3
                   3 2
                   0 1)
        result {:score    score
                :severity severity
                :decision decision
                :phq2     phq2-result
                :phq9     phq9-result}]
    (set-index! [:depression :score] score
                [:depression :severity] severity
                [:depression :decision] decision)
    result))