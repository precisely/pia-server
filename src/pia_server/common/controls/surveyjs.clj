(ns pia-server.common.controls.surveyjs
  (:require [rapids :refer :all]
            [pia-server.util :refer :all])
  (:import (java.util UUID)))

(defn number-slider
  [name title min max & {:keys [increment required? min-text max-text]
                         :or   {increment 1}}]
  {:pre [(nilable string? min-text)
         (nilable string? max-text)
         (nilable string? title)
         (number? increment)]}
  (remove-nil-values {:type       :num-slider
                      :name       name
                      :text       title
                      :min        min
                      :isRequired required?
                      :min-tag    min-text
                      :max        max
                      :max-tag    max-text
                      :increment  increment}))

;; Rating type in surveyjs
(defn rating [name title min max & {:keys [required? min-text max-text]
                                    :or   {required? false, min-text "", max-text ""}}]
  {:pre [(keyword? name)
         (string? title)
         (number? min) (number? max)
         (nilable string? min-text)
         (nilable string? max-text)]}
  (remove-nil-values {:type               :rating
                      :name               name
                      :title              title
                      :isRequired         required?
                      :rateMin            min
                      :rateMax            max
                      :minRateDescription min-text
                      :maxRateDescription max-text}))

;; RadioGroup question https://surveyjs.io/Examples/Library?id=questiontype-radiogroup&platform=jQuery&theme=modern
(defn radiogroup [name title choices & {:keys [required? columns]
                                        :or   {required? false, columns 3}}]
  {:pre [(keyword? name)
         (string? title)
         (or (map? choices) (vector? choices))]}
  (remove-nil-values {:type       :radiogroup
                      :name       name
                      :title      title
                      :isRequired required?
                      :colCount   columns
                      :choices    choices}))

(defn ^:suspending survey [options & controls]
  "Usage:
  (survey {:option1 value2, :option2 value2, ...}
     (radiogroup ..)
     (rating ...)
     ... etc)

  Options:
     :clear-invisible-values    boolean
     :check-errors-mode         :on-values-changed | :on-next-page
     :max-text-length           number
     :required-text"
  (let [options (or options {})
        permit (str (UUID/randomUUID))]
    (>* (merge options
          {:type     :survey
           :elements {:questions controls}
           :permit   permit}))
    (<* :permit permit)))