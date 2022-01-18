(ns pia-server.app
  (:require [compojure.api.sweet :refer :all]
            [clojure.set :refer [rename-keys]]
            [buddy.sign.jwt :as jwt]
            [envvar.core :refer [env]]
            [ring.util.http-response :refer :all]
            [ring.middleware.conditional :refer [if-url-starts-with]]
            [com.unbounce.encors :refer [wrap-cors]]
            [rapids :refer :all]
            [schema.core :as scm]
            [clojure.string :as str]
            [ring.logger :as logger]
            [pia-server.apps.anticoagulation.flows.main :refer [anticoagulation]]
            [taoensso.timbre :as log]
            [compojure.api.exception :as ex]
            [ring.util.http-response :as response])
  (:import (java.sql SQLException)))


(scm/defschema JSONK (scm/maybe
                       (scm/cond-pre scm/Num scm/Str scm/Bool scm/Keyword scm/Uuid
                                     [(scm/recursive #'JSONK)]
                                     {(scm/cond-pre scm/Str scm/Keyword) (scm/recursive #'JSONK)})))

(scm/defschema Run
  {:id                               scm/Uuid
   :state                            (scm/enum :running :complete :error)
   (scm/optional-key :result)        JSONK
   :response                         [JSONK]
   :status                            JSONK
   (scm/optional-key :parent_run_id) (scm/maybe scm/Uuid)})

(scm/defschema Event
  (scm/maybe {(scm/optional-key :permit) JSONK
              (scm/optional-key :data)   JSONK}))

;;
;; Simple example flow
;;
(deflow foo
  "This flow demonstrates status variables and an expiring listen operation.
  The initial response is 'hello' and the second response is the continue! :data
  appended to 'world'."
  []
  (set-status! :bar "initial" :baz "unchanging")
  (>* "hello")
  (let [value (<* :permit "the-permit"                      ; must be provided as :permit argument to continue!
                  :expires (-> 30 minutes from-now)         ; auto expire this list operation after 30min
                  :default "default-suspend-value")]        ;             with this value
    (>* (str value " world!"))
    (set-status! :bar "updated")
    "some result"))

;; marking flows as dynamic to enable tests
(def ^:dynamic flows {:foo             #'foo
                      :anticoagulation #'anticoagulation})

(defn run-result [run]
  (let [raw-run (.rawData run)]
    (reduce-kv #(assoc %1 (keyword (str/replace (name %2) "-" "_")) %3) {}
               (select-keys raw-run
                            [:id :response :result :state :status :parent-run-id]))))


(defn custom-handler [f type]
  (fn [^Exception e data request]
    (f {:message (.getMessage e), :type type})))

(def base-handler
  (api
    {:swagger
               {:ui                       "/"
                :spec                     "/swagger.json"
                :options                  {:ui {:doc-expansion :full}}
                :ignore-missing-mappings? true
                :data                     {:info {:title       "pia-server"
                                                  :description "Precisely Intelligent Agent Server API"}
                                           :tags [{:name "api", :description "For starting flows and continuing runs"}]}}
     :coercion :schema

     :exceptions
               {:handlers
                {:input-error                                (custom-handler response/bad-request :input)

                 :compojure.api.exception/request-validation (custom-handler response/bad-request :input)

                 ;; catches all SQLExceptions (and its subclasses)
                 SQLException                                ex/safe-handler
                 ;(ex/safe-handler)
                 ;(response/internal-server-error {:message "Server error" :type :db})
                 ;:info)

                 ;; everything else
                 ::ex/default                                ex/safe-handler #_(ex/with-logging response/internal-server-error :error)}}}

    (GET "/hello" []
      (ok {:message "hello world"}))

    (context "/api" []
      :tags ["api"]

      (context "/runs" []
        :tags ["runs"]

        (POST "/:flow" []
          :path-params [flow :- (apply scm/enum (map #(first %) flows))]
          :return Run
          :body [args [scm/Any] []]
          :summary "starts a Run based on the given flow"
          (ok (let [result (run-result (apply start! (var-get (get flows flow)) args))]
                (log/debug (str "/api/runs/" flow " =>") result)
                result)))

        (POST "/:id/continue" []
          :path-params [id :- scm/Uuid]
          :return Run
          :body [event Event]
          :summary "continues a run"
          (ok (let [result (run-result (continue! id :data (:data event) :permit (:permit event) :interrupt (:interrupt event)))]
                (log/debug (str "/" id "/continue =>") result)
                result)))

        (GET "/:id" []
          :path-params [id :- scm/Uuid]
          :return Run
          :summary "gets a run"
          (ok (let [result (run-result (ensure-cached-connection (get-run! id)))]
                (log/debug (str "/api/runs/" id " =>") result)
                result)))))

    ;; fallback
    (GET "/__source_changed" [] (ok "false"))
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
              (if (@env :disable-jwt-auth)
                (handler request)
                (unauthorized))
              (internal-server-error)))))
      (if (@env :disable-jwt-auth)
        (handler request)
        (unauthorized)))))

;; FIXME: This is such shit.
(def cors-policy
  {:allowed-origins :star-origin
   :allowed-methods #{:get :post :options}
   :request-headers #{"Accept" "Content-Type" "Origin" "Referer" "User-Agent"}
   :exposed-headers nil
   :allow-credentials? true
   :origin-varies? false
   :max-age nil
   :require-origin? false
   :ignore-failures? true})

(def app
  (-> #'base-handler
      logger/wrap-with-logger
      wrap-jwt
      ;; encors for CORS (https://github.com/unbounce/encors):
      (wrap-cors cors-policy)
      ))
