;;; XXX: Dirty workaround to allow referencing environment variables in
;;; project.clj. This approach echoes the envvar library (and, indeed,
;;; reimplements it). See https://github.com/gcv/envvar. This is necessary
;;; because Leiningen middleware is resolved too late to use environment
;;; variables in some places in the project map. Specifically, repository
;;; resolution happens too early.
;;;
;;; Note that Leiningen's built-in repository credentials mechanism is not used
;;; here because GPG setup for credentials files is inconsistent with how all
;;; other credentials to third-party services are stored.

(require '[clojure.java.io :as io]
  '[clojure.string :as str])

(def env-map
  (->> (merge
         (into {} (System/getenv))
         (into {} (System/getProperties))
         (let [env-file (io/file ".env")]
           (if (.exists env-file)
             (let [props (java.util.Properties.)]
               (.load props (io/input-stream env-file))
               props)
             {})))
    (map (fn [[k v]] [(-> (str/lower-case k)
                        (str/replace "_" "-")
                        (str/replace "." "-")
                        (keyword))
                      v]))
    (into {})))

(defn env
  ([k] (env k nil))
  ([k default] (or (env-map k) default (throw (ex-info (str "Environment key not defined: " k) {:variable k})))))

(defproject pia-server "0.1.3-SNAPSHOT"
  :description "Precisely Intelligent Agent"
  :dependencies [[org.clojure/clojure "1.10.3"]

                 [envvar "1.1.2"]

                 ;; application support
                 [precisely/rapids "0.8.4"]
                 [clojure.java-time "0.3.2"]
                 [org.clojure/core.async "1.3.610"]
                 [camel-snake-kebab "0.4.2"]
                 [potemkin "0.4.5"]
                 [org.clojure/data.json "1.0.0"]
                 [luposlip/json-schema "0.2.9"]
                 [com.taoensso/truss "1.6.0"]               ; have/have!/have?
                 [metosin/malli "0.8.0"]

                 ;; fake
                 [eigenhombre/namejen "0.1.23"]

                 ;; web server stuff
                 [javax.servlet/javax.servlet-api "4.0.1"]
                 [ring-server "0.5.0"]
                 [ring/ring-jetty-adapter "1.8.2"]
                 [ring.middleware.conditional "0.2.0"]
                 [metosin/compojure-api "2.0.0-alpha31"]
                 [metosin/jsonista "0.2.7"]
                 [buddy/buddy-auth "2.2.0"]
                 [ring-cors "0.1.13"]

                 ;; logger
                 [com.taoensso/timbre "5.1.0"]
                 [com.fzakaria/slf4j-timbre "0.3.20"]
                 [ring-logger "1.1.1"]

                 ]
  :repositories {"precisely" {:url        "s3p://precisely-maven-repo/"
                              :username   ~(env :maven-repo-aws-access-key-id)
                              :passphrase ~(env :maven-repo-aws-access-key-secret)}}
  :plugins [[lein-pprint "1.3.2"]
            [s3-wagon-private "1.3.4"]]
  :main main
  :source-paths ["src"]
  :resource-paths ["resources"]
  :ring {:handler       pia-server.app/app
         :port          ~(read-string (env :port "8080"))
         :auto-refresh? true
         :auto-reload?  true
         :refresh-paths ["src"]
         :reload-paths  ["src"]}
  :uberjar-name "pia-server.jar"
  :profiles {:dev     {:dependencies [[ring/ring-mock "0.4.0"]
                                      [org.clojure/core.match "1.0.0"]]
                       :plugins      [[lein-checkouts "1.1.0"]
                                      [lein-ring "0.12.5"]
                                      [lein-cloverage "1.1.2"]
                                      [org.clojars.timvisher/lein-checkout "0.4.2"]]}
             :uberjar {:aot :all}}
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"])
