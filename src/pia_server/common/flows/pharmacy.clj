(ns pia-server.common.flows.pharmacy
  (:require [rapids :refer :all]
            [pia-server.common.notifier :refer [notify]]
            [pia-server.common.controls.basic :refer [<*buttons]]
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

(def PrescriptionStates [:ordered :fulfilled :delivered])

(defn validate-prescription-order [& {:keys [drug strength unit dosage frequency dispense refills route]}]
  (have! string? drug)
  (have! number? strength)
  (have! [:el #{:pills :vials :mls}] unit)
  (have! [:or number? #(= % :as-directed)] dosage)
  (have! [:or
          [:el #{:daily :eod :bid :tid :qid :qhs :qwk :as-directed}]
          [:and map?
           [:or #(-> % :hours number?)
            [:and #(-> % :hours seq?)
             #(-> % :hours first number?)
             #(-> % :hours second number?)]]]]
         frequency)
  (have! number? dispense)
  (have! number? refills)
  (have! [:el #{:po :pr :im :iv :id :in :tp :sl :bucc :ip}] route))

(deflow order-prescription
  "Orders a prescription

  sets :index
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
  [patient & {:keys [drug strength unit dosage frequency dispense refills route] :as args
              :or   {refills 0}}]
  {:pre [(validate-prescription-order :drug drug :strength strength :unit unit :dosage dosage
                                      :dispense dispense :frequency frequency
                                      :refills refills :route route)]}
  (println "SENDING PRESCRIPTION TO PHARMACY....." args "  patient" (:id patient))
  (println (str "Pharmacy service should POST \"delivered\" to http://localhost:8080/api/runs/continue/"
                (current-run :id)))
  (set-index! :prescription :ordered,
               :patient-id (:id patient),
               :title (str "Prescribing " drug))
  (loop [rx-status (<*buttons PrescriptionStates)]
    (set-index! :prescription rx-status)
    (if (not= rx-status :delivered)
      (recur (<*buttons PrescriptionStates)))))
