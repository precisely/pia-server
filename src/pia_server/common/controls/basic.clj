(ns pia-server.common.controls.basic
  (:require [clojure.string :as s]
            [pia-server.common.controls.core :refer [defcontrol]]))

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

(defcontrol <*buttons
  "Presents choices to the user.

  E.g.,
  longhand: (<*buttons [{:id :yes, :text \"Yes\"}, {:id :no, :text \"No\"}])
  shorthand: (<*buttons [:yes :no])
  In shorthand form, the keyword name is capitalized and underscores are turned into spaces)"
  [button-defs]
  (let [norm-bdefs (map normalize-button-def button-defs)]
    {:buttons norm-bdefs
     :schema [:and :keyword `[:enum ~@(map :id norm-bdefs)]]}))

(defn text [& objs]
  {:type :text, :text (apply str objs)})