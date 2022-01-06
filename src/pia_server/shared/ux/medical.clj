(ns pia-server.shared.ux.medical)

(defn display-patient-labs
  "Displays labwork about a patient "
  [patient labwork]
  {:type :labwork
   :patient patient
   :labwork labwork})