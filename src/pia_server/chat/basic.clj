(ns pia-server.chat.basic
  (:require [rapids :refer :all]
            [clojure.string :as string]
            [clojure.spec.alpha :as s])
  (:import (java.util UUID)))

(defn make-choices
  [style? & kvs]
  (let [has-style (even? (count kvs))
        style     (if has-style style? :buttons)
        choices   (map #(let [[k v] %] {:id k, :text v})
                       (partition 2
                                  (if has-style kvs (cons style? kvs))))]
    (assert (#{:buttons :list} style) (str "Invalid choice style provided " style))
    {:type    :choices
     :style   style
     :choices choices}))

(deflow choices
  "Shows choices to the user, suspending until an element is pressed.
  An optional style parameter can be passed, which must be :buttons or :list.
  Usage (choices :yes \"Oh Yes!\" :no \"No, thank you\")"
  [style? & kvs]
  (let [permit (str (UUID/randomUUID))]
    (*> (assoc (apply make-choices style? kvs) :permit permit))
    (keyword (<* :permit permit))))

(defn text [& strings] (*> {:type :text, :text (string/join " " strings)}))

(defn flabel [] (throw 'not-implemented))

(defn finput [type id & {:keys [name label hint]}]
  {:pre [(#{:button :checkbox :color :date :datetime-local :email :file :hidden :image :month :number :password :radio
            :range :reset :search :submit :tel :text :time :url :week} type)
         (keyword? id)]}
    {:type type :id id :name name :label label :hint hint})

(deflow form
  "Shows an HTML form"
  [id & elts]
  (let [permit (str (UUID/randomUUID))]
    (*> {:type :form, :id id, :elements elts})
    (<* :permit permit)))