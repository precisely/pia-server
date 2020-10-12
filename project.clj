(defproject pia-server "0.1.0-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [metosin/compojure-api "2.0.0-alpha30"]
                 [com.github.precisely/longterm "0.1.0"]]
  :repositories [["jitpack" { :url "https://jitpack.io"
                             :username ~(System/getenv "JETPACK_AUTH_TOKEN")
                             :password "." }]]
  :ring {:handler pia-server.handler/app}
  :uberjar-name "server.jar"
  :profiles {:dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [ring/ring-mock "0.3.2"]]
                   :plugins      [[lein-ring "0.12.5"]]}})
