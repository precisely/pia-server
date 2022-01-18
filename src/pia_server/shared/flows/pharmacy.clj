(ns pia-server.shared.flows.pharmacy
  (:require [rapids :refer :all]
            [pia-server.shared.notifier :refer [notify]]
            [taoensso.truss :refer [have have? have!]]))

;; For a pharmacist to dispense a controlled substance, the prescription must include specific information to be considered valid:
;; https://www.ncbi.nlm.nih.gov/books/NBK538424/
;; Date of issue
;; Patient’s name and address
;; Patient's date of birth
;; Clinician name, address, DEA number
;; Drug name
;; Drug strength
;; Dosage form
;; Quantity prescribed
;; Directions for use
;; Number of refills
;; Signature of prescriber


;;
;;
;;(defn validate-prescription [drug strength unit dosage frequency dispense refills route]
;;  (have! string? drug)
;;  (have! number? strength)
;;  (have! #{:pills :vials :mls} unit)
;;  (have! [:or number? #{:as-directed}] dosage)
;;  (have! [:or
;;          [:el #{:daily :eod :bid :tid :qid :qhs :qwk}]
;;          [:and map?
;;           [:or #(-> % :hours number?)
;;            [:and #(-> % :hours seq?)
;;             #(-> % :hours first number?)
;;             #(-> % :hours second number?)]]]]
;;         frequency)
;;  (have! [:el #{:po :pr :im :iv :id :in :tp :sl :bucc :ip}] route)
;;  (have! number? dispense)
;;  (have! number? refills))

(defn validate-rx-status [s]
  (have! [:el #{"ordered" "fulfilled" "delivered"}] s))

(deflow prescribe
  [patient & {:keys [drug strength unit dosage frequency dispense refills route] :as args
              :or   {refills 0}}]
  (println "SENDING PRESCRIPTION TO PHARMACY....." patient args)
  (println (str "Pharmacy service should POST \"delivered\" to http://localhost:8080/api/runs/continue/"
                (current-run :id)))
  (set-status! :prescription "ordered")
  (loop [rx-status (<*)]
    (validate-rx-status rx-status)
    (set-status! :prescription rx-status)
    (if (not= rx-status "delivered")
      (recur (<*)))))

(defn start-prescription
  "Starts a run that manages a prescription.
  :status
     :prescription - state of the Rx
       => ordered
       => fulfilled (prescription available)
       => shipped (if shipping used)
       => delivered (patient has prescription)

  Arguments:

  :drug - Name of the Drug

  :strength - number (representing milligrams)

  :unit - :pills :vials :mls

  :dosage - units patient should take (number or :as-directed)

  :frequency - frequency of dosage
      :as-directed
      :daily (no abbreviation)
      :eod  every other day
      :bid  (twice a day)
      :tid  (three times a day)
      :qid  (four times a day)
      :qhs  (every bedtime)
      {:hours 4}   (every 4 hours)
      {:hours [4 6]} (every 4 to 6 hours)
      :qwk  (every week)

  :route - Route Taken.
     :po (by mouth)
     :pr (per rectum)
     :im (intramuscular)
     :iv (intravenous)
     :id (intradermal)
     :in (intranasal)
     :tp (topical)
     :sl (sublingual)
     :bucc (buccal)
     :ip (intraperitoneal)

  :dispense - number of units dispensed in a single Rx fulfillment

  :refills - Number of Refills."
  [patient & {:keys [drug strength unit dosage frequency refills route]
              :or   {refills 0}}]
  (start! prescribe patient
          :drug (have! string? drug)
          :strength (have! number? strength)
          :unit (have! #{:pills :vials :mls} unit)
          :dosage (have! number? dosage)
          :frequency (have! [:or
                             [:el #{:daily :eod :bid :tid :qid :qhs :qwk}]
                             [:and map?
                              [:or #(-> % :hours number?)
                               [:and #(-> % :hours seq?)
                                #(-> % :hours first number?)
                                #(-> % :hours second number?)]]]]
                            frequency)
          :refills (have! number? refills)
          :route (have! [:el #{:po :pr :im :iv :id :in :tp :sl :bucc :ip}] route)))