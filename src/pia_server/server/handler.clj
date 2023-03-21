(ns pia-server.server.handler
  (:require
    [pia-server.apps.anticoagulation.flows.main :refer [anticoagulation]]
    [pia-server.apps.triage.flows.main :refer :all]
    [pia-server.common.notifier :as pia-notifier]
    [pia-server.server.api-types :as types]
    [pia-server.server.helpers :as h]
    [pia-server.db.models.patient :refer [get-patient]]
    [pia-server.support.util :as util]

    [rapids :refer :all]

    [schema.core :as scm]
    [compojure.api.sweet :refer :all]
    [compojure.api.exception :as ex]
    [ring.util.http-response :refer :all]
    [ring.middleware.cors :refer [wrap-cors]]
    [ring.logger :as logger]
    [ring.util.http-response :as response]

    [clojure.core.async :as async]
    [envvar.core :refer [env]])
  (:import (java.sql SQLException)
           (compojure.api.coercion.core CoercionError)))


;;
;; Simple example flow
;;
(deflow foo
  "This flow demonstrates index variables and an expiring listen operation.
  The initial response is 'hello' and the second response is the continue! :input
  appended to 'world'."
  []
  (set-index! :bar "initial" :baz "unchanging")
  (>* "hello")
  (let [value (<*)]           ;             with this value
    (>* (str value " world!"))
    (set-index! :bar "updated")
    "some result"))


;; marking flows as dynamic to enable tests
(def ^:dynamic flows {:foo             #'foo
                      :anticoagulation #'anticoagulation
                      :depression      #'depression-flow
                      :frailty         #'frailty-flow})

(def base-handler
  (api
    {:swagger
     {:ui      "/"
      :spec    "/swagger.json"
      :options {:ui {:doc-expansion :full}}
      #_#_:ignore-missing-mappings? true
      :data    {:info {:title       "pia-server"
                       :description "Precisely Intelligent Agent Server API"}
                :tags [{:name "Precisely API", :description "For starting flows and continuing runs"}]}}
     :coercion :schema

     :exceptions
     {:handlers
      {:input                                      (h/custom-handler response/bad-request :input true)
       :input-error                                (h/custom-handler response/bad-request :input true)
       :fatal-error                                (h/custom-handler response/internal-server-error :server false)
       :schema.core/error                          h/schema-error-handler
       :compojure.api.exception/request-validation h/schema-error-handler

       CoercionError                               (h/custom-handler response/bad-request :input true)
       ;; catches all SQLExceptions (and its subclasses)
       SQLException                                ex/safe-handler
       ;(ex/safe-handler)
       ;(response/internal-server-error {:message "Server error" :type :db})
       ;:info)

       #_#_:compojure.api.exception/default (h/custom-handler response/internal-server-error :server false)

       ;; everything else
       ::ex/default                                ex/safe-handler #_(ex/with-logging response/internal-server-error :error)}}}

    (context "/api" []
      :tags ["api"]

      (context "/flows" []
        :tags ["flows"]

        (GET "/" []
          :summary "List Flows"
          :description "Returns a list of all flows which can be used to start runs. Aka \"top-level flows\""
          :return {:flows [types/Flow]}
          (h/prepare-result :flows (map (fn [[name flow]]
                                          (util/assoc-if {:name name}
                                            :arglists (-> flow meta :arglists)
                                            :doc (-> flow meta :doc)))
                                     flows)))

        (GET "/:name" []
          :summary "Get Flow"
          :description "Returns the flow with the specified name."
          :path-params [name :- scm/Keyword]
          :return {:flow types/Flow}
          (h/prepare-result :flow {:name name
                                   :doc  (-> (get flows name) meta :doc)})))

      (context "/runs" []
        :tags ["runs"]

        (POST "/" []
          :summary "Create Run"
          :description "Starts a new run of the specified flow. The flow must be specified as a
          string in the flow field. Flows can be discovered at the /api/flows endpoint. \nThe flow's arguments must be specified in the args field as a
          list of values. Keyword arguments can be specified in the kwargs field as a map of
          keyword to value. For example, to start a run of the foo flow with the arguments 'a' and
          'b' and the keyword argument c with the value 'd', use:

    {
      \"flow\": \"foo\",
      \"args\": [\"a\", \"b\"],
      \"kwargs\": {\"c\": \"d\"}
    }"
          :path-params []
          :return {:run types/Run}
          :body [start types/RunStartArgs]
          (let [{flow :flow args :args kwargs :kwargs} start
                args (concat args (apply concat (seq kwargs)))]
            (h/prepare-result :run (h/run-result (start! (var-get (get flows flow)) args)))))

        (GET "/" [& fields]
          :summary "Find Runs"
          :description "Finds runs matching the field constraints. Constrains are provided as
          field$op=value pairs. For example, to find runs with a status of 'running' and where the
          index foo is a number greater than three, use:
          \n    ?status$eq=running&index.foo$gt=3
          \nThe following operators are supported:
          eq, ne, gt, gte, lt, lte, in, not-in, contains,exists, exclude.
          The number of returned records can be limited by providing a limit parameter. For example,
          to return only the first 10 runs, use: `?limit=10`
          \n\nThis endpoint operates on the status, index, id and created_at fields."
          :return {:runs [types/Run]}
          (let [[field-constraints limit] (h/extract-find-run-fields fields)]
            (h/prepare-result :runs (map h/run-result (find-runs field-constraints :limit limit)))))


        (context "/:id" []
          :summary "Run by id"
          :path-params [id :- scm/Uuid]
          :tags ["runs"]

          (GET "/" []
            :summary "Get Run"
            :description "Returns the run with the specified id."
            :path-params [id :- scm/Uuid]
            :return {:run types/Run}
            (h/prepare-result :run (h/run-result (ensure-cached-connection (get-run id)))))

          (POST "/" []
            :summary "Continue Run"
            :description "Continue a run with the given input. If the run is not in running state, an error will be returned.
            \nThe permit is a string which may be required. This value may be visible in the Run object
            which can be obtained by the corresponding GET endpoint. This value is typically a string
            representing a UUID.
            \nThe interrupt is the interrupt UUID, which is returned by the interrupts POST endpoint. It
            is required when continuing an interrupted Run."
            :body [args types/ContinueArgs]
            :return {:run types/Run}
            (let [{input     :input
                   permit    :permit
                   interrupt :interrupt} args]
              (h/prepare-result :run (h/run-result (continue! id :input input :permit permit :interrupt interrupt)))))

          (context "/interrupts" []
            :tags ["interrupts"]

            (GET "/" []
              :summary "List Interrupt Handlers"
              :description "Returns a list of all interrupt handlers which can be used to interrupt the current run."
              :path-params []
              :return {:interrupts [types/InterruptHandler]}
              (h/prepare-result :interrupts (map #(select-keys % #{:name :metadata}) (list-interrupt-handlers (get-run id)))))

            (POST "/" []
              :summary "Interrupt Run"
              :description "Interrupts the specified run using the specified interrupt handler with optional data. The interrupt handler must be
               specified as a string as the \"name\" key. The interrupt handler may be provided optional
               data as the \"data\" key. For example, to interrupt the current run with the \"foo\" interrupt
                handler with the data \"bar\", use:

    {
      \"name\": \"foo\",
      \"data\": \"bar\"
    }"
              :path-params []
              :return {:run types/Run}
              :body [interrupt types/Interrupt]
              (let [{name :name data :data} interrupt]
                (h/prepare-result :run (h/run-result (interrupt! id name data))))))))

      (context "/patients" []
        (GET "/:id" []
          :path-params [id :- scm/Int]
          (h/prepare-result :patient (get-patient id))))

      (GET "/notifications/:entity-type/:entity-id" [entity-type entity-id]
        :path-params [entity-type :- scm/Keyword
                      entity-id :- scm/Int]
        (fn [_ response _]
          (let [ch (async/chan)]
            (response {:status  200
                       :headers {"Content-Type" "text/event-stream"}
                       :body    ch})
            (pia-notifier/sse-register entity-type entity-id ch)))))

    ;; fallback
    (GET "/__source_changed" [] (ok "false"))
    (ANY "*" []
      (not-found))))

(def wrapped-handler
  (let [cors-pattern (@env :cors-allow)
        cors-pattern (if (= "*") #".*" (re-pattern cors-pattern))]
    (-> #'base-handler
      #_logger/wrap-with-logger
      (wrap-cors :access-control-allow-origin [cors-pattern]
        :access-control-allow-methods [:get :post]))))
