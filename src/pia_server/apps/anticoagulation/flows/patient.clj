;;
;; Flows which the patient interacts with
;;
(ns pia-server.apps.anticoagulation.flows.patient
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer :all]
            [pia-server.common.controls.form :refer :all]
            [pia-server.common.roles :refer [require-roles]]
            [pia-server.common.notifier :refer :all]
            [pia-server.common.util :refer [range-case round-to-nearest-half]]
            [pia-server.db.models.patient :as p]))

(declare pills-from-dosage calculate-next-initiation-dose-ucsd warfarin-pill-colors)

(deflow confirm-pills-taken
  "Asks the patient whether they took their pills,
  Returns :yes | :pills-finished | :forgot"
  [patient day days dosage]
  {:pre  [(number? dosage)]
   :post [#{:pills-finished :forgot :yes} %]}
  (set-index! :patient-id (:id patient) :title "Confirm warfarin dose")
  (notify patient (str "Time to take your pills (" dosage " mg)"))
  (>* (text "It's day" day " of " days)
      (text "Time to take your dose of warfarin (" dosage " mg).")
      (text "Please confirm you took your dose."))
  (case (<*buttons {:yes     (str "Yes, I took " dosage " mg")
                    :problem "No, there was a problem"})
    :problem (do (>* (text "Sorry to hear that. What was the issue?")
                     (<*buttons {:pills-finished "Not enough pills left"
                                 :forgot         "I forgot"})))
    :yes))

(deflow measure-inr-level []
  (>* (text "Please use your INR test and record your INR level here (0-5)."))
  (:inr (<*form [(number :inr :min 0 :max 5)])))

;; Some ideas for warfarin dosing:
;; https://www.uwhealth.org/files/uwhealth/docs/pdf3/Warfarin_Dosing_Protocol.pdf
;; initiation: https://depts.washington.edu/anticoag/home/content/warfarin-initiation-dosing
;; maintenance: https://depts.washington.edu/anticoag/home/content/warfarin-maintenance-dosing-nomogram
;; https://health.ucsd.edu/for-health-care-professionals/anticoagulation-guidelines/warfarin/warfarin-initiation/Pages/default.aspx

(deflow post-measurement-follow-up [inr-levels]
  {:pre [(sequential? inr-levels) (every? number? inr-levels)]}
  (let [last-inr-level (last inr-levels)]
    {:post-measurement-follow-up
     (range-case last-inr-level
                 [< 1] (do (>* (text "Your INR level is very low, indicating your blood is clotting too much.")
                               (text "Vitamin K can interfere with your treatment.")
                               (text "Did you eat a large amount of any of the following in the last day?")
                               (text "Kale, Spinach, Brussels sprouts, Collards, Mustard greens, Chard, Broccoli, Asparagus, Green tea"))
                           {:low-inr-reason (<*buttons [{:id :vitamin-k-foods :text "I did"}
                                                        {:id :none, :text "I did not"}])})
                 [< 3] nil
                 :else (do (>* (text "Your INR level is a bit high, indicating your blood is not clotting.")
                               (text "Did you drink cranberry juice or alcohol within the last 24 hours?"))
                           {:high-inr-reason (<*buttons [{:id :alcohol :text "Alcohol"}
                                                         {:id :cranberry :text "Cranberry Juice"}
                                                         {:id :none :text "No"}])}))}))

(deflow initiation-phase
  "Attempts to get to a therapeutic dose. Target INR not yet used."
  ([patient dosage-pool target-inr days]
   {:pre [(p/patient? patient) (pool? dosage-pool) (number? target-inr) (number? days)]}
   (require-roles :patient)
   (set-index! :stage :initiation-phase, :patient-id (:id patient))
   (>* (text "Please follow the directions in the order shown."))
   (loop [inr-levels         []
          pill-confirmations []
          follow-ups         []
          doses              []]
     (notify patient "Time to measure your INR levels and take your warfarin.")
     (let [day                (count inr-levels)
           new-dose           (calculate-next-initiation-dose-ucsd patient (last doses) inr-levels)
           _                  (put-in! dosage-pool new-dose)
           new-inr-level      (measure-inr-level)
           inr-levels         (conj inr-levels new-inr-level)
           pill-confirmations (conj pill-confirmations (confirm-pills-taken patient day days new-dose))
           follow-ups         (conj follow-ups (post-measurement-follow-up inr-levels))
           doses              (conj doses new-dose)]
       (set-index! :initiation-phase {:day day :dose new-dose :inr-level new-inr-level})
       (if (<= day days)
         (do
           (>* (text "Great, we're all done for today. I'll check in tomorrow and we can continue."))
           (<* :expires (-> 24 hours from-now) :permit "advance")
           (recur inr-levels, pill-confirmations, follow-ups, doses))

         ;; signal that we're done using the dosage pool
         (put-in! dosage-pool nil)))))
  ([patient dosage-pool target-inr]
   (initiation-phase patient dosage-pool target-inr 5)))

(deflow maintenance-phase [& rest]
  :TODO)

;;
;; Helper fns
;;
;; another helpful calculator: http://warfarindosing.org/Source/InitialDose.aspx
;; Dr. Brian Gage developed the GIFT algorithm (Genetic InFormatics Trial (GIFT) of Warfarin Therapy to Prevent DVT Trial):
;;      https://generalmedicalsciences.wustl.edu/people/brian-f-gage-md-msc/
(defn calculate-starting-dose [race age sex]
  {:assert [(#{:black :white :hispanic :asian} race)
            ()]}
  ;; https://health.ucsd.edu/for-health-care-professionals/anticoagulation-guidelines/warfarin/warfarin-initiation/Pages/default.aspx
  (cond
    (= race :black) (if (< age 70) 7.5 5)
    ((some-fn :white :hispanic) race) (if (< age 70) 5 (if (= sex :male) 5 2.5))
    (= race :asian) 2.5
    :else 5))

(defn calculate-next-initiation-dose-ucsd
  ;; https://health.ucsd.edu/for-health-care-professionals/anticoagulation-guidelines/warfarin/warfarin-initiation/Pages/default.aspx
  [patient last-dose inr-levels]
  {:post [(number? %)]}
  (let [latest-inr (last inr-levels)]
    (if (nil? latest-inr)
      (calculate-starting-dose (:race patient) (:age patient) (:sex patient))
      (min 12 (max
                0 (round-to-nearest-half (range-case latest-inr
                                                     [< 1.1] (* 2 last-dose)
                                                     [< 1.5] last-dose
                                                     [< 1.9] (* 4/5 last-dose)
                                                     [< 2.5] (* 3/4 last-dose)
                                                     [< 3.5] (* 1/2 last-dose)
                                                     [>= 3.5] 0)))))))

(defn calculate-next-initiation-dose-uwash
  "Based roughly on:  https://depts.washington.edu/anticoag/home/content/warfarin-initiation-dosing"
  [patient last-dose inr-levels]
  (let [day        (count inr-levels)
        recent-inr (last inr-levels)]
    (case day
      0 5
      1 (range-case recent-inr
                    [< 1.5] 5
                    [< 1.9] 2.5
                    [< 2.5] 2                               ;[1 2.5]
                    [>= 2.5] 0)
      2 (range-case recent-inr
                    [< 1.5] 7.5                             ;[5 10]
                    [< 1.9] 2.5                             ;[2.5, 5]
                    [< 2.5] 0                               ;[0, 2.5]
                    [< 3] 0                                 ; [0, 2.5]
                    [>= 3] 0)
      3 (range-case recent-inr
                    [< 1.5] 7.5
                    [< 1.9] 4
                    [< 2.5] 2
                    [< 3] 1
                    [>= 3] 0)
      4 (range-case recent-inr
                    [< 1.5] 10
                    [< 1.9] 6
                    [< 3] 3
                    [>= 3] 0)
      5 (range-case recent-inr
                    [< 1.5] 10
                    [< 1.9] 8
                    [< 3] 3
                    [>= 3] 0)
      6 (range-case recent-inr
                    [< 1.5] 10
                    [< 1.9] 8
                    [< 3] 5
                    [>= 3] 0))))

(def warfarin-pill-colors
  {1   :pink,
   2   :lavender
   2.5 :green
   3   :tan
   4   :blue
   5   :peach
   6   :teal
   7.5 :yellow
   10  :white})