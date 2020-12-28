(ns pia-server.hl7-playground
  (:require [clojure.data.json :as json]
            [json-schema.core :as json-schema]
            [pia-server.db-hl7 :as db-hl7]))


(def r4-schema-json (clojure.java.io/resource "hl7-schemas/r4.json"))
(def r4-schema (json/read (clojure.java.io/reader r4-schema-json)))


(defn run []
  (let [valid-pt {"resourceType" "Patient",
                  :id "PLY-001"
                  :name [{:given ["Johnny"], :family "Appleseed"}]}
        invalid-pt {"resourceType" "Patient",
                    :id "PLY-001"
                    :name [{:prefix "Sir" :given ["Johnny"], :family "Appleseed"}]}]
    [(try
       (json-schema/validate r4-schema (json/write-str valid-pt))
       (catch clojure.lang.ExceptionInfo ex (ex-data ex)))
     (try
       (json-schema/validate r4-schema valid-pt)
       (catch clojure.lang.ExceptionInfo ex (ex-data ex)))
     (try
       (json-schema/validate r4-schema (json/write-str invalid-pt))
       (catch clojure.lang.ExceptionInfo ex (-> (ex-data ex) :errors first)))
     (try
       (json-schema/validate r4-schema invalid-pt)
       (catch clojure.lang.ExceptionInfo ex (-> (ex-data ex) :errors first)))]))

(defn validate [res]
  (json-schema/validate r4-schema res))

(defn valid? [res]
  (try
    (validate res)
    true
    (catch clojure.lang.ExceptionInfo ex false)))

(defn create [res & {:keys [validate?] :or {validate? true}}]
  (let [res-to-save (if validate? (validate res) res)]
    (db-hl7/execute-one!
     "INSERT INTO resources (data) VALUES (to_jsonb(?))"
     (json/write-str res-to-save))
    :ok))

;; FIXME: Implement this.
(defn save [])

;; TODO: Need to support finders, not just ID.
(defn update-slow [id path val]
  ;; FIXME: broken
  (let [res (-> (db-hl7/execute-one!
                 "SELECT data FROM resources WHERE id = uuid(?)" id)
                :resources/data
                str
                json/read-str
                json/read-str)
        updated (assoc-in res path val)]
    (db-hl7/execute-one!
     "UPDATE resources SET data = ? WHERE id = uuid(?)"
     [(json/write-str updated)
      id])))

(defn update-fast [id path val]
  ;; FIXME: write this
  ;; does not validate the schema and uses jsonb_set
  )
