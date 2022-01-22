(ns pia-server.common.roles
  (:require [rapids :refer :all]))

(def ^:dynamic *current-roles* #{})

(defmacro with-roles [roles & body]
  `(let [roles# ~roles]
     (binding [*current-roles* (into #{} roles)]
       ~@body)))

(defn require-roles [& roles]
  #_(assert (every? #(*current-roles* %) roles)
          (str "Current user not authorized"))
  (set-status! :roles roles))