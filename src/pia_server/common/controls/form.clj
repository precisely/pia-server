(ns pia-server.common.controls.form
  (:require [rapids :refer :all]
            [pia-server.common.controls.core :refer [defcontrol keyword-to-label normalize-id-map]]
            [pia-server.util :refer [assoc-if]]))

(defcontrol <*form
  "E.g., (<*form [
            (number :age :label \"your age\" :min 0 :max 140)
            (short-text :first-name :label \"your first name\")
           ])
   produces a form in the API that looks like
      {
         \"type\": \"form\"
         \"elements\": [
           { \"type\": }
         ]
      }

   A custom schema can be created by passing a second argument, schema-fn.

   (schema-fn constraints) where
      constraints - a sequence of malli constraints representing the keys of a map.
         For example, in
         `(<*form [(multiple-choice :success [:yes :no]) (long-text :data ...)] my-schema-fn)`
         `my-schema-fn` will receive [[:success ...] [:data ...]].
         If data should not be checked when :success = :no, then my-schema-fn would be:
         (defn my-schema-fn [constraints]
           [:or [:map [:success [:= :no]]]
                `[:map [:success [:= :yes]]
                      ~@constraints]])
  "
  [elements & {:keys [schema-fn]}]
  (let [schema `[:map ~@(remove nil? (map (fn [elt]
                                            (if-let [schema (:schema elt)]
                                              [(:id elt) schema]))
                                          elements))]
        schema (if schema-fn (schema-fn (rest schema)) schema)]
    {:type     :form
     :elements (mapv #(dissoc % :schema) elements)
     :schema   schema}))

(defn group
  [id elements]
  "Used to group elements "
  {:id id :type :group :elements elements})

(defn label
  ([title] (label title nil))
  ([title subtitle]
   {:type     :label
    :title    title
    :subtitle subtitle}))

(defn number [id & {:keys [label units min max required integer-only]}]
  (let [num-opts (assoc-if {} :min min :max max)]
    (assoc-if
      {}
      :type :number
      :label label
      :units units
      :id id
      :min min
      :max max
      :required required
      :integer-only integer-only
      :schema (let [int-schema [:int num-opts]]
                (if integer-only
                  int-schema
                  [:or int-schema [:double num-opts]])))))

(defn date [id & {:keys [format required]}]
  {:type     :date
   :id       id
   :format   format
   :required required
   ;; regex from https://stackoverflow.com/a/26972181/305149
   ;; I decided against a fancier version: #"^(?:(?:31(\/|-|\.)(?:0?[13578]|1[02]|(?:Jan|Mar|May|Jul|Aug|Oct|Dec)))\1|(?:(?:29|30)(\/|-|\.)(?:0?[1,3-9]|1[0-2]|(?:Jan|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec))\2))(?:(?:1[6-9]|[2-9]\d)?\d{2})$|^(?:29(\/|-|\.)(?:0?2|(?:Feb))\3(?:(?:(?:1[6-9]|[2-9]\d)?(?:0[48]|[2468][048]|[13579][26])|(?:(?:16|[2468][048]|[3579][26])00))))$|^(?:0?[1-9]|1\d|2[0-8])(\/|-|\.)(?:(?:0?[1-9]|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep))|(?:1[0-2]|(?:Oct|Nov|Dec)))\4(?:(?:1[6-9]|[2-9]\d)?\d{2})$"
   :schema   [:string {:re #"^(0?[1-9]|[12][0-9]|3[01])[\/\-](0?[1-9]|1[012])[\/\-]\d{4}$"}]})

(defn short-text [id label & {:keys [hint required min-length max-length]}]
  {:type       :short-text
   :id         id
   :label      label
   :hint       hint                                         ; e.g., grayed out text in text box
   :required   required
   :max-length max-length
   :schema     [:string (assoc-if {} :max max-length :min-length min-length)]})

(defn long-text [id label & {:keys [required min-length max-length]}]
  {:type       :long-text
   :id         id
   :label      label
   :required   required
   :max-length max-length
   :schema     [:string (assoc-if {} :max max-length :min-length min-length)]})

;; Constantine - this one
(defn multiple-choice
  "Display a multiple choice control.
    E.g.,
     (multiple-choice [{:id :a :label \"choice a\"} {:id :b :label \"choice b\"} ...])
     (multiple-choice [:a :b]) ;; labels will be automatically generated \"a\" and \"b\"
     (multiple-choice {:a \"choice a\", :b \"choice b\"})"
  [id items & {:keys [label required randomize show-other multiselect]
               :or   {required false, randomize false, show-other false}}]
  {:pre  [(keyword id)
          (sequential? items)
          (not (empty? items))
          (boolean? required)
          (boolean? randomize)
          (boolean? show-other)
          (or (nil? multiselect)
              (number? multiselect)
              (and (seq? multiselect) (= (count multiselect) 2)))]
   :post [(:schema %) (-> % :id keyword?) (-> % :items sequential?)]}
  (let [items (if (map? items)
                (normalize-id-map items #(hash-map :label %2))
                (mapv #(cond
                         (keyword? %) {:id % :label (name %)}
                         (map? %) (do (assert (some-> % :id keyword?) "multiple-choice item :id must be a keyword")
                                      (assoc % :label (or (:label %) (-> % :id name))))
                         :else (throw (ex-info "Invalid multiple-choice control choice"
                                               {:type :fatal-error :choice %}))) items))]
    (assoc-if {:type   :multiple-choice
               :id     id
               :items  items
               :schema [:and :keyword `[:enum ~@(map :id items)]]}
              :required required
              :randomize randomize                          ; don't bother
              :label (or label (keyword-to-label id))
              :show-other show-other                        ; don't bother
              :multiselect multiselect)))                   ; don't bother


(defn dropdown [id choices & {:keys [label required]
                              :or   {required false}}]
  {:pre [(keyword? id)
         (every? string? choices)
         (boolean? required)]}
  {:type     :dropdown
   :id       id
   :label    label
   :choices  choices
   :required required
   :schema   [:and :keyword `[:enum ~@(map :id choices)]]})

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
   :required required
   :schema   :boolean})

(defn rating [id & {:keys [label required levels icon]
                    :or   {levels 3, icon :star}}]
  {:type     :rating
   :id       id
   :required required
   :label    label
   :levels   levels
   :icon     icon
   :schema   [:int {:min 0 :max levels}]})

(defn ranking [id items & {:keys [label required levels]
                           :or   {required false}}]
  {:type     :ranking
   :id       id
   :items    items
   :label    label
   :levels   levels
   :required required
   :schema   [:int {:min 0 :max levels}]})
