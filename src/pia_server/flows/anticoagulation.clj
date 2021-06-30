(ns pia-server.flows.anticoagulation
  (:require [rapids :refer :all]
            [pia-server.chat :refer :all]
            [digest :refer [sha-1]]
            [clojure.string :only [join]]
            [clojure.string :as string])
  (:import (java.time LocalDateTime)))

(defn normalize-order
  "In future, convert from set/list to map"
  [order]
  order)

(defn transmit-order [order patient-id permit]
  ())                                                       ; http request

(deflow labwork
        [patient-id order]
        (let [order  (normalize-order order)
              permit (hash (str patient-id (LocalDateTime/now)))] ; quick and dirty tracking ID
          (transmit-order order patient-id permit)
          (<* :permit permit)))

(defn meta*
  "Retrieves metadata for a flow, symbol or var, resolving the object as necessary"
  ([s] (meta* s *ns*))
  ([s ns]
   (cond
     (var? s) (meta s)
     (qualified-symbol? s) (meta (find-var s))
     (flow? s) (meta (find-var (:name s)))
     :else (meta (ns-resolve ns s)))))

(defn send-patient-notification [patient-id message data]
  (print (format "Patient Notification: %s (%s) %s\n" message patient-id data)))
(defn send-emr-notification [patient-id message data]
  (print (format "EMR Notification: %s (%s) %s\n" message patient-id data)))

(defn send-notification
  "Send a notification to the EMR, limiting to specific roles regarding a patient"
  [pid role fmt & fmtargs]
  (let [msg    (apply format fmt fmtargs)
        run-id (:id (current-run))]
    (case role
      :patient (send-patient-notification pid msg {:runId run-id})
      (send-emr-notification pid msg {:runId run-id, :role role}))))

(def ^:dynamic *permit*)
(defmacro interaction [[role pid fmt & fmtargs] & forms]
  (let [pid#  ~pid
        role# ~role
        msg#  (apply format ~fmt ~@fmtargs)]
    (with-bindings [*permit* (digest/sha-1 (str pid (LocalDateTime/now)))]
      ~@forms
      (send-notification pid# role# msg#))))

(deflow initial-review
        [patient bloodwork]
        (interaction [:doctor (:id patient)]
          (text (:name patient) "requires bloodwork review.")
          (form (flabel "Initial dose" :for :initial-dose)
                ()

                (deflow warfarin-onboarding [patient]
                        (text "")))))




;(defmacro
;  ^{:suspending true,
;    :arglist '[name docstring? [patient-id message & params?] roles? & body]}
;  defescalation
;  "Escalates the patient to the attention of medical staff for a decision
;
;  Example:
;  (defescalation bloodwork [patient-id message]"
;  [&form]
;  (let [[name docstring params roles & body] (normalize-def-with-docstring &form)
;        [patient-id message] params]
;    `(deflow ~name ~docstring ~@params
;       (emr-notification, roles, ~patient-id, ~message (:id (current-run)))
;       ~@body)))

(defn initial-labs-to-ui-table
  "convert labwork to tabular format for display in medical UI"
  [labwork]
  labwork)

;(defescalation initial-blood-work-review
;               [patient-id message data]
;               #{:doctor}
;               (*> "The patient's initial bloodwork is complete")
;               (*> (initial-labs-to-ui-table data))
;               "continue with treatment?"
;               {:type :button :choices [:yes :no]})
;
;
;(deflow anticoagulation [patient-id]
;        (let [initial-labs (!>> (start! labwork patient-id
;                                        [:inr :cbc-platelets :liver-panel, :albumin]))]
;          (medical-escalation :anticoagulation-initial-labs
;                              patient-id
;                              {:data {:initial-labs initial-labs}})))
;
;
