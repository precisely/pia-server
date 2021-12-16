(ns pia-server.db.models.clinic)

(def +clinics+ (atom {001 {:id    001,
                           :name  "Acme Anticoagulation Clinic"
                           :phone "1-555-555-5555"
                           :fax   "1-777-777-7777"}}))

(defn get-clinic [pid]
  (get @+clinics+ pid))

