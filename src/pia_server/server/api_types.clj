(ns pia-server.server.api-types
  (:require [schema.core :as scm]))

(scm/defschema JSONK (scm/maybe
                       (scm/cond-pre scm/Num scm/Str scm/Bool scm/Keyword scm/Uuid
                         [(scm/recursive #'JSONK)]
                         {(scm/cond-pre scm/Str scm/Keyword) (scm/recursive #'JSONK)})))

;;(scm/defschema QueryArgs (scm/maybe (scm/cond-pre scm/Num scm/Str scm/Bool scm/Keyword scm/Uuid)))
;;(scm/defschema MapSchema (scm/maybe {(scm/cond-pre scm/Str scm/Keyword) scm/Str}))
(scm/defschema Flow
  {:name                        scm/Keyword
   (scm/optional-key :doc)      (scm/maybe scm/Str)
   (scm/optional-key :schema)   (scm/maybe JSONK)
   (scm/optional-key :arglists) (scm/maybe [scm/Str])})

(scm/defschema Run
  {:id                                 scm/Uuid
   :state                              (scm/enum :running :complete :error)
   (scm/optional-key :result)          JSONK
   :output                             [JSONK]
   :index                              JSONK
   (scm/optional-key :waiting_run_ids) [scm/Uuid]})

(scm/defschema RunStartArgs
  {:flow                      scm/Keyword
   (scm/optional-key :args)   [(scm/maybe scm/Any)]
   (scm/optional-key :kwargs) {scm/Keyword scm/Any}
   (scm/optional-key :index)  JSONK})

(scm/defschema ContinueArgs
  (scm/maybe {(scm/optional-key :permit) JSONK
              (scm/optional-key :input)  JSONK}))

(scm/defschema InterruptArgs
  (scm/maybe {(scm/optional-key :message) scm/Str
              (scm/optional-key :data)    JSONK}))

(scm/defschema Interrupt
  {:name                           scm/Str
   (scm/optional-key :description) scm/Str})
