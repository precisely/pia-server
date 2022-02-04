(ns pia-server.db.models.patient
  (:require [namejen.names :as nj]
            [clojure.string :as str]))

(def +patients+ (atom {}))

(defn get-patient [pid]
  (get @+patients+ pid))

(defn patient? [o]
  (and (map? o) (-> o :type (= :patient))))

(defn update-patient!
  ([p]
   (swap! +patients+ assoc (:id p) p)
   p)
  ([p k f & args]
   (update-patient! (apply update p k f k args))))

(defn generate-patient [id]
  (let [sex (if (= 1 (rand-int 2)) :male :female)
        first-name (if (= sex :male) (nj/male-name) (nj/female-name))
        last-name (nj/generic-name)]
    {:id    (inc id)
     :name  (str first-name " " last-name)
     :email (str first-name last-name "@gmail.com")
     :sex   sex
     :race  (rand-nth [:white :black :asian])}))

(dotimes [id 20]
  (update-patient! (generate-patient id)))