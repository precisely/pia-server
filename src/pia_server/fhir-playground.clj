(ns pia-server.fhir-playground
  (:import [javax.persistence EntityManager EntityManagerFactory EntityTransaction Persistence]
           [ca.uhn.fhir.context FhirContext]
           [ca.uhn.fhir.validation FhirValidator]
           [ca.uhn.fhir.jpa.dao BaseHapiFhirResourceDao]
           [ca.uhn.fhir.jpa.dao BaseHapiFhirDao]
           [ca.uhn.fhir.jpa.dao.r4 FhirResourceDaoPatientR4]
           [org.hl7.fhir.common.hapi.validation.validator FhirInstanceValidator]
           [org.hl7.fhir.r4.model
            HumanName
            HumanName$NameUse
            Identifier
            Patient
            ]))


(def ctx (FhirContext/forR4))


(defn make-patient []
  (let [pt (Patient.)
        id (-> pt .addIdentifier
               (.setSystem "https://precise.ly/mrn")
               (.setValue "0001"))
        name (-> pt .addName
                 (.setUse HumanName$NameUse/OFFICIAL)
                 (.setFamily "Appleseed")
                 (.addGiven "Johnny"))]
    (-> ctx .newJsonParser (.encodeResourceToString pt))
    (let [validator (-> ctx .newValidator)
          validator-module (FhirInstanceValidator. ctx)]
      (.registerValidatorModule validator validator-module)
      (.validateWithResult validator pt)
      pt)))


(defn test-jpa []
  (let [entity-manager-factory (Persistence/createEntityManagerFactory "PERSISTENCE")
        entity-manager (-> entity-manager-factory .createEntityManager)
        entity-transaction (-> entity-manager .getTransaction)]
    (-> entity-transaction .begin)
    ;; The below does not work because Patient objects are not
    ;; @Entity-annotated. hapifhir is brain-damaged, and will only work through
    ;; a DAO layer. This DAO layer cannot be instantiated without Spring
    ;; dependency injection: https://groups.google.com/u/1/g/hapi-fhir/c/gHndem9R6c0/m/y_l_2p-ECQAJ
    (.persist entity-manager (make-patient))
    (-> entity-transaction .commit)
    (-> entity-manager .close)
    (-> entity-manager-factory .close)))
