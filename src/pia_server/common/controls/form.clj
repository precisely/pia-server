(ns pia-server.common.controls.form
  (:require [malli.core :as m]
            [pia-server.common.controls.core :refer [defcontrol normalize-id-map]]
            [pia-server.util :refer [assoc-if]]
            [rapids :refer :all]))

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
                                              [(:id elt) {:optional (:optional elt)} schema]))
                                       elements))]
        schema (if schema-fn (schema-fn (rest schema)) schema)]
    {:type     :form
     :elements (mapv #(dissoc % :schema) elements)
     :schema   schema}))

(defn _convert-items
  "Converts various item formats to a common format.

  Args:
    items - [{:id :a :label \"choice a\"} {:id :b :label \"choice b\"} ...]
            [:a :b]
            {:a \"choice a\", :b \"choice b\"}

  Returns:
    [{:id :a :label \"choice a\"} {:id :b :label \"choice b\"} ...]
  "
  [items]
  {:pre  [(or (map? items) (sequential? items))]
   :post [(m/validate [:* [:map
                           [:id keyword?]
                           [:label string?]]] %)]}
  (if (map? items)
    (normalize-id-map items #(hash-map :label %2))
    (mapv #(cond
             (keyword? %) {:id % :label (name %)}
             (map? %) (do (assert (some-> % :id keyword?) "multiple-choice item :id must be a keyword")
                        (assoc % :label (or (:label %) (-> % :id name))))
             :else (throw (ex-info "Invalid multiple-choice control choice"
                            {:type :fatal-error :choice %}))) items)))

;;
;; Form Controls
;;
;; Below is a list of all existing form controls (following alphabetical order):
;;
;; multiselect
;; number
;; select
;; slider
;; text-input
;; yesno
;;


(defn multiselect
  "Display a multiselect input."
  [id items & {:keys [label optional info min max]
               :or   {optional false}}]
  {:pre  [(keyword id)
          (or (nil? label) (string? label))
          (boolean? optional)
          (or (nil? info) (string? info))
          (or (nil? min) (number? min))
          (or (nil? max) (number? max))]}
  (let [items (_convert-items items)]
    (assoc-if {:type     :multiselect
               :id       id
               :optional optional
               :schema   [:repeat (assoc-if {} :min min :max max)
                          [:and :keyword `[:enum ~@(map :id items)]]]}
      :label label
      :info info
      :items items)))

(defn number
  "Display a number input.

  Args:
    units - unit display
    min   - minimum value
    max   - maximum value
    step  - smallest number interval allowed (1 = integer)"
  [id & {:keys [label optional info units min max step]
         :or   {optional false, step 1}}]
  {:pre  [(keyword id)
          (or (nil? label) (string? label))
          (boolean? optional)
          (or (nil? info) (string? info))
          (or (nil? units) (string? units))
          (or (nil? min) (number? min))
          (or (nil? max) (number? max))
          (or (number? step) (= :any step))]}
  (assoc-if
    {:type     :number
     :id       id
     :optional optional
     :schema   (let [num-opts   (assoc-if {} :min min :max max)
                     int-schema [:int num-opts]]
                 (if (= 1 step)
                   int-schema
                   [:or int-schema [:double num-opts]]))}
    :label label
    :info info
    :units units
    :min min
    :max max
    :step step))

(defn select
  "Display a select input."
  [id items & {:keys [label optional info display]
               :or   {optional false, display :multiple-choice}}]
  {:pre  [(keyword? id)
          (or (nil? label) (string? label))
          (boolean? optional)
          (or (nil? info) (string? info))
          (contains? #{:multiple-choice :dropdown} display)]}
  (let [items (_convert-items items)]
    (assoc-if {:type     display
               :id       id
               :optional optional
               :schema   [:and :keyword `[:enum ~@(map :id items)]]}
      :label label
      :info info
      :items items)))

(defn slider
  "Display a slider input.

  Args:
    min   - minimum value
    max   - maximum value
    step  - smallest number interval allowed (1 = integer)
    add-plus - replaces the maximum value b with +b
               scale runs a -> +b instead of a -> b"
  [id & {:keys [label optional info min max step add-plus]
         :or   {optional false, step 1, add-plus false}}]
  {:pre  [(keyword id)
          (or (nil? label) (string? label))
          (boolean? optional)
          (or (nil? info) (string? info))
          (or (nil? min) (number? min))
          (or (nil? max) (number? max))
          (or (number? step) (= :any step))
          (boolean? add-plus)]}
  (assoc-if
    {:type     :slider
     :id       id
     :optional optional
     :schema   (let [num-opts   (assoc-if {} :min min :max max)
                     int-schema [:int num-opts]]
                 (if (= 1 step)
                   int-schema
                   [:or int-schema [:double num-opts]]))}
    :label label
    :info info
    :min min
    :max max
    :step step
    :add-plus add-plus))

(defn text-input
  "Display a text input.

  Args:
    placeholder - placeholder string (hint before user begins typing)
    min   - minimum length of text
    max   - maximum length of text
    lines - number of lines in text input"
  [id & {:keys [label optional info placeholder min max lines]
         :or   {optional false, placeholder "", lines 1}}]
  {:pre  [(keyword id)
          (or (nil? label) (string? label))
          (boolean? optional)
          (or (nil? info) (string? info))
          (string? placeholder)
          (or (nil? min) (number? min))
          (or (nil? max) (number? max))
          (number? lines)]}
  (assoc-if
    {:type     :text
     :id       id
     :optional optional
     :schema   [:string (assoc-if {} :max max :min min)]}
    :label label
    :info info
    :placeholder placeholder
    :min min
    :max max
    :lines lines))

(defn yesno
  "Display a yesno input."
  [id & {:keys [label optional info]
         :or   {optional false}}]
  {:pre  [(keyword id)
          (or (nil? label) (string? label))
          (boolean? optional)
          (or (nil? info) (string? info))]}
  (assoc-if
    {:type     :yesno
     :id       id
     :optional optional
     :schema   :boolean}
    :label label
    :info info))

;; UNUSED FORM ELEMENTS
;
;(defn fileupload [id & {:keys [label required]
;                        :or   {required true}}]
;  {:type     :fileupload
;   :id       id
;   :label    label
;   :required required})
;
;(defn phone-number [id & {:keys [label required country]
;                          :or   {country :us}}]
;  (throw (ex-info "Not yet implemented" {})))
;
;(defn rating [id & {:keys [label required levels icon]
;                    :or   {levels 3, icon :star}}]
;  {:type     :rating
;   :id       id
;   :required required
;   :label    label
;   :levels   levels
;   :icon     icon
;   :schema   [:int {:min 0 :max levels}]})
;
;(defn ranking [id items & {:keys [label required levels]
;                           :or   {required false}}]
;  {:type     :ranking
;   :id       id
;   :items    items
;   :label    label
;   :levels   levels
;   :required required
;   :schema   [:int {:min 0 :max levels}]})
;
;(defn date [id & {:keys [format required]}]
;  {:type     :date
;   :id       id
;   :format   format
;   :required required
;   ;; regex from https://stackoverflow.com/a/26972181/305149
;   ;; I decided against a fancier version: #"^(?:(?:31(\/|-|\.)(?:0?[13578]|1[02]|(?:Jan|Mar|May|Jul|Aug|Oct|Dec)))\1|(?:(?:29|30)(\/|-|\.)(?:0?[1,3-9]|1[0-2]|(?:Jan|Mar|Apr|May|Jun|Jul|Aug|Sep|Oct|Nov|Dec))\2))(?:(?:1[6-9]|[2-9]\d)?\d{2})$|^(?:29(\/|-|\.)(?:0?2|(?:Feb))\3(?:(?:(?:1[6-9]|[2-9]\d)?(?:0[48]|[2468][048]|[13579][26])|(?:(?:16|[2468][048]|[3579][26])00))))$|^(?:0?[1-9]|1\d|2[0-8])(\/|-|\.)(?:(?:0?[1-9]|(?:Jan|Feb|Mar|Apr|May|Jun|Jul|Aug|Sep))|(?:1[0-2]|(?:Oct|Nov|Dec)))\4(?:(?:1[6-9]|[2-9]\d)?\d{2})$"
;   :schema   [:string {:re #"^(0?[1-9]|[12][0-9]|3[01])[\/\-](0?[1-9]|1[012])[\/\-]\d{4}$"}]})
;
;(defn group
;  [id elements]
;  "Used to group elements "
;  {:id id :type :group :elements elements})
;
;(defn label
;  ([title] (label title nil))
;  ([title subtitle]
;   {:type     :label
;    :title    title
;    :subtitle subtitle}))