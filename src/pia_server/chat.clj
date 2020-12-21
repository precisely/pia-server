(ns pia-server.chat
  (:require pia-server.chat.survey
            pia-server.chat.basic
            [potemkin :refer [import-vars]]))

(import-vars
  [pia-server.chat.survey survey radiogroup rating number-slider]
  [pia-server.chat.basic choices text])