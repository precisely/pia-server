(require '[clojure.edn :as edn] '[clojure.java.io :as io])
(defn load-edn
  "Load edn from an io/reader source (filename or io/resource)."
  [source]
  (with-open [r (io/reader source)]
    (edn/read (java.io.PushbackReader. r))))

(def envfile (load-edn (io/file "env.edn")))

(defproject pia-server "0.1.1-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [metosin/compojure-api "2.0.0-alpha30"]
                 [seancorfield/next.jdbc "1.1.588"]
                 [org.clojure/tools.logging "1.1.0"]
                 [environ "1.2.0"]
                 [com.github.precisely/longterm "0.1.1-SNAPSHOT"]

                 [korma "0.4.3"]
                 ;[org.postgresql/postgresql "9.2-1002-jdbc4"]
                 [org.xerial/sqlite-jdbc "3.7.15-M1"]
                 ]
  :plugins  [[lein-environ "1.2.0"]]
  :repositories [["jitpack" {:url "https://jitpack.io"
                             :username ~(:jetpack-auth-token envfile)
                             :password "." }]]
  :main pia-server.handler
  :ring {:handler pia-server.handler/app}
  :uberjar-name "server.jar"
  :uberwar-name "server.war"
  :profiles {:dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [ring/ring-mock "0.3.2"]]
                   :plugins      [[lein-ring "0.12.5"]]}}
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"])