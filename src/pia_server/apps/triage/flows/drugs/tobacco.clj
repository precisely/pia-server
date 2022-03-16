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
  [is-currently]
  {:pre [(boolean? is-currently)]}
  (multiple-choice
    :tobacco-q2
    {:chewing-tobacco "Chewing tobacco"
     :pipe            "Pipe/cigar"
     :cigarettes      "Cigarettes"}
    :required true
    ;:multiselect true                                       ;TODO: add multiselect support
    :label (str "Which of the following " (if is-currently "do you use" "have you used") "?")))

(defn tobacco-q3
  [is-currently]
  {:pre [(boolean? is-currently)]}
  (number
    :tobacco-q3
    :units "packs"
    :min 0
    :required true
    :integer-only true
    :label (str "On average, how many packs per day " (if is-currently "do you" "did you") " smoke?")))

(defn tobacco-q4
  [is-currently packs]
  {:pre [(boolean? is-currently)
         (number? packs)]}
  (number
    :tobacco-q4
    :units "years"
    :min 0
    :required true
    :integer-only true
    :label (str "For how many years " (if is-currently "did" "have") " you " (if is-currently "smoke" "smoked") packs " packs of cigarettes per day?")))

(def tobacco-q5 (dropdown
                  :tobacco-q5
                  (doall (map #(- (jt/value (jt/year)) %) (range 100)))
                  :required true
                  :label "Congratulations on quitting! What year did you quit?"))

(deflow screening
  "Asks the patient some screening questions.

  Returns
  {
    :responses      map between question id and answer
    :pack-year      pack-year calculation
    :is-currently     if the user currently uses tobacco products
    :used-cigarettes    if the user smokes cigarettes
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "Tobacco Screen")
  (let [is-currently (form-value (<*form [tobacco-q1]))
        types (form-value (<*form [(tobacco-q2 is-currently)]))
        used-cigarettes (= :cigarettes types)               ;TODO: add multiselect support (contains? types :cigarettes)
        packs (when used-cigarettes (form-value (<*form [(tobacco-q3 is-currently)])))
        years (when used-cigarettes (form-value (<*form [(tobacco-q4 is-currently packs)])))
        pack-year (when used-cigarettes (* packs years))
        quit-year (when (not is-currently) (int (form-value (<*form [tobacco-q5]))))
        current-year (jt/value (jt/year))
        has-quit-recently (when quit-year (<= (- current-year quit-year) 1))
        has-long-history (when (and pack-year quit-year) (>= pack-year 20))
        result {:responses         {:tobacco-q1 is-currently
                                    :tobacco-q2 types
                                    :tobacco-q3 packs
                                    :tobacco-q4 years
                                    :tobacco-q5 quit-year}
                :pack-year         pack-year
                :is-currently      is-currently
                :used-cigarettes   used-cigarettes
                :has-quit-recently has-quit-recently
                :has-long-history  has-long-history
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
  (let [q1-result (form-value (<*form [taps2-q1]))
        _ (println q1-result)
        responses (conj (if q1-result (<*form [taps2-q2 taps2-q3]) {}) [:taps2-q1 q1-result])
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
    :decision       decision value
    :screening      output of screening
    :taps2          output of taps-2
  }"
  [patient]
  (require-roles :patient)
  (set-index! :patient-id (:id patient) :title "Tobacco Screen")
  (let [has-used? (initial-screen patient)
        screening-result (when has-used? (screening patient))
        is-currently (:is-currently screening-result)
        used-cigarettes (:used-cigarettes screening-result)
        has-quit-recently (:has-quit-recently screening-result)
        has-long-history (:has-long-history screening-result)
        taps2-result (when (and is-currently used-cigarettes) (taps2 patient))
        decision (cond
                   (not has-used?) (dsc 1 nil "Lifetime non-smoker")
                   (not is-currently) (if used-cigarettes (if (or has-long-history has-quit-recently) (dsc 3 nil "former smoker, moderate risk")
                                                                                                      (dsc 2 nil "former smoker, low risk"))
                                                          (dsc 2 nil "former non-cigarette tobacco user"))
                   (not used-cigarettes) (dsc 2 nil "non-cigarette tobacco user")
                   taps2-result (condp <= (:score taps2-result)
                                  2 (dsc 3 (:score taps2-result) "current smoker, high risk")
                                  0 (dsc 3 (:score taps2-result) "current smoker")))
        result {:decision  decision
                :screening screening-result
                :taps2     taps2-result}]
    (set-index! [:drugs :tobacco :decision] decision)
    result))