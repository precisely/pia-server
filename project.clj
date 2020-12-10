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

(def env
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

(defproject pia-server "0.1.2-SNAPSHOT"
  :description "Precisely Intelligent Agent"
  :dependencies [[org.clojure/clojure "1.10.0"]

                 [envvar "1.1.1"]

                 ;; application-specific
                 [precisely/rapids "0.3.1-SNAPSHOT"]
                 [clojure.java-time "0.3.2"]
                 [org.clojure/core.async "1.3.610"]

                 ;; web server stuff
                 [metosin/compojure-api "2.0.0-alpha30"]
                 [metosin/jsonista "0.2.7"]
                 [ring/ring-jetty-adapter "1.8.2"]
                 [ring.middleware.conditional "0.2.0"]
                 [buddy/buddy-auth "2.2.0"]

                 ;; logger
                 [com.taoensso/timbre "5.1.0"]
                 [com.fzakaria/slf4j-timbre "0.3.20"]
                 [ring-logger "1.0.1"]

                 ;; database
                 [org.postgresql/postgresql "42.2.10"]
                 [seancorfield/next.jdbc "1.1.613"]
                 [honeysql "1.0.444"]
                 [danlentz/clj-uuid "0.1.9"]
                 [hikari-cp "2.13.0"]

                 ;; HAPI
                 [ca.uhn.hapi.fhir/hapi-fhir-base "5.1.0"]
                 [ca.uhn.hapi.fhir/hapi-fhir-structures-r4 "5.1.0"]
                 [ca.uhn.hapi.fhir/hapi-fhir-validation "5.1.0"]
                 [ca.uhn.hapi.fhir/hapi-fhir-validation-resources-r4 "5.1.0"]]
  :repositories {"precisely" {:url        "s3p://precisely-maven-repo/"
                              :username   ~(env :maven-repo-aws-access-key-id)
                              :passphrase ~(env :maven-repo-aws-access-key-secret)}}
  :plugins [[lein-pprint "1.3.2"]
            [s3-wagon-private "1.3.4"]]
  :main main
  :source-paths ["src"]
  :resource-paths ["src/resources"]
  :ring {:handler pia-server.core/app
         :port ~(read-string (env :port))
         :auto-refresh? true
         :auto-reload? true
         :refresh-paths ["src"]
         :reload-paths ["src"]}
  :uberjar-name "pia-server.jar"
  :profiles {:dev     {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                      [ring-server "0.5.0"]
                                      [ring/ring-mock "0.3.2"]]
                       :plugins      [[lein-ring "0.12.5"]
                                      [org.clojars.timvisher/lein-checkout "0.4.2"]]}
             :uberjar {:aot :all}}
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"])
