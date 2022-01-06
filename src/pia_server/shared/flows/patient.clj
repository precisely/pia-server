(ns pia-server.shared.flows.patient
  (:require [rapids :refer :all]
            [pia-server.ux.basic :refer [<*buttons]]
            [pia-server.shared.notifier :refer [notify]])
  (:import (java.time Period)))

(defn- valid-user-delay [x]
  (and (vector? x)
       (-> x first string?)
       (instance? Period (second x))))

(defn make-user-delay-buttons
  "Returns sequence of buttons representing user delays and "
  [uds]
  {:pre [(every? valid-user-delay uds)]}
  (map-indexed (fn [idx ud]
                 [{:id idx :text (first ud)}
                  (second ud)])))

(deflow remind-patient
  "Regularly sends a message to the patient until the test condition or the maximum
  number of reminders is provided.

  message - a string
  cancelable - boolean determines whether the patient is asked, or string representing the stop button text. Default
     is \"Stop reminding me\"
  user-delays - nil or a vector of two-tuples providing text and a java period,
        e.g., :user-delays [[\"tomorrow\" (days 1)] [\"next week\" (week 1)]
  "
  [patient message & {:keys [cancelable user-delays until max interval reminder-type]
                      :or   {interval (days 1)}}]
  {:pre [or (if max (number? max))
         (if test (closure? until))]}
  (let [cancel-text    (if (string? cancelable) cancelable "Stop reminding me")
        cancel-buttons (cond-> []
                               cancelable (conj {:id   :cancel,
                                                 :text cancel-text})
                               user-delays (conj (make-user-delay-buttons user-delays)))]
    (loop [count 0]
      (when (and (or (nil? until) (until))
                 (or (nil? max) (< count max)))
        (notify patient message)
        (let [delay-result (if (empty? cancel-buttons)
                             (<* :expires (-> interval from-now) :default interval)
                             (<*buttons cancel-buttons
                                        :expires (-> interval from-now)))]
          (when-not (= delay-result :cancel)
            (recur (inc count))))))))

(deflow start-patient-reminders [patient message &
                                 {:keys [cancelable user-delays test max interval]
                                  :or   {interval (days 1)}}]
  (start! remind-patient patient message
          :cancelable cancelable
          :user-delays user-delays
          :test test :max max :interval interval))
