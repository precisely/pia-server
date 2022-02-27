;;
;; Flows for patient frailty evaluation
;;
(ns pia-server.apps.triage.flows.frailty-evaluation
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer :all]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.notifier :refer :all]
            [pia-server.common.util :refer [range-case round-to-nearest-half]]
            [pia-server.db.models.patient :as p]
            [pia-server.apps.triage.flows.common :refer :all]))

(def self-rated-q1 (multiple-choice
                     :self-rated-q1
                     {:poor      "Poor"
                      :fair      "Fair"
                      :good      "Good"
                      :very-good "Very Good"
                      :excellent "Excellent"
                      }
                     :required true
                     :label "In general, how would you describe your health?"))

(def self-rated-q2 (multiple-choice
                     :self-rated-q2
                     {:rarely       "Rarely (Less than 1 day)"
                      :sometimes    "Some of the time (1-2 days)"
                      :occasionally "Occasionally (3-4 days)"
                      :always       "All of the time (5-7 days)"
                      }
                     :required true
                     :label "In a typical week, how often would you say that everything you do is an effort?"))

(deflow self-rated-questions
  "Frailty self rated questions.

  Returns
  {
    :health     :poor | :fair | :good | :very-good | :excellent
    :frequency  :rarely | :sometimes | :occasionally | :always
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "Self-rated Health Screening Questions")
  {
   :health    (form-value (<*form [self-rated-q1]))
   :frequency (form-value (<*form [self-rated-q2]))
   })

(defn cfs-template
  "Clinical Frailty Scale activities of daily living question format"
  [phrase]
  {:pre (string? phrase)}
  (str "How easily are you able to " phrase " on your own?"))

(def cfs-responses {
                    :without-help "I'm able to do this on my own without help"
                    :needs-help   "I need help from another person"
                    })

(def badl-q1 (multiple-choice
               :badl-q1
               cfs-responses
               :required true
               :label (cfs-template "dress and undress yourself (including putting on socks and shoes)")))

(def badl-q2 (multiple-choice
               :badl-q2
               cfs-responses
               :required true
               :label (cfs-template "eat")))

(def badl-q3 (multiple-choice
               :badl-q3
               cfs-responses
               :required true
               :label (cfs-template "walk")))

(def badl-q4 (multiple-choice
               :badl-q4
               cfs-responses
               :required true
               :label (cfs-template "get in and out of bed")))

(def badl-q5 (multiple-choice
               :badl-q5
               cfs-responses
               :required true
               :label (cfs-template "take a bath or a shower")))

(deflow badl-questions
  "CFS basic activities of daily living questions.

  Returns
  {
    :score    integer score
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "CFS BADL Questions")
  )

(def iadl-q1 (multiple-choice
               :iadl-q1
               cfs-responses
               :required true
               :label (cfs-template "use the telephone (including looking up numbers and dialing)")))

(def iadl-q2 (multiple-choice
               :iadl-q2
               cfs-responses
               :required true
               :label (cfs-template "go shopping for groceries or clothes")))

(def iadl-q3 (multiple-choice
               :iadl-q3
               cfs-responses
               :required true
               :label (cfs-template "prepare your own meals (including planning and cooking full meals)")))

(def iadl-q4 (multiple-choice
               :iadl-q4
               cfs-responses
               :required true
               :label (cfs-template "do your household chores")))

(def iadl-q5 (multiple-choice
               :iadl-q5
               cfs-responses
               :required true
               :label (cfs-template "take your own medicine (including preparing it and taking the right dose at the right time)")))

(def iadl-q6 (multiple-choice
               :iadl-q6
               cfs-responses
               :required true
               :label (cfs-template "handle your own money (including paying bills)")))

(deflow iadl-questions
  "CFS instrumental activities of daily living questions.

  Returns
  {
    :score    integer score
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "CFS IADL Questions")
  )

(deflow frailty-assessment
  "Frailty assessment.

  Returns
  {
    :cfs    :cfs-1 | :cfs-2 | :cfs-3 | :cfs-4 | :cfs-5 | :cfs-6 | :cfs-7
  }"
  [patient]
  (set-index! :patient-id (:id patient) :title "Frailty Assessment")
  
  )