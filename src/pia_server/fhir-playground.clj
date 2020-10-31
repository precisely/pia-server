(ns pia-server.fhir-playground
  (:import [ca.uhn.fhir.context FhirContext]
           [org.hl7.fhir.r4.model
            HumanName
            HumanName$NameUse
            Identifier
            Patient
            ]))


(def ctx (FhirContext/forR4))


(defn run []
  (let [pt (Patient.)
        id (-> pt .addIdentifier
               (.setSystem "https://precise.ly/mrn")
               (.setValue "0001"))
        name (-> pt .addName
                 (.setUse HumanName$NameUse/OFFICIAL)
                 (.setFamily "Appleseed")
                 (.addGiven "Johnny"))]
    (-> ctx .newJsonParser (.encodeResourceToString pt))))
