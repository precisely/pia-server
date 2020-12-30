(ns pia-server.util
  (:require [clojure.string :as str]))


(defn remove-nil-values [m]
  (into {} (filter #(let [[_ v] %] v) m)))

(defn nilable [p o] (or (nil? o) (p o)))

(defn heroku-db-url->jdbc-url [heroku-db-url]
  (let [uri (java.net.URI. heroku-db-url)
        [username password] (str/split (.getUserInfo uri) #":")]
    (str "jdbc:postgresql://"
         (.getHost uri) ":" (.getPort uri) (.getPath uri)
         "?user=" username "&password=" password)))
