;;
;; Flows for patient tobacco screening
;;

(ns pia-server.apps.preop.eval.drugs.tobacco
  (:require [rapids :refer :all]
            [pia-server.common.docs :refer :all]
            [pia-server.apps.preop.eval.common :refer :all]
            [pia-server.apps.preop.common :refer :all]
            [java-time :as t]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.controls.basic :refer :all]))

(def taps2-schema [:map
                   [:timestamp number?]
                   [:score number?]
                   [:questions questions-schema]])

(def tobacco-schema [:map
                     [:timestamp number?]
                     [:decision decision-schema]
                     [:pack-year number?]
                     [:taps-2 taps2-schema]
                     [:questions questions-schema]
                     ])

(defn _get-score
  "Gets the TAPS-2 score from a list of questions"
  [responses]
  {:pre (map? responses)}
  (->> responses
    (vals)
    (map #(if % 1 0))
    (reduce +)))

(def _taps2-q1 (yesno
                 :taps2-q1
                 :required true
                 :label "In the PAST 3 MONTHS, did you smoke a cigarette containing tobacco?"))

(def _taps2-q2 (yesno
                 :taps2-q2
                 :required true
                 :label "In the PAST 3 MONTHS, did you usually smoke more than 10 cigarettes each day?"))

(def _taps2-q3 (yesno
                 :taps2-q3
                 :required true
                 :label "In the PAST 3 MONTHS, did you usually smoke within 30 minutes after waking?"))

(deflow _obtain-taps2
  "TAPS-2 Tobacco"
  [doc]
  (module doc :taps-2
    `(do (require-roles :patient)
       (let [q1        (<*ask doc [:tobacco :taps-2] [_taps2-q1])
             responses (when q1 (<*ask doc [:tobacco :taps-2] [_taps2-q2 _taps2-q3]))
             score     (_get-score (merge {(:id _taps2-q1) q1} responses))]
         {:score score}))
    :interval (t/months 3)))

(def _q1 (yesno
           :q1
           :required true
           :label "Do you currently or have you ever smoked or used tobacco?"))

(def _q2 (yesno
           :q2
           :required true
           :label "Do you currently smoke or use tobacco?"))

(def _q3 (dropdown
           :q3
           (doall (map #(- (t/value (t/year)) %) (range 100)))
           :required true
           :label "Congratulations on quitting! What year did you quit?"))

(defn _q4
  [is-currently]
  {:pre [(boolean? is-currently)]}
  (multiselect
    :q4
    {:chewing-tobacco "Chewing tobacco"
     :pipe            "Pipe/cigar"
     :cigarettes      "Cigarettes"}
    :required true
    :label (str "Which of the following " (if is-currently "do you use" "have you used") "?")))

(defn _q5
  [is-currently]
  {:pre [(boolean? is-currently)]}
  (number
    :q5
    :units "packs"
    :min 0
    :required true
    :integer-only true
    :label (str "On average, how many packs per day " (if is-currently "do you" "did you") " smoke?")))

(defn _q6
  [is-currently packs]
  {:pre [(boolean? is-currently)
         (number? packs)]}
  (number
    :q6
    :units "years"
    :min 0
    :required true
    :integer-only true
    :label (str
             "For how many years "
             (if is-currently "did" "have")
             " you "
             (if is-currently "smoke" "smoked")
             packs
             " packs of cigarettes per day?")))

;;
;;  Clinic Flows
;;

(deflow clinic-pulmonary-risk
  "Clinical pulmonary risk assessment"
  [patient-id]
  (require-roles :clinic))

(deflow clinic-substance-use-disorder
  "Clinical substance use disorder assessment"
  [patient-id]
  (require-roles :clinic))

;;
;;  Main flow
;;
(deflow obtain-tobacco
  "Patient tobacco screening"
  [patient-id]
  (let [doc (retrieve-patient-doc :preop-drugs-tobacco patient-id :schema tobacco-schema)]
    (module doc [:tobacco]
      `(do (require-roles :patient)
         (set-index! :title "Tobacco Screen")
         (let [used-tobacco (<*ask doc [:tobacco] [_q1])]
           {:decision (if (not used-tobacco)
                        (dsn 6 :desc "Lifetime non-smoker")
                        (let [is-currently    (<*ask doc [:tobacco] [_q2])
                              quit-year       (when (not is-currently) (<*ask doc [:tobacco] [_q3]))
                              tobacco-types   (<*ask doc [:tobacco] [(_q4 is-currently)])
                              used-cigarettes (contains? tobacco-types :cigarettes)
                              pack-years      (when used-cigarettes
                                                (let [packs-per-day (<*ask doc [:tobacco] [(_q5 is-currently)])
                                                      years-smoked  (<*ask doc [:tobacco] [(_q6 is-currently packs-per-day)])]
                                                  (* packs-per-day years-smoked)))]
                          (if is-currently
                            (if used-cigarettes
                              (let [taps2-score (:score (_obtain-taps2 doc))]
                                (condp <= taps2-score
                                  2 (do (start! clinic-pulmonary-risk [~patient-id])
                                      (start! clinic-substance-use-disorder [~patient-id])
                                      (dsn 3 :desc "Current smoker (high risk)"))
                                  0 (do (start! clinic-pulmonary-risk [~patient-id])
                                      (dsn 3 :desc "Current smoker"))))
                              (dsn 2 :desc "Non-cigarette tobacco user"))
                            (if used-cigarettes
                              (if (or (>= pack-years 20) (<= quit-year 1))
                                (do (start! clinic-pulmonary-risk [~patient-id])
                                  (dsn 3 :desc "Former smoker (moderate risk)" :comments "Recommend clinical evaluation"))
                                (dsn 2 :desc "Former smoker (low risk)" :comments "No follow-up required"))
                              (dsn 2 :desc "Former non-cigarette tobacco user")))))}))
      :interval (t/months 3))))

