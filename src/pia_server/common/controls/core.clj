(ns pia-server.common.controls.core
  (:require [rapids :refer :all]
            [malli.core :as m]
            [malli.transform :as mt]
            [malli.json-schema :as mjs]
            [malli.error :as me]
            [rapids.support.util :refer [new-uuid]]))

(defn ^:suspending <*control
  "Outputs a control to the response vector, generating a random unique permit value,
   and injecting the permit into the control expr and using that in the listen operation"
  [expr & {:keys [expires default permit]}]
  (let [permit (if permit (if (= true permit) (str (new-uuid)) permit))]
    (>* (assoc expr :permit permit))
    (<* :permit permit :expires expires :default default)))

(def default-control-transformer (mt/transformer mt/string-transformer mt/default-value-transformer))

(defn default-control-validator [ctrl input]
  (let [schema (:schema ctrl)
        input  (if schema
                 (m/decode schema input default-control-transformer)
                 input)]
    (if (m/validate schema input)
      input
      (throw (ex-info "Bad input" {:type        :input-error
                                   :explanation (-> schema (m/explain input) me/humanize)})))))

(defn args-to-hashmap [args]
  (apply hash-map (if (-> args count odd?) (conj args nil) args)))

(defn add-input!-args [sig]
  (let [args          (first sig)
        body          (rest sig)
        valid-args    (vector? args)
        has-ampersand ((set args) '&)
        last-arg      (last args)
        kw-args       (if (map? last-arg)
                        (:keys last-arg))
        _             (if (and has-ampersand
                               (not kw-args))
                        (throw (ex-info "Invalid defcontrol signature. Varargs not permitted."
                                        {:signature sig})))]

    (if kw-args
      `([~@(butlast args) ~(update last-arg :keys (comp vec #(clojure.set/union % #{'expires 'default}) set))] ~@body)
      `([~@args & {:keys [~'expires ~'default]}] ~@body))))

(defn starts-with-<*? [n]
  (assert (re-find #"^\<\*" (name n)) "Control names must start with <*") true)

(defmacro
  defcontrol
  "Defines a Rapids control. Controls send instructions to output and receive a response from input.
  Controls may optionally transform and validate the input.

   E.g., (defcontrol yesno
           \"Requests the client to display a yes / no input button, and provide a boolean result\"
           []
           {:type :my-yesno :schema :boolean})

   This defines a flow, <*yesno, which returns a boolean value or throws an error if the
   client returns a non-boolean value.

   The defcontrol form must return a map which contains advice to the UI about what to display
   and/or the type of input that will be accepted.

   By default, a :type key will contain a keywordized version of the control name. The schema
   is optional. Schema documentation is available at https://github.com/metosin/malli.
   When provided, the schema is converted to JSONSchema format before being sent to the client.

   However, conversion and validation can be customized or extended by providing an alternative
   validator function. E.g.,

   (defcontrol foo {:validator ([ctrl, input] ...validate and return transformed input)} [] ...)

   The default validator function is default-control-validator."
  [control-name & cdecl]
  {:pre [(symbol? control-name) (starts-with-<*? control-name)]}
  (let [name-str          (name control-name)
        default-type      (keyword (subs name-str 2))
        [doc-string? attr-map? & sigs?] cdecl
        [doc-string attr-map sigs] (if (string? doc-string?)
                                     (if (map? attr-map?)
                                       [doc-string? attr-map? sigs?]
                                       [doc-string? {} `(~attr-map? ~@sigs?)])
                                     (if (map? doc-string?)
                                       [nil doc-string? `(~attr-map? ~@sigs?)]
                                       [nil {} `(~doc-string? ~attr-map? ~@sigs?)]))
        sigs              (if (-> sigs first vector?) [sigs] sigs)
        ctor-name         (symbol (str name-str "-ctor"))
        validator-fn-def  (:validator attr-map)
        validator-fn-name (symbol (str name-str "-validator"))
        validator-op      (if validator-fn-def validator-fn-name `default-control-validator)
        attr-map          (dissoc attr-map :validator)
        reified-sigs      (map add-input!-args sigs)
        attr-map          (assoc attr-map
                            :arglists (mapv first reified-sigs)
                            :doc doc-string)]
    `(do
       (defn ~ctor-name [& args#]
         (let [ctrl#   (apply (fn ~@reified-sigs) args#)
               ctrl#   (update ctrl# :type #(if % % ~default-type))
               output# (update ctrl# :schema #(if % (mjs/transform %)))]
           [ctrl# output#]))

       ~@(if validator-fn-def `((defn ~validator-fn-name ~@validator-fn-def)))

       (deflow ~control-name [& args#]
         (let [hashmap-args# (args-to-hashmap args#)
               expires#      (:expires hashmap-args#)
               default#      (:default hashmap-args#)
               [ctrl# output#] (apply ~ctor-name args#)
               result#       (<*control output# :expires expires# :default default#)]
           (~validator-op ctrl# result#)))

       (alter-meta! #'~control-name merge '~attr-map)

       #'~control-name)))

(alter-meta! #'defcontrol merge {:arglists '([name doc-string? attr-map [params*] prepost-map? body]
                                             [name doc-string? attr-map ([params*] prepost-map? body) + attr-map?])})