(ns pia-server.core
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [longterm :refer :all]
            [pia-server.db :as db]
            [pia-server.expiry-monitor :as expiry-monitor]
            [schema.core :as scm]))


(scm/defschema Run
  {:id           scm/Uuid
   :state        (scm/enum :running :suspended :complete)
   :result       scm/Any
   :response     [scm/Any]
   :run-response [scm/Any]
   :return-mode  (scm/maybe (scm/enum :block :redirect))
   :next-id      (scm/maybe scm/Uuid)
   :next         (scm/maybe (scm/recursive #'Run))})

(scm/defschema Event
  {:permit                  scm/Keyword
   (scm/optional-key :data) scm/Any})

(def db-runstore (db/make-runstore))
(set-runstore! db-runstore)
(db/create-db!)
(expiry-monitor/start db-runstore)

(deflow foo []
  (*> "hello")
  (let [value (<* :permit :foo :expires (-> 2 minutes from-now) :default "default-suspend-value")]
    (*> (str value " world!"))
    :result))

(def flows {:foo foo})

(defn run-result [run]
  (select-keys run
    [:id :response :next-id :next :result :state :return-mode :run-response]))

(def base-handler
  (api
    {:swagger
               {:ui      "/"
                :spec    "/swagger.json"
                :options {:ui {:doc-expansion :full}}
                :data    {:info {:title       "pia-server"
                                 :description "Precisely Intelligent Agent Server API"}
                          :tags [{:name "api", :description "some apis"}]}}
     :coercion :schema}

    (context "/api" []
      :tags ["api"]

      (context "/runs" []
        :tags ["runs"]

        (POST "/:flow" []
          :path-params [flow :- (apply scm/enum (map #(first %) flows))]
          :return Run
          :body [args [scm/Any] []]
          :summary "starts a Run based on the given flow"
          (ok (run-result (apply start! (get flows flow) args))))

        (POST "/:id/continue" []
          :path-params [id :- scm/Uuid]
          :return Run
          :body [event Event]
          :summary "continues a run"
          (ok (run-result (continue!
                            id
                            (:permit event)
                            (:data event)))))

        (GET "/:id" []
          :path-params [id :- scm/Uuid]
          :return Run
          :summary "gets a run"
          (ok (run-result (get-run id))))))))

(def app
  (-> #'base-handler
    ;; put ring middlware here, e.g.:
    ;; Buddy for JWT (https://funcool.github.io/buddy-auth/latest/#signed-jwt):
    ;;(wrap-authentication ...)
    ;; encors for CORS (https://github.com/unbounce/encors):
    ;;(wrap-cors cors-policy)
    ))
