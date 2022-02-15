(ns pia-server.common.controls.basic
  (:require [rapids :refer :all]
            [clojure.string :as s]
            [pia-server.common.controls.core :refer [defcontrol keyword-to-label normalize-id-map]]))

(defn normalize-button-def [bdef]
  (letfn [(button-def-error [] (throw (ex-info "Invalid button def" bdef)))]
    (cond
      (keyword? bdef) {:id bdef :label (keyword-to-label bdef)}
      (map? bdef) (if (and (-> bdef :id keyword?)
                           (-> bdef :label string?))
                    bdef
                    (button-def-error))
      :else (button-def-error))))

(defcontrol <*buttons
  "Presents choices to the user.

  E.g.,
  longhand: (<*buttons [{:id :yes, :label \"Yes\"}, {:id :no, :label \"No\"}])
  shorthand: (<*buttons [:yes :no]) or
             (<*buttons {:yes \"Yes\", :no \"No\"})
  In shorthand form, the keyword name is capitalized and underscores are turned into spaces)"
  [button-defs]
  (let [norm-bdefs (if (map? button-defs)
                     (normalize-id-map button-defs #(hash-map :label %2))
                     (mapv normalize-button-def button-defs))]
    {:buttons norm-bdefs
     :schema  [:and :keyword `[:enum ~@(map :id norm-bdefs)]]}))

(defn text
  "Request the user to present text to the user"
  [& objs]
  {:type :text, :text (apply str objs)})

(defn >*text
  [& objs]
  (>* {:type :text, :text (apply str objs)}))