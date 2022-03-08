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
            [java-time :as jt]))

(def has-used (yesno
                :has-used
                :required true
                :label "Do you currently or have you ever smoked or used tobacco?"))

(deflow initial-screen
  "Asks if the patient has smoked or used tobacco before.

  Returns bool"
  [patient]
  (set-index! :patient-id (:id patient) :title "Tobacco Usage")
  (let [responses (<*form [has-used])
        result (form-value responses)]
    (set-index! [:drugs :tobacco :has-used] result)
    result))

(def tobacco-q1 (yesno
                  :tobacco-q1
                  :required true
                  :label "Do you currently smoke or use tobacco?"))

(defn tobacco-q2
  [currently?]
  {:pre (boolean? currently?)}
  (multiple-choice
    :tobacco-q2
    {:chewing-tobacco "Chewing tobacco"
     :pipe            "Pipe/cigar"
     :cigarettes      "Cigarettes"}
    :required true
    :multiselect true
    :label (str "Which of the following " (if currently? "do you use" "have you used") "?")))

(defn tobacco-q3
  [currently?]
  {:pre (boolean? currently?)}
  (number
    :tobacco-q3
    :units "packs"
    :min 0
    :required true
    :label (str "On average, how many packs per day " (if currently? "do you" "did you") " smoke?")))

(defn tobacco-q4
  [currently? packs]
  {:pre (boolean? currently?) (number? packs)}
  (number
    :tobacco-q4
    :units "years"
    :min 0
    :required true
    :label (str "For how many years " (if currently? "did" "have") " you " (if currently? "smoke" "smoked") packs " packs of cigarettes per day?")))

(def tobacco-q5 (dropdown
                  :tobacco-q5
                  (map #(- (jt/value (jt/year)) %1) (range 100))
                  :required true
                  :label "Congratulations on quitting! What year did you quit?"))

(deflow screening
  "Asks the patient some screening questions.

  Returns
  {
    :responses      map between question id and answer
    :pack-year      pack-year calculation
    :currently?     if the user currently uses tobacco products
    :cigarettes?    if the user smokes cigarettes
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "Tobacco Screen")
  (let [currently? (form-value (<*form [tobacco-q1]))
        types (form-value (<*form [(tobacco-q2 currently?)]))
        cigarettes? (contains? types :cigarettes)
        packs (when cigarettes? (form-value (<*form [(tobacco-q3 currently?)])))
        years (when cigarettes? (form-value (<*form [(tobacco-q4 currently? packs)])))
        pack-year (when cigarettes? (* packs years))
        quit-year (when (not currently?) (form-value (<*form [(tobacco-q5)])))
        current-year (jt/value (jt/year))
        less-than-one-year? (when quit-year (<= (- current-year quit-year) 1))
        long-history? (when quit-year (>= pack-year))
        result {:responses           {:tobacco-q1 currently?
                                      :tobacco-q2 types
                                      :tobacco-q3 packs
                                      :tobacco-q4 years
                                      :tobacco-q5 quit-year}
                :pack-year           pack-year
                :currently?          currently?
                :cigarettes?         cigarettes?
                :less-than-one-year? less-than-one-year?
                :long-history?       long-history?
                }]
    (set-index! [:drugs :tobacco :screening] result)
    result))

(def taps2-q1 (yesno
                :taps2-q1
                :required true
                :label "In the past 3 months, did you smoke a cigarette containing tobacco?"))

(def taps2-q2 (yesno
                :taps2-q2
                :required true
                :label "In the past 3 months, did you usually smoke more than 10 cigarettes each day?"))

(def taps2-q3 (yesno
                :taps2-q3
                :required true
                :label "In the past 3 months, did you usually smoke within 30 minutes after waking?"))

(deflow taps2
  "Asks the patient TAPS-2 tobacco questions.

  Returns
  {
    :responses      map between question id and answer
    :score          integer score
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "Tobacco TAPS-2")
  (let [responses (<*form [taps2-q1 taps2-q2 taps2-q3])
        score (->> responses
                   (vals)
                   (map #(if % 1 0))
                   (reduce +))
        result {:responses responses
                :score     score}]
    (set-index! [:drugs :tobacco :taps2] result)
    result))

(deflow tobacco
  "Patient tobacco screening.

  Returns
  {
    :decision       [1,5]
    :decision-info  description of decision
    :screening      output of screening
    :taps2          output of taps-2
  }"
  [patient]
  (require-roles :patient)
  (set-index! :patient-id (:id patient) :title "Tobacco Screen")
  (let [has-used? (initial-screen patient)
        screening-result (when has-used? (screening [patient]))
        taps2-result (when
                       (and (:currently? screening-result) (:cigarettes? screening-result))
                       (taps2 patient))
        decision (cond
                   (not has-used?) 1
                   (or (:long-history? screening-result) (:less-than-one-year? screening-result)) 3
                   taps2-result 3
                   :else 2)
        result {:decision  decision
                :screening screening-result
                :taps2     taps2-result}]
    (set-index! [:drugs :tobacco :decision] 0)
    result))