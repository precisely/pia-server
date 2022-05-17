(ns pia-server.common.controls.core
  (:require [rapids :refer :all]
            [malli.core :as m]
            [malli.transform :as mt]
            [malli.json-schema :as mjs]
            [malli.error :as me]
            [rapids.support.util :refer [new-uuid]]
            [clojure.string :as str]))

(defn ^:suspending <*control
  "Outputs a control to the response vector, generating a random unique permit value,
   and injecting the permit into the control expr and using that in the listen operation"
  [expr & {:keys [expires default permit] :or {:permit true}}]
  (let [permit (if permit (if (= true permit) (str (new-uuid)) permit))]
    (>* (assoc expr :permit permit))
    (<* :permit permit :expires expires :default default)))

;; defcontrol helpers
(declare default-control-transformer default-control-validator args-to-hashmap
         add-input!-args starts-with-<*? general-control-ctor extract-<*control-args
         index-of-longest-sequence)

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
       (defn ~ctor-name [~'expires? ~'default & ~'args]
         (general-control-ctor ~default-type ~'expires? ~'default (fn ~@reified-sigs) ~'args))

       ~@(if validator-fn-def `((defn ~validator-fn-name ~@validator-fn-def)))

       (deflow ~control-name [& ~'args]
         (let [[ctrl# output# expires# default#] (extract-<*control-args ~ctor-name ~'args)
               result# (<*control output# :expires expires# :default default#)]
           (~validator-op ctrl# result#)))

       (alter-meta! #'~control-name merge '~attr-map)

       #'~control-name)))

(alter-meta! #'defcontrol merge {:arglists '([name doc-string? attr-map [params*] prepost-map? body]
                                             [name doc-string? attr-map ([params*] prepost-map? body) + attr-map?])})

;;
;; HELPERS
;;
(defn index-of [x coll]
  (let [idx? (fn [i a] (when (= x a) i))]
    (first (keep-indexed idx? coll))))

(defn index-of-longest-sequence [seqs]
  (let [counts    (map count seqs)
        max-count (apply max counts)]
    (index-of max-count counts)))

(def default-control-transformer (mt/transformer mt/string-transformer mt/default-value-transformer))

(defn default-control-validator [ctrl input]
  (let [schema (:schema ctrl)
        input  (if schema
                 (m/decode schema input default-control-transformer)
                 input)]
    (if (or (not schema) (m/validate schema input))
      input
      (throw (ex-info "Bad input" {:type        :input-error
                                   :input       input
                                   :schema      schema
                                   :explanation (-> schema (m/explain input) me/humanize)})))))

(defn args-to-hashmap [args]
  (apply hash-map (if (-> args count odd?) (conj args nil) args)))

(defn add-input!-args [sig]
  (let [args          (first sig)
        body          (rest sig)
        _             (assert (vector? args))
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

(defn keyword-to-label [k]
  (str/join " " (map str/capitalize (str/split (name k) #"[_\-]"))))

(defn normalize-id-map
  "Enables compact representation of control arguments.

  Converts an object of the form {:yes {:label \"Yes\"}, :no {:label \"No\"}}
  => [{:id :yes :label \"Yes\"}, {:id :no {:label \"No\"}}

  Or a transformer function may be provided (fn [k v] ) which must return a map representing
  the control.

  E.g., (normalize-id-map {:yes \"Yes\", :no \"No\"} #(hash-map :label %2)})
  => [{:id :yes :label \"Yes\"}, {:id :no {:label \"No\"}}"
  ([obj]
   (normalize-id-map obj #(if (map? %)
                            %
                            (throw (ex-info "normalize-id-map expected map of ids to maps" {:invalid %})))))
  ([obj transformer]
   (cond
     (vector? obj) obj
     (map? obj) (reduce (fn [arr [id val]]
                          (conj arr (assoc (transformer id val) :id id)))
                        [] (seq obj)))))

(defn general-control-ctor [default-type expiring? default ctrl-fn args]
  (let [ctrl   (apply ctrl-fn args)
        ctrl   (update ctrl :type #(if % % default-type))
        ctrl   (if (contains? ctrl :schema)
                 (update ctrl :schema #(if expiring?
                                         (if default
                                           [:or % [:= default]]
                                           [:maybe %])
                                         %))
                 ctrl)
        output (update ctrl :schema #(if % (mjs/transform %)))]
    [ctrl output]))

(defn extract-<*control-args
  "Plucks out extra control arguments :default and :expires, then calls the control ctor"
  [ctor args]
  (let [hashmap-args (args-to-hashmap args)                 ; nil => non-kw args, rest of keys are kw args
        expires      (:expires hashmap-args)
        default      (:default hashmap-args)
        [ctrl output] (apply ctor expires default args)]
    [ctrl output expires default]))