(ns pia-server.common.flows.patient
  (:require [rapids :refer :all]
            [pia-server.common.controls.basic :refer [<*buttons]]
            [pia-server.common.notifier :refer [notify]]
            [pia-server.db.models.patient :as p]
            [pia-server.common.roles :refer [require-roles]])
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

(deflow send-reminders
  "Regularly sends a message to the patient until the test condition or the maximum
  number of reminders is provided.

  message - a string
  cancelable - boolean determines whether the patient is asked, or string representing the stop button text. Default
     is \"Stop reminding me\"
  user-delays - nil or a vector of two-tuples providing text and a java period,
        e.g., :user-delays [[\"tomorrow\" (days 1)] [\"next week\" (week 1)]
  "
  [patient message & {:keys [cancelable user-delays until max interval]
                      :or   {interval (days 1)}}]
  {:pre [(p/patient? patient)
         (or max until)
         (or (nil? max) (number? max))
         (or (nil? until) (closure? until) (fn? until))]}
  (require-roles :patient)
  (set-status! :patient-id (:id patient))
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

(deflow pick-lab
  "In future, this might launch an interaction with the patient to determine the right lab. This might just request
  a map picker/list control be shown, for example, with labs which provide the appropriate bloodwork.
  Extra points for a flow which "
  ;; QUESTION: what if multiple tests are requested and no lab provides all coverage? What if some labs provide all
  ;; coverage (but they're further away) while closer labs provide partial coverage?
  [patient orders]
  ;; in future, initiate the interaction:
  #_(notify patient "Please pick a lab")
  (require-roles :patient)
  (set-status! :patient-id (:id patient))
  ;; for now, just return a default lab...
  {:id "lc-9876", :type :lab, :name "Labcorp Lab, 123 Main Street"})
