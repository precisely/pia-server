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

"Condition Schema

This schema defines a condition that needs to be met given some data. These conditions can either be malli schemas or
keywords/strings that point to pre-defined functions.

Schema example: [:map [:id [:= \"uuid\"]]
This is true when the data is a map that has a key-value pair of [:id \"uuid\"]. m/validate is called with the arguments
schema and data.

Keyword example: [:clinic-of \"uuid\"]
The keyword :clinic-of is matched to the predefined function _clinic-of. _clinic-of is called with the arguments data
and \"uuid\" and returns true/false.

String example: [\"zero?\" true]
The string \"zero?\" is converted to a symbol and resolved as a function. zero? is called with the arguments data and
true and returns true/false.
"
(def condition-schema [:or
                       m/schema?
                       [:cat keyword? [:* :any]]
                       [:cat string? [:* :any]]])

"Auth Schema

This schema defines the conditions a user needs to meet to access a run.

The schema follows this structure {[role]: {[data]: [conditions]}}

Only one role's conditions need to be satisfied to grant the user access. However, ALL conditions within the role need
to be satisfied.

Example:
{ :patient
    {:user [[:map [:id [:= \"uuid\"]]]]}
  :clinic
    {:user [[:clinic-of \"uuid\"]]}
  :admin {}}

In this example, a user has three ways of gaining access to the resource.

1. the user is a patient and has an id equal to \"uuid\"

2. the user is a clinic and the function :clinic-of returns true when the user data and \"uuid\"
   are provided as arguments

3. the user is an admin
"

(def role-auth-schema [:map [[:enum [:user :index :doc]] [:* condition-schema]]])

(def auth-schema [:map [[:enum [:patient :clinic :admin]] role-auth-schema]])

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
  {:pre  [(sequential? key)
          (contains? #{:> :>= :< :<= := :not=} condition)]
   ;:post [(m/schema? %)]
   }
  (loop [<key   (reverse key)
         schema [condition value]]
    (if (empty? <key)
      schema
      (let [[k & rest] <key]
        (recur rest [:map [k schema]])))))

;;
;; Predefined Auth Functions
;;
;; These are referened in conditions by keyword.
;;

(defn _clinic-of
  "Checks that the current clinic is responsible for the patient-id provided.

  Returns: bool"
  [user patient-id]
  {:pre  [(m/validate *current-user*-schema user)
          (is-role? :clinic)]
   :post [(boolean? %)]}
  true)

(defn is-condition-valid
  "Checks condition against provided data. Conditions can be schemas or keywords and strings that represent functions.
  This should not be called directly, as it doesn't directly check for roles.

  Returns: bool"
  [data condition]
  {:pre  [(m/validate condition-schema condition)]
   :post [(boolean? %)]}
  (cond
    (m/schema? condition) (m/validate condition data)
    (keyword? (first condition)) (let [[fn & args] condition]
                                   (case fn
                                     :clinic-of (apply _clinic-of data args)))
    (string? (first condition)) (let [[sym & args] condition
                                      _fn (resolve (symbol sym))]
                                  (apply _fn data args))
    :default false))

(defn check-auth
  "Given an auth schema, verifies that the current user satisfies the schema for the current run.

  Returns: bool"
  ([schema] (check-auth (current-run) schema))
  ([run schema]
   {:pre [(m/validate auth-schema schema)]
    :post [(boolean? %)]}
   (let [user       *current-user*
         index      (:index run)
         doc        (when (= (:type index) :doc) (adoc/get-data run))
         _role_data (fn [src & conditions] (every? #(is-condition-valid (case src
                                                                          :user user
                                                                          :index index
                                                                          :doc doc) %) conditions))
         _role_auth (fn [role & data-schema] (if (is-role? role)
                                               (every? _role_data data-schema)
                                               false))]
     (some _role_auth schema))))


(defn ^{:arglists '[[schema] [run schema]]}
  assert-auth
  "Asserts that the current user satisfies the provided auth schema."
  [& args]
  (let [_check-auth (check-auth args)]
    (if _check-auth _check-auth
      (throw (ex-info "Not authorized" {:type :not-authorized})))))

(defn grant-run
  "Verifies the current user can start the current run, then sets the auth requirements for a user to continue the run."
  ([start continue] (grant-run (current-run) start continue))
  ([run start continue]
   {:pre [(m/validate auth-schema start)
          (m/validate auth-schema continue)]}
   (assert-auth run start)
   (set-index! :auth continue)))