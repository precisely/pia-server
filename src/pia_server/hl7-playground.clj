(ns pia-server.hl7-playground
  (:require [clojure.data.json :as json]
            [json-schema.core :as json-schema]))


(def r4-schema-json (clojure.java.io/resource "hl7-schemas/r4.json"))
(def r4-schema (json/read (clojure.java.io/reader r4-schema)))


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
