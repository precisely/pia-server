;;
;; Flows for patient frailty evaluation
;;
(ns pia-server.apps.triage.flows.frailty
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer :all]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.notifier :refer :all]
            [pia-server.common.util :refer [range-case round-to-nearest-half]]
            [pia-server.db.models.patient :as p]
            [pia-server.apps.triage.flows.common :refer :all]))

(def self-rated-q1_ (multiple-choice
                     :self-rated-q1
                     {:poor      "Poor"
                      :fair      "Fair"
                      :good      "Good"
                      :very-good "Very Good"
                      :excellent "Excellent"}
                     :required true
                     :label "In general, how would you describe your health?"))

(def self-rated-q2_ (multiple-choice
                     :self-rated-q2
                     {:rarely       "Rarely (Less than 1 day)"
                      :sometimes    "Some of the time (1-2 days)"
                      :occasionally "Occasionally (3-4 days)"
                      :always       "All of the time (5-7 days)"}
                     :required true
                     :label "In a typical week, how often would you say that everything you do is an effort?"))

(deflow self-rated-questions
  "Frailty self rated questions.

  Returns
  {
    :responses      map between question id and answer
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "Self-rated Health Screening Questions")
  (let [result {:responses (<*form [self-rated-q1_ self-rated-q2_])}]
    (set-index! [:frailty :self-rated] result)
    result))

(defn cfs-template
  "Clinical Frailty Scale activities of daily living question format"
  [phrase]
  {:pre (string? phrase)}
  (str "How easily are you able to " phrase " on your own?"))

(def cfs-responses {:without-help "I'm able to do this on my own without help"
                    :needs-help   "I need help from another person"})

(deflow get-score
  "Gets the frailty score from a list of questions"
  [responses]
  {:pre (map? responses)}
  (->>
    responses
    (vals)
    (map #(case %
            :without-help 0
            :needs-help 1))
    (reduce +)))

(def badl-q1_ (multiple-choice
               :badl-q1
               cfs-responses
               :required true
               :label (cfs-template "dress and undress yourself (including putting on socks and shoes)")))

(def badl-q2_ (multiple-choice
               :badl-q2
               cfs-responses
               :required true
               :label (cfs-template "eat")))

(def badl-q3_ (multiple-choice
               :badl-q3
               cfs-responses
               :required true
               :label (cfs-template "walk")))

(def badl-q4_ (multiple-choice
               :badl-q4
               cfs-responses
               :required true
               :label (cfs-template "get in and out of bed")))

(def badl-q5_ (multiple-choice
               :badl-q5
               cfs-responses
               :required true
               :label (cfs-template "take a bath or a shower")))

(deflow badl-questions
  "CFS basic activities of daily living questions.

  Returns
  {
    :score          integer score
    :responses      map between question id and answer
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "CFS BADL Questions")
  (let [responses (<*form [badl-q1_ badl-q2_ badl-q3_ badl-q4_ badl-q5_])
        score (get-score responses)
        result {:score     score
                :responses responses}]
    (set-index! [:frailty :badl] result)
    result))

(def iadl-q1_ (multiple-choice
               :iadl-q1
               cfs-responses
               :required true
               :label (cfs-template "use the telephone (including looking up numbers and dialing)")))

(def iadl-q2_ (multiple-choice
               :iadl-q2
               cfs-responses
               :required true
               :label (cfs-template "go shopping for groceries or clothes")))

(def iadl-q3_ (multiple-choice
               :iadl-q3
               cfs-responses
               :required true
               :label (cfs-template "prepare your own meals (including planning and cooking full meals)")))

(def iadl-q4_ (multiple-choice
               :iadl-q4
               cfs-responses
               :required true
               :label (cfs-template "do your household chores")))

(def iadl-q5_ (multiple-choice
               :iadl-q5
               cfs-responses
               :required true
               :label (cfs-template "take your own medicine (including preparing it and taking the right dose at the right time)")))

(def iadl-q6_ (multiple-choice
               :iadl-q6
               cfs-responses
               :required true
               :label (cfs-template "handle your own money (including paying bills)")))

(deflow iadl-questions
  "CFS instrumental activities of daily living questions.

  Returns
  {
    :score          integer score
    :responses      map between question id and answer
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "CFS IADL Questions")
  (let [responses (<*form [iadl-q1_ iadl-q2_ iadl-q3_ iadl-q4_ iadl-q5_ iadl-q6_])
        score (get-score responses)
        result {:score     score
                :responses responses}]
    (set-index! [:frailty :iadl] result)
    result))

(defn low-frailty-risk
  "Scoring for low-frailty risk patients.

  Returns
  {
    :cfs            [1,4]
  }"
  [chronic-conditions health frequency has-strenuous-activity]
  (if (>= chronic-conditions 10)
    4
    (case health
      (:poor :fair) 4
      (:very-good :good) (case frequency
                           :always 4
                           (:rarely :sometimes :occasionally) (if has-strenuous-activity 2 3))
      (:excellent) (case frequency
                     :always 4
                     (:sometimes :occasionally) (if has-strenuous-activity 2 3)
                     :rarely (if has-strenuous-activity 1 2)))))

(deflow frailty
  "Frailty assessment.

  Returns
  {
    :cfs            nil | [1,7]
    :decision       [0,5]
    :self-rated     output of self-rated questions
    :badl           output of badl questions
    :iadl           output of iadl questions
  }"
  [patient]
  (require-roles :patient)
  (set-index! :patient-id (:id patient) :title "Frailty Assessment")
  (let [chronic-conditions 0                                ;TODO: add answer
        chronic-medications 0                               ;TODO: add answer
        age 0                                               ;TODO: add answer
        has-strenuous-activity false                       ;TODO: add answer
        self-rated-result (self-rated-questions patient)
        health (:self-rated-q1 (:responses self-rated-result))
        frequency (:self-rated-q2 (:responses self-rated-result))
        need-full-screening? (or (>= chronic-conditions 2)
                                 (>= chronic-medications 5)
                                 (>= age 65)
                                 (contains? #{:poor :fair} health)
                                 (= frequency :always))
        badl-result (when need-full-screening? (badl-questions patient))
        badl-score (:score badl-result)
        iadl-result (when (= badl-score 0) (iadl-questions patient))
        iadl-score (:score iadl-result)
        cfs (cond
              (not need-full-screening?) nil
              (>= badl-score 3) 7
              (>= badl-score 1) 6
              (>= iadl-score 5) 6
              (>= iadl-score 1) 5
              :else (low-frailty-risk chronic-conditions health frequency has-strenuous-activity))
        decision (condp <= (or cfs 0)
                   5 3
                   4 2
                   1 1
                   0 0)
        result {:cfs        cfs
                :decision   decision
                :self-rated self-rated-result
                :badl       badl-result
                :iadl       iadl-result}]
    (set-index! [:frailty :cfs] cfs
                [:frailty :decision] decision)
    result))