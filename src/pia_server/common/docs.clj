(ns pia-server.common.docs
  (:require [rapids :refer :all]
            [rapids.active-doc :as adoc]
            [malli.core :as m]))

;;
;; Functions to retrieve patient active documents
;;

(deflow retrieve-patient-doc
  "Finds an active patient document given a document type and list of filters"
  [type patient-id & {:keys [schema]}]
  {:pre [(keyword? type)
         (or (nil? schema) (m/schema? schema))]}
  (let [docs (find-runs [[:state :eq :running]
                                  [[:index :type] :eq type]
                                  [[:index :patient-id] :eq patient-id]]
                        {:limit 1})
        doc (or (peek docs) (adoc/create! :index {:type type
                                                  :patient-id patient-id}
                                          :schema schema))]
    doc))