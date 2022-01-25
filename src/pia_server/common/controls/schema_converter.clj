;; Converts Plumatic Schema to JSON schema
(ns pia-server.common.controls.schema-converter
  (:require [schema.core :as s]
            [clojure.data.json :as json]))

(s/defrecord SomethingElse
    [id :- s/Uuid
     config :- (s/maybe s/Str)
     page_id :- [s/Uuid]
     type :- (s/enum "webhook" "something_else")])

(s/defrecord
    Integration
    [id :- s/Uuid
     config :- (s/maybe s/Str)
     page_id :- [s/Uuid]
     metadata :- SomethingElse
     type :- (s/enum "webhook" "something_else")])

(s/explain Integration)
;; -- Output -------------------------------------------------------------------
(record schema.Integration {:id Uuid,
                            :config (maybe Str),
                            :page_id [Uuid],
                            :metadata (record schema.SomethingElse {:id Uuid,
                                                                    :config (maybe Str),
                                                                    :page_id [Uuid],
                                                                    :type (enum "something_else" "webhook")}),
                            :type (enum "something_else" "webhook")})




;; -- Plumbing Code  ------------------------------------------------------------
(defn plumatic-schema->json-schema [schema]
  "One way conversion from rich data into json schema."
  (cond (vector? schema)
        {:type "array"
         :items (plumatic-schema->json-schema (first schema))}

        (seq? schema)
        (condp = (first schema)
          'record {:$ref (str (clojure.string/replace (second schema) "." "/") ".json")}
          'maybe (plumatic-schema->json-schema (second schema))
          'enum  {:type "string"
                  :enum (rest schema)})

        :default
        {:type (condp = schema
                 'Str "string"
                 'Uuid "string")}))

(defn write-json-schema [schema]
  (let [[type symbol spec] (s/explain schema)
        required           (filter #(not (is-optional (second %))) spec)]
    {:$schema     "http://json-schema.org/draft-04/schema#"
     :description "Figure out how to assign this meta from defschema"
     :type        "object" ,
     :required    (map first required) ;only use the keys
     :properties  (into {} (for [[k v] spec]
                             [k (plumatic-schema->json-schema v)]))}))

;;
;;;; -- DEMO ---------------------------------------------------------------------
;;(println (-> Integration
;;          write-json-schema
;;          json/write-str))
;;
;;;; -- OUTPUT --------------------------------------------------------------
;;(comment
;;
;;{
;;  "$schema": "http://json-schema.org/draft-04/schema#",
;;  "description": "Figure out how to assign this meta from defschema",
;;  "type": "object",
;;  "required": [
;;    "id",
;;    "page_id",
;;    "metadata",
;;    "type"
;;  ],
;;  "properties": {
;;    "id": {
;;      "type": "string"
;;    },
;;    "config": {
;;      "type": "string"
;;    },
;;    "page_id": {
;;      "type": "array",
;;      "items": {
;;        "type": "string"
;;      }
;;    },
;;    "metadata": {
;;      "$ref": "schema/SomethingElse.json"
;;    },
;;    "type": {
;;      "type": "string",
;;      "enum": [
;;        "something_else",
;;        "webhook"
;;      ]
;;    }
;;  }
;;}
;;	)