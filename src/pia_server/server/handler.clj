(ns pia-server.server.handler
  (:require
    [pia-server.apps.anticoagulation.flows.main :refer [anticoagulation]]
    [pia-server.apps.triage.flows.main :refer :all]
    [pia-server.common.notifier :as pia-notifier]
    [pia-server.server.api-types :as types]
    [pia-server.server.helpers :refer [prepare-result run-result custom-handler schema-error-handler extract-find-run-fields]]
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
      {:input                                      (custom-handler response/bad-request :input true)
       :input-error                                (custom-handler response/bad-request :input true)
       :fatal-error                                (custom-handler response/internal-server-error :server false)
       :schema.core/error                          schema-error-handler
       :compojure.api.exception/request-validation schema-error-handler

       CoercionError                               (custom-handler response/bad-request :input true)
       ;; catches all SQLExceptions (and its subclasses)
       SQLException                                ex/safe-handler
       ;(ex/safe-handler)
       ;(response/internal-server-error {:message "Server error" :type :db})
       ;:info)

       #_#_:compojure.api.exception/default (custom-handler response/internal-server-error :server false)

       ;; everything else
       ::ex/default                                ex/safe-handler #_(ex/with-logging response/internal-server-error :error)}}}

    (context "/api" []
      :tags ["api"]

      (context "/flows" []
        :tags ["flows"]

        (GET "/" []
          :summary "List Flows"
          :return {:flows [types/Flow]}
          (prepare-result :flows (map (fn [[name flow]]
                                        (println "flow" name (meta flow))
                                        (util/assoc-if {:name name}
                                          :arglists (-> flow meta :arglists)
                                          :doc (-> flow meta :doc)))
                                   flows)))

        (GET "/:name" []
          :summary "Get Flow"
          :path-params [name :- scm/Keyword]
          :return {:flow types/Flow}
          (prepare-result :flow {:name name
                                 :doc  (-> (get flows name) meta :doc)})))

      (context "/runs" []
        :tags ["runs"]

        (POST "/" []
          :summary "Create Run"
          :path-params []
          :return {:run types/Run}
          :body [start types/RunStartArgs]
          (let [{flow :flow args :args kwargs :kwargs} start
                args (concat args (apply concat (seq kwargs)))]
            (prepare-result :run (run-result (start! (var-get (get flows flow)) args)))))

        (GET "/" [& fields]
          :summary "Find Runs"
          :return {:runs [types/Run]}
          (let [[field-constraints limit] (extract-find-run-fields fields)]
            (prepare-result :runs (map run-result (find-runs field-constraints :limit limit)))))


        (context "/:id" []
          :summary "Run by id"
          :path-params [id :- scm/Uuid]
          :tags ["runs"]

          (GET "/" []
            :summary "Get Run"
            :path-params [id :- scm/Uuid]
            :return {:run types/Run}
            (prepare-result :run (run-result (ensure-cached-connection (get-run id)))))

          (POST "/continue" []
            :summary "Continue Run"
            :body [args types/ContinueArgs]
            :return {:run types/Run}
            (let [{input     :input
                   permit    :permit
                   interrupt :interrupt} args]
              (prepare-result :run (run-result (continue! id :input input :permit permit :interrupt interrupt)))))

          (context "/interrupts" []
            :tags ["interrupts"]

            (GET "/" []
              :summary "List Interrupts"
              :path-params [id :- scm/Uuid]
              :return {:interrupts [types/Interrupt]}
              (prepare-result :interrupts (list-interrupt-handlers (get-run id))))

            (POST "/:name" []
              :path-params [name :- scm/Keyword]
              :return {:run types/Run}
              :body [args types/InterruptArgs]
              :summary "Interrupt Run"
              (prepare-result :run (run-result (interrupt! id name :message (:message args) :data (:data args))))))))

      (context "/patients" []
        (GET "/:id" []
          :path-params [id :- scm/Int]
          (prepare-result :patient (get-patient id))))

      (GET "/hello" []
        (ok {:message "hello world"}))

      (GET "/notifications/:entity-type/:entity-id" [entity-type entity-id]
        :path-params [entity-type :- scm/Keyword
                      entity-id :- scm/Int]
        (fn [request response raise]
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
