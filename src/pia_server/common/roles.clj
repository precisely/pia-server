(ns pia-server.common.roles
  (:require [rapids :refer :all]))

(declare check-roles)
(def ^:dynamic *current-roles* #{})

(defmacro with-roles [roles & body]
  `(let [roles# ~roles]
     (binding [*current-roles* (into #{} roles)]
       ~@body)))

(defn ^{:arglists '[[run & roles] [& roles]]}
  require-roles
  "Sets a requirement that the indicated run should be run only by users with the provided roles.
  If run is not provided, the current run is used, and the roles are immediately checked
  for the current user. It is expected this happens when a run is started."
  [& roles]
  (if (-> roles first run?)
    (set-status! (first roles) :roles (rest roles))
    (do
      ;; TODO: enable this when we have roles enabled
      #_ (check-roles roles)
      (set-status! :roles roles))))

(defn check-roles [roles]
  (if-not (every? #(*current-roles* %) roles)
    (throw (ex-info "Not authorized" {:type :not-authorized
                                      :required-roles roles
                                      :current-roles *current-roles*}))))