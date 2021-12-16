(ns pia-server.db.models.exports
  (:require [potemkin :refer :all]
            pia-server.db.models.patient
            pia-server.db.models.clinic))

(import-vars
  [pia-server.db.models.patient get-patient update-patient!]
  [pia-server.db.models.clinic get-clinic])