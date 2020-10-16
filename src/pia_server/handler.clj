(ns pia-server.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [longterm :refer :all]
            [pia-server.db :as db]
            [schema.core :as s]
            pia-server.select-coercion
            [compojure.api.coercion.core :as cc]))

(s/defschema Run
  {:id       s/Uuid
   :state    (s/enum :running :suspended :complete)
   :result   s/Any
   :response s/Any})

(s/defschema Event
  {:event-id              s/Keyword
   (s/optional-key :data) s/Any})

(def db-runstore (db/make-runstore))
(set-runstore! db-runstore)

(deflow foo []
  (respond! "hello...")
  (suspend! :foo)
  (respond! "world!"))

(def flows {:foo foo})

(defn run-result [run]
  (select-keys run [:id :state :result :response]))

(def app
  (api
    {:swagger
               {:ui   "/"
                :spec "/swagger.json"
                :data {:info {:title       "pia-server"
                              :description "Precisely Intelligent Agent Server API"}
                       :tags [{:name "api", :description "some apis"}]}}
     :coercion :schema}

    (context "/api" []
      :tags ["api"]

      (context "/runs" []
        :tags ["runs"]

        (GET "/:id" []
          :path-params [id :- s/Uuid]
          :return Run
          :summary "gets a run"
          (ok (run-result (get-run id))))

        (POST "/:flow" []
          :path-params [flow :- s/Keyword]
          :return Run
          :body [args [s/Any]]
          :summary "starts a Run based on the given flow"
          (ok (run-result (apply start-flow! (get flows flow) args))))

        (POST "/:id/continue" []
          :path-params [id :- s/Uuid]
          :return Run
          :body [event Event]
          :summary "continues a run"
          (ok (run-result (apply process-event!
                                 id
                                 (:event-id event)
                                 (:data event)))))))))
