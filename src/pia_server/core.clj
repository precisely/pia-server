(ns pia-server.core
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [longterm :refer :all]
            ;; [pia-server.db :as db]
            [schema.core :as scm]))


(scm/defschema Run
  {:id       scm/Uuid
   :state    (scm/enum :running :suspended :complete)
   :result   scm/Any
   :response scm/Any})

(scm/defschema Event
  {:event-id              scm/Keyword
   (scm/optional-key :data) scm/Any})


;; (def db-runstore (db/make-runstore))
;; (set-runstore! db-runstore)


(deflow foo []
  (respond! "hello...")
  (let [value (suspend! :foo)]
    (respond! (str  value " world!"))))

(def flows {:foo foo})

(defn run-result [run]
  (select-keys run [:id :state :result :response]))


(def base-handler
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
          :path-params [id :- scm/Uuid]
          :return Run
          :summary "gets a run"
          (ok (run-result (get-run id))))

        (POST "/:flow" []
          :path-params [flow :- scm/Keyword]
          :return Run
          :body [args [scm/Any]]
          :summary "starts a Run based on the given flow"
          (ok (run-result (apply start-flow! (get flows flow) args))))

        (POST "/:id/continue" []
          :path-params [id :- scm/Uuid]
          :return Run
          :body [event Event]
          :summary "continues a run"
          (ok (run-result (apply process-event!
                                 id
                                 (:event-id event)
                                 (:data event)))))))))


(def app
  (-> #'base-handler
      ))
