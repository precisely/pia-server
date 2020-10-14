(ns pia-server.handler
  (:require [compojure.api.sweet :refer :all]
            [ring.util.http-response :refer :all]
            [longterm :as lt]
            ;[pia-server.postgres_runstore :as pgrs]
            [schema.core :as s]))

(s/defschema Run
  {:id           s/Str
   :state        (s/enum :running :suspended :complete)
   :result       s/Any
   :response     s/Any})

(s/defschema StartForm
  {:flow s/Str
   :args [s/Any]})

(s/defschema Event
  {:run-id s/Str
   :event-id s/Str
   :data s/Any})

;(lt/set-runstore! (pgrs/postgres-runstore))

(def app
  (api
    {:swagger
     {:ui   "/"
      :spec "/swagger.json"
      :data {:info {:title       "pia-server"
                    :description "Precisely Intelligent Agent Server API"}
             :tags [{:name "api", :description "some apis"}]}}}

    (context "/api" []
      :tags ["api"]

      (context "/runs" []
        :tags ["runs"]

        (GET "/:id" [id]
          ;:return {:data Run}
          :summary "gets a run"
          (ok ()));(get-run id)))

        (POST "/" []
          :return Run
          :body [input StartForm]
          :summary "starts a flow"
          (ok (apply lt/start-flow!
                     (symbol (:flow input))
                     (:args input))))

        (POST "/:id/continue" [id]
          :return Run
          :body [event Event]
          :summary "continues a flow"
          (ok (apply lt/process-event!
                     (:run-id event)
                     (:event-id event)
                     (:data event))))))))

;(GET "/plus/:id" [id]
;        :return {:result Long}
;        :query-params [x :- Long, y :- Long]
;        :summary "adds two numbers together"
;        (ok {:result (+ id x y)}))
;
;      (POST "/echo" []
;        :return Pizza
;        :body [pizza Pizza]
;        :summary "echoes a Pizza"
;        (ok pizza)))))
