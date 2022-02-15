(ns pia-server.db.models.lab)


(def default-lab {:id 1, :type :lab, :name "Labcorp Lab, 123 Old Industry Road"})
(def genetics-lab {:id 2, :type :lab, :name "Akesogen Corp, 999 Mark Way"})

(def +labs+ (atom {1 default-lab
                   2 genetics-lab}))
