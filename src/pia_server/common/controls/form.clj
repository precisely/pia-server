(ns pia-server.common.controls.form
  (:require [rapids :refer :all]
            [pia-server.common.controls.core :refer [<*control]]))

(deflow <*form [elements & {:keys [expires default]}]
  (let [result (<*control {:type :form, :elements elements}
                          :expires expires :default default)]
    result))

(defn valid? [element data]
  (or (not (:required element))
      (contains? data (:id element))))

(defn label
  ([title] (label title nil))
  ([title subtitle]
   {:type     :label
    :title    title
    :subtitle subtitle}))

(defn number [id & {:keys [label units min max required]}]
  {:type     :number
   :label    label
   :units    units
   :id       id
   :min      min
   :max      max
   :required required})

(defn date [id & {:keys [format required]}]
  {:type     :date
   :id       id
   :format   format
   :required required})

(defn short-text [id label & {:keys [hint required max-length]}]
  {:type       :short-text
   :id         id
   :label      label
   :hint       hint                                         ; e.g., grayed out text in text box
   :required   required
   :max-length max-length})

(defn long-text [id label & {:keys [required max-length]}]
  {:type       :long-text
   :id         id
   :label      label
   :required   required
   :max-length max-length})

(defn multiple-choice
  [id items & {:keys [label required randomize show-other multiselect]
               :or   {required false, randomize false, show-other false}}]
  {:pre [(keyword id)
         (seq? items)
         (not (empty? items))
         (boolean? required)
         (boolean? randomize)
         (boolean? show-other)
         (or (nil? multiselect)
             (number? multiselect)
             (and (seq? multiselect) (= (count multiselect) 2)))]}
  {:type        :multiple-choice
   :id          id
   :required    required
   :randomize   randomize
   :label       label
   :show-other  show-other
   :multiselect multiselect
   :items       items})

(defn dropdown [id choices & {:keys [label required]
                              :or   {required false}}]
  {:pre [(keyword? id)
         (every? string? choices)
         (boolean? required)]}
  {:type     :dropdown
   :id       id
   :label    label
   :choices  choices
   :required required})

(defn fileupload [id & {:keys [label required]
                        :or   {required true}}]
  {:type     :fileupload
   :id       id
   :label    label
   :required required})

(defn phone-number [id & {:keys [label required country]
                          :or   {country :us}}]
  (throw (ex-info "Not yet implemented" {})))

(defn yesno [id & {:keys [required]}]
  {:type     :yesno
   :id       id
   :label    label
   :required required})

(defn rating [id & {:keys [label required levels icon]
                    :or   {levels 3, icon :star}}]
  {:type     :rating
   :id       id
   :required required
   :label    label
   :levels   levels
   :icon     icon})

(defn ranking [id items & {:keys [label required]
                           :or {required false}}]
  {:type     :ranking
   :id       id
   :items    items
   :label    label
   :required required})

;
; (let [user-input (<*form
;                     (multiple-choice
;                       :favorite-dog-breed
;                        [{:id :golden
;                          :text "Golden Retriever"}]))]
;    ...)
