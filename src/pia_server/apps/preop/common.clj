(ns pia-server.apps.preop.common
  (:require [rapids :refer :all]
            [rapids.active-doc :as adoc]
            [pia-server.common.docs :refer [retrieve-patient-doc]]
            [java-time :as t]
            [pia-server.common.controls.form :refer :all]
            [malli.core :as m]))

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
  timestamp       nil or Instant object
  interval        nil or duration"
  [timestamp interval]
  {:pre  [(or (nil? timestamp) (t/instant? timestamp))
          (or (nil? interval) (t/period? interval))]
   :post [(boolean? %)]}
  (cond
    (nil? interval) true
    (nil? timestamp) false
    :default (t/before? (t/instant) (t/plus timestamp interval))))

(defn get-cached-data
  "Retrieve cached data from an active document if the data is valid

  Args:
  doc             Active doc object
  key             nil or key of data
  interval        nil or duration"
  [doc key & {:keys [interval]}]
  {:pre [(sequential? key)
         (or (nil? interval) (t/period? interval))]}
  (let [data      (adoc/get-data doc key)
        timestamp (:timestamp data)
        is-valid  (if data
                    (if interval
                      (if timestamp
                        (valid-time? (t/instant timestamp) interval)
                        false)
                      true)
                    false)]
    (if is-valid data nil)))


(defn update-doc!
  "Updates the document non-destructively. Adds a timestamp to the updated data. Returns the data that was saved."
  [doc key value]
  {:pre [(sequential? key)]}
  (let [current (adoc/get-data doc key)
        data    (if (map? value)
                  (merge (or current {}) value {:timestamp (t/to-millis-from-epoch (t/instant))})
                  value)]
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
  {:pre [(sequential? key)
         (or (nil? interval) (t/period? interval))]}
  (or (get-cached-data doc key :interval interval)
    (let [result (eval query)]
      (update-doc! doc key result))))

(deflow <*ask
  "Requests answers for questions and records answers in a provided document.

  If multiple questions are provided, returns a map of question-id to responses.
  If one question is provided, returns the value of the response.

  Args:
  doc
  key
  questions       form-ready schemas"
  [doc key questions]
  {:pre [(sequential? key)
         (sequential? questions)]}
  (let [responses (<*form questions)
        result    (into {} (map #(let [id (:id %)]
                                   [id {:form % :result (responses id)}])
                             questions))]

    (if (> (count result) 0)
      (do
        (update-doc! doc (conj key :questions) result)
        responses)
      (let [[question-id, response] (first result)]
        (update-doc! doc (conj key :questions question-id) response)
        (:result response)
        ))))