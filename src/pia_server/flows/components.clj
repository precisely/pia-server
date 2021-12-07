(ns pia-server.flows.components
  (:require [rapids :refer :all]
            [rapids.support.util :refer [new-uuid]]))

(def ^:dynamic *permit*)
(defn ^:suspending <*control
  [expr]
  (let [permit (new-uuid)]
    (>* (assoc expr :permit permit))
    (<* :permit *permit*)))

(defn form [& elements]
  {:type     :form
   :elements elements})

(deflow <*form [& {:keys [elements]}]
  (let [result (<*control {:type :form, :elements elements})]
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

(defn number [id & {:keys [min max required]}]
  {:type     :number
   :id       id
   :min      min
   :max      max
   :required required})

(defn date [id & {:keys [format required]}]
  {:type     :date
   :id       id
   :format   format
   :required required})

(defn short-text [id text & {:keys [required max-length]}]
  {:type       :short-text
   :id         id
   :text       text
   :required   required
   :max-length max-length})

(defn long-text [id text & {:keys [required max-length]}]
  {:type       :long-text
   :id         id
   :text       text
   :required   required
   :max-length max-length})

(defn multiple-choice
  [id items & {:keys [required randomize show-other multiselect]
               :or   (:required false, :randomize false, :show-other false)}]
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
   :show-other  show-other
   :multiselect multiselect
   :items       items})

(defn dropdown [id choices & {:keys [required]
                              :or   {:required false}}]
  {:pre [(keyword? id)
         (every? string? choices)
         (boolean? required)]}
  {:type     :dropdown
   :id       id
   :choices  choices
   :required required})

(defn fileupload [id & {:keys [required]
                        :or   {:required true}}]
  {:type     :fileupload
   :id       id
   :required required})

(defn phone-number [id & {:keys [required country]
                          :or   {:country :us}}]
  (throw (ex-info "Not yet implemented" {})))

(defn yesno [id & {:keys [required]}]
  {:type     :yesno
   :id       id
   :required required})

(defn rating [id & {:keys [required levels icon]
                    :or   {:levels 3, icon :star}}]
  {:type     :rating
   :id       id
   :required required
   :levels   levels
   :icon     icon})

(defn ranking [id items & {:keys [required] :or {:required false}}]
  {:type     :ranking
   :items    items
   :required required})

;
; (let [user-input (<*form
;                     (multiple-choice
;                       :favorite-dog-breed
;                        [{:id :golden
;                          :text "Golden Retriever"}]))]
;    ...)

{id: "f148-ab4234..." (run-id)
 state: "running",
 response: [{type: "form", elements: [{type: "multiple-choice", items: [{type: "choice", }]}]}]
 result: nil}