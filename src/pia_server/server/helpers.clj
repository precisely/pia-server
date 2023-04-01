(ns pia-server.server.helpers
  (:require [buddy.sign.jwt :as jwt]
            [clojure.string :as str]
            [ring.util.http-response :refer :all]
            [envvar.core :refer [env]]
            [cheshire.core :as cheshire]
            [taoensso.timbre :as log]
            [ring.util.response :as response]
            [pia-server.support.util :as util])
  (:import (java.util UUID)))

(defn run-result [run]
  (let [raw-run (.rawData run)
        run     (util/assoc-if raw-run :waiting-run-ids (-> run :waits keys))
        run     (reduce-kv #(assoc %1 (keyword (str/replace (name %2) "-" "_")) %3) {}
                  (select-keys run
                    [:id :output :result :state :index :waiting-run-ids]))]
    (if (-> run :state (not= :complete))
      (dissoc run :result)
      run)))

(defn process-find-run-query
  "Process the query array into a map of key-value pairs.
  The input query array is of the form [\"key1=value1\" \"key2=value2\" ...]."
  [query]
  (into {} (map #(let [[lhs rhs] (str/split % #"=")] [(keyword lhs) rhs]) query)))

(defn extract-find-run-fields [fields]
  (let [query               (:query fields)
        fields              (merge (dissoc fields :query) (process-find-run-query query))
        uuid-shaped?        (fn [v] (re-find #" ^[0-9a-fA-F] {8} \b- [0-9a-fA-F] {4} \b- [0-9a-fA-F] {4} \b- [0-9a-fA-F] {4} \b- [0-9a-fA-F] {12} $ " v))
        parse-val           (fn [v]
                              (try (if (uuid-shaped? v)
                                     (UUID/fromString v)
                                     (cheshire/parse-string v))
                                (catch Exception _ v)))
        process-final-field (fn [k]
                              (let [str-keys (str/split (name k) #"\.")
                                    [butlast-keys last-key] [(butlast str-keys) (last str-keys)]
                                    [last op] (str/split last-key #"\$")
                                    kop      (if op (keyword op) :eq)]
                                (if (#{:eq :not-eq :contains :in :gt :lt :lte :gte :not-in} kop)
                                  [`[~@butlast-keys ~last] kop]
                                  (throw (ex-info " Invalid operator "
                                           {:type :input-error,
                                            :op   op})))))
        process-field       (fn [[k v]]
                              (let [[str-keys operator] (process-final-field k)
                                    field (mapv keyword str-keys)
                                    field (if (= 1 (count field)) (first field) field)]
                                [field operator (parse-val v)]))
        limit               (:limit fields)
        field-constraints   (mapv process-field (dissoc fields :limit))]
    [field-constraints limit]))

(defn schema-error-handler [^Exception e data request]
  (let [{{error :error value :value} :out} e]
    (response/bad-request {:message (.getMessage e), :value value :type :validation})))

(defn custom-handler [f type show-data]
  (fn [^Exception e data request]
    (f {:message (.getMessage e), :type type})))

;; XXX: Buddy wrap-authentication middleware doesn't work as described in
;; https://funcool.github.io/buddy-auth/latest/#authentication. After this
;; middleware, (:identity request) will contain the decoded and verified JWT,
;; something like {:email alice@example.com, :sub 1, :scp user, :aud nil, :iat
;; 1605615895, :exp 1605616195, :jti 03b88e50-45bb-45f3-b340-d4efda27a2de}.
(defn wrap-jwt [handler]
  (fn wrap-jwt-fn
    ([request]
     (wrap-jwt-fn request nil nil))
    ([request response-fn raise-fn]
     (if-let [auth-hdr (get-in request [:headers " authorization "])]
       (let [bearer (subs auth-hdr (.length " Bearer "))]
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


(defmacro log-result [result]
  `(log/info (str
               (str/upper-case (name (:request-method ~'+compojure-api-request+))) " "
               (:uri ~'+compojure-api-request+) " "
               (if-not (empty? (:query-params ~'+compojure-api-request+)) (:query-params ~'+compojure-api-request+) " ") " "
               (if-not (empty? (:form-params ~'+compojure-api-request+)) (:form-params ~'+compojure-api-request+) " ")
               " => ") ~result))

(defmacro prepare-result [field result]
  `(let [result# ~result
         return# (if result# (ok {~field result#}) (not-found))]
     (log-result result#)
     return#))