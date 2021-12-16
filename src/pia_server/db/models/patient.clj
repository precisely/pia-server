(ns pia-server.db.models.patient)

(def +patients+ (atom {123 {:id             123,
                            :name           "Bob Smith"
                            :email          "bob@bobmail.com"
                            :age            55
                            :diseases       {
                                             :heart-disease  true,
                                             :kidney-disease false,
                                             :hypertension   false,
                                             }
                            :blood-pressure 123
                            :phone          "1-555-555-5555"}}))

(defn get-patient [pid]
  (get @+patients+ pid))

(defn update-patient!
  ([p]
   (swap! +patients+ assoc (:id p) p))
  ([p k f & args]
   (update-patient! (apply update p k f k args))))