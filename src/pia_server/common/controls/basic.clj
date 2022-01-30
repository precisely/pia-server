(ns pia-server.common.controls.basic
  (:require [clojure.string :as s]
            [pia-server.common.controls.core :refer [defcontrol normalize-id-map]]))

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

;; Constantine - buttons, eg in a bot chat
(defcontrol <*buttons
  "Presents choices to the user.

  E.g.,
  longhand: (<*buttons [{:id :yes, :label \"Yes\"}, {:id :no, :label \"No\"}])
  shorthand: (<*buttons [:yes :no]) or (<*buttons {:yes \"Yes\", :no \"No\"})
  In shorthand form, the keyword name is capitalized and underscores are turned into spaces)"
  [button-defs]
  (let [norm-bdefs (if (map? button-defs)
                     (normalize-id-map button-defs #(hash-map :label %2))
                     (mapv normalize-button-def button-defs))]
    {:buttons norm-bdefs
     :schema  [:and :keyword `[:enum ~@(map :id norm-bdefs)]]}))

;; Constantine - this is a text bubble in a chat
(defn text [& objs]
  {:type :text, :text (apply str objs)})
