(ns pia-server.common.ux.basic
  (:require [clojure.string :as s]
            [rapids :refer :all]
            [rapids.support.util :refer [new-uuid]]))

(defn ^:suspending <*control
  "Outputs a control to the response vector, generating a random unique permit value,
   and injecting the permit into the control expr and using that in the listen operation"
  [expr & {:keys [expires default]}]
  (let [permit (str (new-uuid))]
    (>* (assoc expr :permit permit))
    (<* :permit permit :expires expires :default default)))

;;[ordered-params map-params] (if (-> params reverse second (= '&))
;;                                      [(-> params butlast butlast) (last params)]
;;                                      [params {:keys []}])
;;        map-params (if (contains? map-params :or) map-params (conj map-params {:or {}}))
;;        map-params (assoc-in map-params [:or 'required] true)
;;        map-params (assoc map-params :keys (distinct (conj keys 'required)))
(defmacro ^{:arglists ['[name docstring? generator-fn]]}
  defcontrol
  [name & cdecl]
  {:pre [(symbol? name)]}
  (let [[docstring params & rest-cdecl] (if (-> cdecl first string?) cdecl (conj cdecl nil))
        [prepost-map input-expr result-expr?] (if (-> rest-cdecl first map?) rest-cdecl (conj {} rest-cdecl))
        validation-expr (or result-expr? (constantly true))
        id              (-> name clojure.core/name keyword)]
    (assert (vector? params))
    (assert (or (nil? docstring) (string? docstring)))
    `(defn ~name ~docstring ~[] ~prepost-map
       (conj ~input-expr
             {:id id}
             {:_validation (fn)}))))

(defn- normalize-button-def [bdef]
  (letfn [(keyword-to-nice-string [k]
            (s/join (map s/capitalize (s/split (name k) #"_")) " "))
          (button-def-error [] (throw (ex-info "Invalid button def" bdef)))]
    (cond
      (keyword? bdef) {:id bdef :text (keyword-to-nice-string bdef)}
      (map? bdef) (if (and (-> bdef :id keyword?)
                           (-> bdef :text string?))
                    bdef
                    (button-def-error))
      :else (button-def-error))))

(defn normalize-id-map
  "Converts an object of the form {:yes {:text \"Yes\"}, :no {:text \"No\"}}
  => [{:id "
  ([obj]
   (normalize-id-map obj #(if (map? %)
                            %
                            (throw (ex-info "normalize-id-map expected map of ids to maps" {:invalid %})))))
  ([obj f]
   (cond
     (vector? obj) obj
     (map? obj) (reduce (fn [arr [id val]]
                          (conj arr (assoc (f val) :id id)))
                        [] (seq obj)))))

(defn ^:suspending <*buttons
  "Presents a button in the UI.

  E.g.,
  longhand: (<*buttons [{:id :yes, :text \"Yes\"}, {:id :no, :text \"No\"}])
  shorthand: (<*buttons [:yes :no])
  In shorthand form, the keyword name is capitalized and underscores are turned into spaces)"
  [button-defs & {:keys [expires default]
                  :or   {default ::first-button}}]
  (let [norm-bdefs (map normalize-button-def button-defs)
        default    (if (= default ::first-button)
                     (-> norm-bdefs first :id)
                     default)]
    (<*control {:type    :buttons
                :buttons norm-bdefs}
               :expires expires
               :default default)))

(defn text [& objs]
  {:type :text, :text (apply str objs)})