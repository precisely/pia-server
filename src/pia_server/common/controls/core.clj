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
  [expr & {:keys [expires default]}]
  (let [permit (str (new-uuid))]
    (>* (assoc expr :permit permit))
    (<* :permit permit :expires expires :default default)))

(def default-control-transformer (mt/transformer mt/string-transformer mt/default-value-transformer))

(defn default-control-value [ctrl input]
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

(defn- validate-control-sig [sig]
  (if-not (and (vector? sig)
               (if ((set sig) '&)
                 (map? (last sig))))
    (throw (ex-info "Invalid defcontrol. Varargs used in signature."
                    {:signature sig}))))

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
   value function. E.g.,

   (defcontrol foo {:value ([ctrl, input] ...validate and return transformed input)} [] ...)

   The default value function is default-control-value."
  [n & cdecl]
  {:pre [(symbol? n)]}
  (let [name-str      (name n)
        control       (symbol (str "<*" name-str))
        [doc-string? attr-map? & sigs?] cdecl
        [doc-string attr-map sigs] (if (string? doc-string?)
                                     (if (map? attr-map?)
                                       [doc-string? attr-map? sigs?]
                                       [doc-string? {} `(attr-map? ~@sigs?)])
                                     (if (map? doc-string?)
                                       [nil doc-string? `(attr-map? ~@sigs?)]
                                       [nil {} `(~doc-string? ~attr-map? ~@sigs?)]))
        sigs          (if (-> sigs first vector?) [sigs] sigs)
        value-fn-def  (:value attr-map)
        value-fn-name (symbol (str name-str "-value"))
        value-op      (if value-fn-def value-fn-name `default-control-value)
        attr-map      (dissoc attr-map :value)
        attr-map      (assoc attr-map
                        :suspending true
                        :arglists (vec (map first sigs))
                        :doc doc-string)
        default-type  (keyword name-str)]
    (run! validate-control-sig sigs)
    `(do
       (defn ~n [& args#]
         (let [ctrl# (apply (fn ~@sigs) args#)
               ctrl# (update ctrl# :schema #(if % (mjs/transform %)))]
           (update ctrl# :type #(if % % ~default-type))))

       ~@(if value-fn-def `((defn ~value-fn-name ~@value-fn-def)))

       (deflow ~control [& args#]
         (let [hashmap-args# (args-to-hashmap args#)
               expires# (:expires hashmap-args#)
               default# (:default hashmap-args#)
               ctrl#   (apply ~n args#)
               result# (<*control ctrl# :expires expires# :default default#)]
           (~value-op ctrl# result#)))

       (alter-meta! #'~control merge '~attr-map)

       #'~control)))

(alter-meta! #'defcontrol merge {:arglists '([name doc-string? attr-map [params*] prepost-map? body]
                                             [name doc-string? attr-map ([params*] prepost-map? body) + attr-map?])})