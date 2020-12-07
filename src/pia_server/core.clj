(ns pia-server.core
  (:require [compojure.api.sweet :refer :all]
            [buddy.sign.jwt :as jwt]
            [envvar.core :as envvar :refer [env]]
            [ring.util.http-response :refer :all]
            [ring.logger :as logger]
            [rapids :refer :all]
            [pia-server.db :as db]
            [pia-server.expiry-monitor :as expiry-monitor]
            [schema.core :as scm]
            [pia-server.flows.cfsdemo :refer :all]))


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

(def flows {:foo foo
            :home home
            :welcome welcome
            })

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
          (ok (run-result (get-run id))))))

    ;; fallback
    (ANY "*" []
      (not-found))))

;; XXX: Buddy wrap-authentication middleware doesn't work as described in
;; https://funcool.github.io/buddy-auth/latest/#authentication. After this
;; middleware, (:identity request) will contain the decoded and verified JWT,
;; something like {:email alice@example.com, :sub 1, :scp user, :aud nil, :iat
;; 1605615895, :exp 1605616195, :jti 03b88e50-45bb-45f3-b340-d4efda27a2de}.
(defn wrap-jwt [handler]
  (fn [request]
    (if-let [auth-hdr (get-in request [:headers "authorization"])]
        (let [bearer (subs auth-hdr (.length "Bearer "))]
          (try
            (handler (assoc request
                            :identity
                            (jwt/unsign bearer (@env :jwt-secret))))
            (catch Exception e
              (if (= {:type :validation :cause :signature}
                     (ex-data e))
                  (unauthorized)
                  (internal-server-error)))))
        (unauthorized))))

(def app
  (-> #'base-handler
      logger/wrap-with-logger
      wrap-jwt
    ;; encors for CORS (https://github.com/unbounce/encors):
    ;;(wrap-cors cors-policy)
    ))
