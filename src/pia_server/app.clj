(ns pia-server.app
  (:require [compojure.api.sweet :refer :all]
            [clojure.set :refer [rename-keys]]
            [clojure.core.async :as async]
            [clojure.data.json :as json]
            [buddy.sign.jwt :as jwt]
            [envvar.core :refer [env]]
            [ring.util.http-response :refer :all]
            [ring.middleware.conditional :refer [if-url-starts-with]]
            [ring.middleware.cors :refer [wrap-cors]]
            [rapids :refer :all]
            [schema.core :as scm]
            [clojure.string :as str]
            [ring.logger :as logger]
            [pia-server.apps.anticoagulation.flows.main :refer [anticoagulation]]
            [taoensso.timbre :as log]
            [compojure.api.exception :as ex]
            [ring.util.http-response :as response]
            [cheshire.core :as cheshire]
            [schema.core :as s])
  (:import (java.sql SQLException)
           (java.util UUID)))


(scm/defschema JSONK (scm/maybe
                       (scm/cond-pre scm/Num scm/Str scm/Bool scm/Keyword scm/Uuid
                                     [(scm/recursive #'JSONK)]
                                     {(scm/cond-pre scm/Str scm/Keyword) (scm/recursive #'JSONK)})))

(scm/defschema QueryArgs (scm/maybe (scm/cond-pre scm/Num scm/Str scm/Bool scm/Keyword scm/Uuid)))
(scm/defschema MapSchema (scm/maybe {(scm/cond-pre scm/Str scm/Keyword) scm/Str}))

(scm/defschema Run
  {:id                               scm/Uuid
   :state                            (scm/enum :running :complete :error)
   (scm/optional-key :result)        JSONK
   :output                           [JSONK]
   :status                           JSONK
   (scm/optional-key :parent_run_id) (scm/maybe scm/Uuid)})

(scm/defschema ContinueArgs
  (scm/maybe {(scm/optional-key :permit) JSONK
              (scm/optional-key :input)  JSONK}))

;;
;; Simple example flow
;;
(deflow foo
  "This flow demonstrates status variables and an expiring listen operation.
  The initial response is 'hello' and the second response is the continue! :input
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
  (let [raw-run (.rawData run)
        run     (reduce-kv #(assoc %1 (keyword (str/replace (name %2) "-" "_")) %3) {}
                           (select-keys raw-run
                                        [:id :output :result :state :status :parent-run-id]))]
    (if (-> run :state (not= :complete))
      (dissoc run :result)
      run)))


(defn custom-handler [f type show-data]
  (fn [^Exception e data request]
    (f (cond-> {:message (.getMessage e), :type type}
         show-data (assoc :data data)))))

(def stream-response
  (partial assoc {:status 200, :headers {"Content-Type" "text/event-stream"}} :body))

(def EOL "\n")

(defn stream-msg [payload]
  (str "data:" (json/write-str payload) EOL EOL))

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
                {:input-error                                (custom-handler response/bad-request :input true)
                 :fatal-error                                (custom-handler response/internal-server-error :server false)

                 :compojure.api.exception/request-validation (custom-handler response/bad-request :input true)

                 ;; catches all SQLExceptions (and its subclasses)
                 SQLException                                ex/safe-handler
                 ;(ex/safe-handler)
                 ;(response/internal-server-error {:message "Server error" :type :db})
                 ;:info)

                 ;; everything else
                 ::ex/default                                ex/safe-handler #_(ex/with-logging response/internal-server-error :error)}}}

    (context "/api" []
      :tags ["api"]

      (context "/runs" []
        :tags ["runs"]

        (POST "/start/:flow" []
          :path-params [flow :- (apply scm/enum (map #(first %) flows))]
          :return Run
          :body [args [scm/Any] []]
          :summary "Starts a Run based on the given flow"
          (ok (let [result (run-result (apply start! (var-get (get flows flow)) args))]
                (log/info (str "/api/runs/" flow " =>") result)
                result)))

        (POST "/continue/:id" []
          :path-params [id :- scm/Uuid]
          :return Run
          :body [args ContinueArgs]
          :summary "Continues a run"
          (ok (let [result (run-result (continue! id :input (:input args) :permit (:permit args) :interrupt (:interrupt args)))]
                (log/info (str "/" id "/continue =>") result)
                result)))

        (GET "/find" [& fields]
          :summary "Retrieves multiple runs"
          :return scm/Any
          (ok
            (let [uuid-shaped?      (fn [v] (re-find #"^[0-9a-fA-F]{8}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{4}\b-[0-9a-fA-F]{12}$" v))
                  parse-val         (fn [v]
                                      (try (if (uuid-shaped? v)
                                             (UUID/fromString v)
                                             (cheshire/parse-string v))
                                           (catch Exception _ v)))
                  process-field     (fn [[k v]]
                                      (let [str-keys (str/split (name k) #"\.")
                                            [str-keys array-lookup?] (if (-> str-keys last (str/ends-with? "$"))
                                                                       (let [last-str (last str-keys)]
                                                                         [`[~@(butlast str-keys) ~(-> last-str (subs 0 (-> last-str count dec)))] true])
                                                                       [str-keys false])
                                            field    (mapv keyword str-keys)
                                            field    (if (= 1 (count field)) (first field) field)]
                                        [field (if array-lookup? :? :eq) (parse-val v)]))
                  limit             (:limit fields)
                  field-constraints (mapv process-field (dissoc fields :limit))]
              (map run-result (find-runs field-constraints :limit limit)))))

        (GET "/:id" []
          :path-params [id :- scm/Uuid]
          :return Run
          :summary "Gets a run"
          (ok (let [result (run-result (ensure-cached-connection (get-run id)))]
                (log/info (str "/api/runs/" id " =>") result)
                result)))))

    (GET "/hello" []
      (ok {:message "hello world"}))

    (GET "/async" []
      (fn [req res raise]
        (let [ch (async/chan)]
          (res (stream-response ch))
          (async/go (async/>! ch (stream-msg {:val 42}))
                    (async/<! (async/timeout 1000))
                    (async/>! ch (stream-msg {:val 100}))
                    (async/close! ch)))))

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
  (fn
    ([request]
     (wrap-jwt request nil nil))
    ([request response-fn raise-fn]
     (if-let [auth-hdr (get-in request [:headers "authorization"])]
       (let [bearer (subs auth-hdr (.length "Bearer "))]
         (try
           (let [request+identity (assoc request
                                    :identity
                                    (jwt/unsign bearer (@env :jwt-secret)))]
             (if response-fn
               (handler request+identity response-fn raise-fn)
               (handler request+identity)))
           (catch Exception e
             (if (= {:type :validation :cause :signature}
                    (ex-data e))
               (if (@env :disable-jwt-auth)
                 (if response-fn
                   (handler request response-fn raise-fn)
                   (handler request))
                 (if response-fn
                   (response-fn (unauthorized))
                   (unauthorized)))
               (if response-fn
                 (response-fn (internal-server-error))
                 (internal-server-error))))))
       (if (@env :disable-jwt-auth)
         (if response-fn
           (handler request response-fn raise-fn)
           (handler request))
         (if response-fn
           (response-fn (unauthorized))
           (unauthorized)))))))

(def app
  (-> #'base-handler
      logger/wrap-with-logger
      (wrap-cors :access-control-allow-origin [#".*"]
                 :access-control-allow-methods [:get :post])))
