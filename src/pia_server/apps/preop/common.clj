(ns pia-server.apps.preop.common
  (:require [rapids :refer :all]
            [rapids.active-doc :as adoc]
            [pia-server.common.data :refer [retrieve-patient-doc]]
            [java-time :as t]
            [pia-server.common.controls.form :refer :all]))

;Defines the schema for questions
(def question-schema [:map
                      [:form :any]
                      [:result :any]
                      ])
(def questions-schema [:map-of :keyword question-schema])

(deflow fmap [f s]
  "Map over flows"
  (if-not (empty? s)
    (loop [[head & rest] s
           results []]
      (let [results (conj results (fcall f head))]
        (if (empty? rest) results
                          (recur rest results))))))



(defn valid-time?
  "Checks if the current time is within a valid timeframe

  Args:
  timestamp       Instant object
  interval        nil or duration"
  [timestamp interval]
  {:pre [(t/instant? timestamp)
         ((or (nil? interval) (t/duration? interval)))]}
  {:post [(boolean? %)]}
  (if (nil? interval) true
                      (<= (t/instant) (t/plus timestamp interval))))

(defn get-cached-data
  "Retrieve cached data from an active document if the data is valid

  Args:
  doc             Active doc object
  key             nil or key of data
  interval        nil or duration"
  [doc key & {:keys [interval]}]
  {:pre [(or (nil? key) (sequential? key))
         (or (nil? interval) (t/duration? interval))]}
  (let [data (adoc/get-data doc key)
        is-valid (when (and data interval) (valid-time? (t/instant (:timestamp data)) interval))]
    (if is-valid data nil)))


(deflow update-doc!
  "Updates the document non-destructively. Adds a timestamp to the updated data. Returns the data that was saved."
  [doc key value]
  {:pre [(or (nil? key) (sequential? key))]}
  (let [current (adoc/get-data doc key)
        data (merge (or current {}) value {:timestamp (t/to-millis-from-epoch (t/instant))})]
    (adoc/set-data! doc key data)
    data))


(deflow module
  "Module wrapper that validates existing data. If data doesn't exist or is expired, runs the function provided and
  saves the data in the active doc.

  Args:
  doc         Active document
  key         nil or key
  query       expression that returns a result that will be saved
  interval    nil or duration for data to be refreshed"
  [doc key query & {:keys [interval]}]
  {:pre [(or (nil? key) (sequential? key))
         ((or (nil? interval) (t/duration? interval)))]}
  (or (get-cached-data doc keys :interval interval)
      (let [result (eval query)]
        (update-doc! doc key result))))

(deflow get-responses
  "Requests answers for questions and records answers in a provided document.

  If multiple questions are provided, returns a map of question-id to responses.
  If one question is provided, returns the value of the response.

  Args:
  doc
  key
  questions       form-ready schemas"
  [doc key questions]
  {:pre [(sequential? questions)]}
  {:post [()]}
  (let [responses (<*form (map :form questions))
        result (conj {} (map #(let [id (:id %)]
                                [id {:form % :result (responses id)}])
                             questions))]
    (update-doc! doc (conj key :questions) result)
    (if (> (count result) 0)
      result
      (:result (first result)))))

;; time-wrapper for obtaining results
;; obtain-value given full index key
;; check doc location for result
;; if result exists, make sure it's within the time frame
;; if expired/doesn't exist, ask questions


;; question wrapper
;; form display, full index key