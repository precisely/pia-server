(ns pia-server.core
  (:require [compojure.api.sweet :refer :all]
            [clojure.set :refer [rename-keys]]
            [buddy.sign.jwt :as jwt]
            [envvar.core :refer [env]]
            [ring.util.http-response :refer :all]
            [ring.middleware.conditional :refer [if-url-starts-with]]
            [rapids :refer :all]
            [pia-server.db :as db]
            [pia-server.expiry-monitor :as expiry-monitor]
            [schema.core :as scm]
            [clojure.string :as str]
            [ring.logger :as logger]))

(scm/defschema JSON (scm/maybe
                      (scm/cond-pre scm/Num scm/Str scm/Bool
                                    [(scm/recursive #'JSON)]
                                    {scm/Str (scm/recursive #'JSON)})))

(scm/defschema Run
  {:id                               scm/Uuid
   :state                            (scm/enum :running :suspended :complete)
   (scm/optional-key :result)        JSON
   :response                         JSON
   (scm/optional-key :run_response)  JSON
   (scm/optional-key :return_mode)   (scm/maybe (scm/enum :block :redirect))
   :next_id                          (scm/maybe scm/Uuid)
   (scm/optional-key :parent_run_id) (scm/maybe scm/Uuid)
   (scm/optional-key :next)          (scm/maybe (scm/recursive #'Run))})

(scm/defschema Event
  (scm/maybe {(scm/optional-key :permit) JSON
              (scm/optional-key :data)   JSON}))

(deflow foo []
        (*> "hello")
        (let [value (<* :permit :foo :expires (-> 30 minutes from-now) :default "default-suspend-value")]
          (*> (str value " world!"))
          "some result"))

(def flows {:foo foo})

(defn run-result [run]
  (prn "run-result" run)
  (reduce-kv #(assoc %1 (keyword (str/replace (name %2) "-" "_")) %3) {}
             (select-keys run
                          [:id :response :next-id :next :result :state :return-mode :run_response :parent-run-id])))

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
          (ok (run-result (db/with-transaction [_]
                                               (apply start! (get flows flow) args)))))

        (POST "/:id/continue" []
          :path-params [id :- scm/Uuid]
          :return Run
          :body [event Event]
          :summary "continues a run"
          (ok (let [result (run-result
                             (db/with-transaction [_]
                                                  (continue!
                                                    id
                                                    event)))]
                (println result)
                result)))

        (GET "/:id" []
          :path-params [id :- scm/Uuid]
          :return Run
          :summary "gets a run"
          (ok (run-result (get-run id))))))

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

(def app
  (-> #'base-handler
      (if-url-starts-with "/api" logger/wrap-with-logger)
      wrap-jwt
      ;; encors for CORS (https://github.com/unbounce/encors):
      ;;(wrap-cors cors-policy)
      ))
