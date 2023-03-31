
(defproject pia-server "0.2.0-SNAPSHOT"
  :description "Precisely Intelligent Agent"
  :min-lein-version "2.9.7"
  :dependencies [[org.clojure/clojure "1.11.1"]

                 [envvar "1.1.2"]

                 ;; application support
                 [precisely/rapids "0.12.5-SNAPSHOT"]
                 [precisely/rapids-active-doc "LATEST"]
                 [clojure.java-time "1.2.0"]
                 [org.clojure/core.async "1.6.673"]
                 [camel-snake-kebab "0.4.3"]
                 [potemkin "0.4.6"]
                 [org.clojure/data.json "2.4.0"]
                 [siili/humanize "0.1.1"]
                 [metosin/malli "0.10.1"]

                 ;; fake
                 [eigenhombre/namejen "0.1.23"]

                 ;; web server stuff
                 [javax.servlet/javax.servlet-api "4.0.1"]
                 [ring-server "0.5.0"]
                 [ring/ring-jetty-adapter "1.9.6"]
                 [metosin/ring-swagger "0.26.2"]
                 [ring.middleware.conditional "0.2.0"]
                 [compojure "1.7.0"]
                 [metosin/jsonista "0.3.7"]
                 [metosin/compojure-api "2.0.0-alpha31"]
                 [buddy/buddy-auth "3.0.323"]
                 [ring-cors "0.1.13"]

                 ;; logger
                 [com.taoensso/timbre "6.1.0"]
                 [com.fzakaria/slf4j-timbre "0.3.21"]
                 [ring-logger "1.1.1"]

                 ]
  :repositories {"precisely" {:url        "s3p://precisely-maven-repo/"
                              :username   :env/maven_repo_aws_access_key_id
                              :passphrase :env/maven_repo_aws_access_key_secret}}
  :plugins [[lein-pprint "1.3.2"]
            [s3-wagon-private "1.3.5"]]

  :main main
  :source-paths ["src"]
  :resource-paths ["resources"]
  :ring {:handler       pia-server.app/app
         :port          :env/port
         :auto-refresh? true
         :auto-reload?  true
         :refresh-paths ["src"]
         :reload-paths  ["src"]}
  :uberjar-name "pia-server.jar"
  :profiles {:dev     {:dependencies [[ring/ring-mock "0.4.0"]
                                      [philoskim/debux "0.8.2"]
                                      [org.clojure/core.match "1.0.1"]]
                       :plugins      [[lein-checkouts "1.1.0"]
                                      [lein-ring "0.12.5"]
                                      [lein-cloverage "1.1.2"]
                                      [org.clojars.timvisher/lein-checkout "0.4.2"]]}
             :uberjar {:aot :all}}
  :repl-options {:init-ns pia-server.support.repl}
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"])
