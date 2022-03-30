;;
;; Flows for patient depression screening
;;

(ns pia-server.apps.preop.eval.depression
  (:require [rapids :refer :all]
            [rapids.active-doc :as adoc]
            [pia-server.common.docs :refer :all]
            [pia-server.apps.preop.eval.common :refer :all]
            [pia-server.apps.preop.common :refer :all]
            [java-time :as t]
            [malli.core :as m]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.controls.basic :refer :all]))

(def phq2-schema [:map
                  [:timestamp number?]
                  [:eval [:enum :positive :negative]]
                  [:score number?]
                  [:questions questions-schema]])

(def phq9-schema [:map
                  [:timestamp number?]
                  [:eval [:enum :positive :negative]]
                  [:score number?]
                  [:difficulty [:enum :none :some :very :extremely]]
                  [:questions questions-schema]])

(def depression-schema [:map
                        [:timestamp number?]
                        [:decision decision-schema]
                        [:phq-2 phq2-schema]
                        [:phq-9 phq9-schema]
                        ])

(def _freq-responses {:not-at-all     "Not at all"
                      :several        "Several days"
                      :more-than-half "More than half the days"
                      :nearly-every   "Nearly every day"})

(defn _freq-to-score
  "Converts frequency response value to integer score"
  [freq]
  (case freq
    :not-at-all 0
    :several 1
    :more-than-half 2
    :nearly-every 3))

(deflow _get-score
  "Gets the depression score from a list of questions"
  [responses]
  {:pre (map? responses)}
  (->> responses
       (vals)
       (map #(_freq-to-score (:result %)))
       (reduce +)))


;;
;;  PHQ-2
;;

(def _phq2-q1 (multiple-choice
                :phq2-q1
                _freq-responses
                :required true
                :label "Little interest or pleasure in doing things"))

(def _phq2-q2 (multiple-choice
                :phq2-q2
                _freq-responses
                :required true
                :label "Feeling down, depressed, or hopeless"))

(deflow _obtain-phq2
  "PHQ-2"
  [doc]
  (module doc :phq-2
          `(do (require-roles :patient)
               (>* (text "Over the last 2 weeks, how often have you been bothered by any of the following problems?"))
               (let [responses (get-responses doc :phq-2 [_phq2-q1 _phq2-q2])
                     score (_get-score responses)]
                 {:eval  (if (< score 3) :negative :positive)
                  :score score}))
          :interval (t/weeks 2)))

;;
;;  PHQ-9
;;

(def _phq9-q3 (multiple-choice
                :phq9-q3
                _freq-responses
                :required true
                :label "Trouble falling or staying asleep, or sleeping too much"))

(def _phq9-q4 (multiple-choice
                :phq9-q4
                _freq-responses
                :required true
                :label "Feeling tired or having little energy"))

(def _phq9-q5 (multiple-choice
                :phq9-q5
                _freq-responses
                :required true
                :label "Poor appetite or overeating"))

(def _phq9-q6 (multiple-choice
                :phq9-q6
                _freq-responses
                :required true
                :label "Feeling bad about yourself - or that you are a failure or have let yourself or your family down"))

(def _phq9-q7 (multiple-choice
                :phq9-q7
                _freq-responses
                :required true
                :label "Trouble concentrating on things, such as reading the newspaper or watching television"))

(def _phq9-q8 (multiple-choice
                :phq9-q8
                _freq-responses
                :required true
                :label "Moving or speaking so slowly that other people could have noticed. Or the opposite being so figety or restless that you have been moving around a lot more than usual."))

(def _phq9-q9 (multiple-choice
                :phq9-q9
                _freq-responses
                :required true
                :label "Thoughts that you would be better off dead, or of hurting yourself"))

(def _phq9-q10 (multiple-choice
                 :phq9-q10
                 {:none      "Not difficult at all"
                  :some      "Somewhat difficult"
                  :very      "Very difficult"
                  :extremely "Extremely difficult"}
                 :required true
                 :label "How difficult have these problems made it for you to do your work, take care of things at home, or get along with other people?"))

(deflow _obtain-phq9
  "PHQ-9"
  [doc]
  (module doc :phq-9
          `(do (require-roles :patient)
               (let [phq2 (_obtain-phq2 doc)
                     responses (get-responses doc :phq-9 [_phq9-q3 _phq9-q4 _phq9-q5 _phq9-q6 _phq9-q7 _phq9-q8 _phq9-q9])
                     difficulty (get-responses doc :phq9 [_phq9-q10])
                     score (+ (:score phq2) (_get-score responses))]
                 {:eval       (if (< score 3) :negative :positive)
                  :score      score
                  :difficulty difficulty}))
          :interval (t/weeks 2)))

;;
;;  Clinic Flows
;;

(deflow clinic-suicide
  "Clinical suicide assessment"
  [patient-id]
  (require-roles :clinic))

(deflow clinic-depression
  "Clinical suicide assessment"
  [patient-id]
  (require-roles :clinic))

;;
;;  Main flow
;;
(deflow obtain-depression
  "Patient depression screening"
  [patient-id]
  (let [doc (retrieve-patient-doc :preop-depression patient-id :schema depression-schema)]
    (module doc nil
            `(do (require-roles :patient)
                 (set-index! :title "Depression Screen")
                 (let [phq2-result (_obtain-phq2 doc)
                       phq9-result (when (= (:eval phq2-result) :positive) (_obtain-phq9 doc))
                       score (or (:score phq9-result) (:score phq2-result))]
                   {:decision (cond
                                (-> phq9-result :questions :phq-q9 :result) (do (start! clinic-suicide [patient-id])
                                                                                (start! clinic-depression [patient-id])
                                                                                (dsn 4 :score score :desc "Positive suicide risk screening"))
                                :default (condp <= score
                                           20 (do
                                                (start! clinic-depression [patient-id])
                                                (dsn 4 :score score :desc "Severe depression" :comments "Expedited referral to mental health specialist"))
                                           15 (do
                                                (start! clinic-depression [patient-id])
                                                (dsn 3 :score score :desc "Moderately severe depression" :comments "Clinical follow-up recommended"))
                                           10 (do
                                                (start! clinic-depression [patient-id])
                                                (dsn 3 :score score :desc "Moderate depression" :comments "Clinical follow-up recommended"))
                                           5 (dsn 2 :score score :desc "Mild depression" :comments "Clinical follow-up not indicated")
                                           3 (dsn 2 :score score :desc "Minimal depression" :comments "Clinical follow-up not indicated")
                                           0 (dsn 1 :score score :desc "Negative PHQ-2 screen")))}))
            :interval (t/weeks 2))))

