(ns pia-server.common.auth
  (:require [rapids :refer :all]
            [malli.core :as m]
            [rapids.active-doc :as adoc]))

(def *current-user*-schema [:map
                            [:id string?]
                            [:roles [:* [:enum :patient :clinic :admin]]]
                            ])

(def ^:dynamic *current-user* #{})

(defmacro with-user
  "Sets current user."
  [user & body]
  {:pre [(m/validate *current-user*-schema user)]}
  `(binding [*current-user* user]
     ~@body))

"Auth Schema

This schema defines the conditions a user needs to meet to access a run.

The schema follows this structure {[role]: {[data]: [conditions]}}

Only one role's conditions need to be satisfied to grant the user access. However, ALL conditions within the role need
to be satisfied.

Example:
{ :patient
    {:user [[:map [:id [:= \"uuid\"]]]]}
  :clinic
    {:user [[clinic-of \"uuid\"]]}
  :admin {}}

In this example, a user has three ways of gaining access to the resource.

1. the user is a patient and has an id equal to \"uuid\"

2. the user is a clinic and the function clinic-of returns true when the user data and \"uuid\"
   are provided as arguments

3. the user is an admin
"

(def role-auth-schema [:map [[:enum [:user :index :doc]] {:optional true} [:* :any]]])

(def auth-schema [:map [[:enum [:patient :clinic :admin]] {:optional true} role-auth-schema]])

(defn is-role?
  "Checks if the current user has a specific role."
  [role]
  (some #(= role %) (:roles *current-user*)))

(defn assert-role
  "Asserts that the current user has the provided role."
  [role]
  (let [_is_role (is-role? role)]
    (if _is_role _is_role
      (throw (ex-info "Not authorized" {:type :not-authorized})))))

;;
;; Condition Helper Functions
;;

(defn kcv
  "Key condition value to a valid malli schema format.

  Converts [:id :a :b] := 1 to [:map [:id [:map [:a [:map [:b [:= 1]]]]]]]"
  [key condition value]
  {:pre [(sequential? key)
         (contains? #{:> :>= :< :<= := :not=} condition)]}
  (loop [<key   (reverse key)
         schema [condition value]]
    (if (empty? <key)
      schema
      (let [[k & rest] <key]
        (recur rest [:map [k schema]])))))

(defn set-run-auth
  "Sets the auth requirements for a run.

  Args:
    run
    schema  - symbol of schema (needs to be defined)"
  [run schema]
  {:pre [(symbol? schema)
         (m/validate auth-schema (eval schema))]}
  (set-index! run :auth (str schema))
  )

(defn get-run-auth
  "Retrieves the auth requirements for a run.

  Returns auth schema"
  [run]
  {:pre [(run? run)]
   :post [(m/validate auth-schema %)]}
  (eval (read-string (-> run :index :auth))))

(defn _is-condition-valid
  "Checks condition against provided data. Conditions can be schemas or functions.
  This should not be called directly, as it doesn't directly check for roles.

  Condition Schema

  This schema defines a condition that needs to be met given some data. These conditions can either be malli schemas or
  keywords/strings that point to pre-defined functions.

  Schema example: [:map [:id [:= \"uuid\"]]
                   This is true when the data is a map that has a key-value pair of [:id \"uuid\"]. m/validate is called with the arguments
                   schema and data.

                   Function example: [clinic-of \"uuid\"]
                   The keyword :clinic-of is matched to the predefined function _clinic-of. _clinic-of is called with the arguments data
                   and \"uuid\" and returns true/false.

  Returns: bool"
  [data condition]
  {:post [(boolean? %)]}
  (cond
    (fn? (first condition)) (let [[f & args] condition]
                              (apply f data args))
    :default (m/validate condition data)))

(defn check-auth
  "Given an auth schema, verifies that the current user satisfies the schema for the current run.
  Given a run, verifies that the current user satisfies the schema stored in the run.

  Returns: bool"
  ([] (check-auth (current-run)))
  ([arg] (if (run? arg)
           (check-auth arg (get-run-auth arg))
           (check-auth (current-run) arg)))
  ([run schema]
   {:pre  [(run? run)
           (m/validate auth-schema schema)]
    :post [(boolean? %)]}
   (let [user       *current-user*
         index      (:index run)
         doc        (when (= (:type index) :doc) (adoc/get-data run))
         _role_data (fn [args] (let [[src conditions] args]
                                 (every? #(_is-condition-valid (case src
                                                                 :user user
                                                                 :index index
                                                                 :doc doc) %) conditions)))
         _role_auth (fn [args] (let [[role data-schema] args]
                                 (if (is-role? role)
                                   (every? _role_data data-schema)
                                   false)))]
     (if (some _role_auth schema) true false))))


(defn ^{:arglists '[[schema] [run schema]]}
  assert-auth
  "Asserts that the current user satisfies the provided auth schema."
  [& args]
  (let [_check-auth (apply check-auth args)]
    (if _check-auth _check-auth
      (throw (ex-info "Not authorized" {:type :not-authorized})))))



(defn grant-run
  "Verifies the current user can start the current run, then sets the auth requirements for a user to continue the run.
  The requirements for a user to continue the run most be a symbol that is resolved to an auth-schema."
  ([start continue] (grant-run (current-run) start continue))
  ([run start continue]
   {:pre [(m/validate auth-schema start)
          (symbol? continue)]}
   (assert-auth run start)
   (set-run-auth run continue)))

