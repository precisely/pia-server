(ns pia-server.db.core
  (:require [clojure.string :as str]
            [envvar.core :as envvar :refer [env keywordize]]))


(defn heroku-db-url->jdbc-url [heroku-db-url]
  (let [uri (java.net.URI. heroku-db-url)
        [username password] (str/split (.getUserInfo uri) #":")]
    (str "jdbc:postgresql://"
         (.getHost uri) ":" (.getPort uri) (.getPath uri)
         "?user=" username "&password=" password)))

(defn jdbc-url [db]
  {:pre [(keyword? db)]}
  (let [name-db (name db)
        name-db-underscore (str/replace name-db "-" "_")
        heroku-indirection-var (keyword (str "herokudb-env-var-" name-db))
        jdbc-var (keyword (str "db-" name-db))]
    (cond
      ;; if Heroku indirection environment variable set, dereference and return:
      (@env heroku-indirection-var)
      (heroku-db-url->jdbc-url (@env
                                (-> @env
                                    (get heroku-indirection-var)
                                    keywordize)))
      ;; if JDBC regular environment variable set, return itself:
      (@env jdbc-var)
      (@env jdbc-var)
      ;; else no known database environment variable set, return a reasonable default:
      :else
      (str "jdbc:postgresql://localhost:5432/" name-db-underscore
           "?user=" (System/getProperty "user.name")
           "&password="))))
