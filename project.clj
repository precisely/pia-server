(require '[clojure.edn :as edn] '[clojure.java.io :as io] '[clojure.string :as str])
(defn load-edn
  "Load edn from an io/reader source (filename or io/resource)."
  [source]
  (with-open [r (io/reader source)]
    (edn/read (java.io.PushbackReader. r))))
(def envfile (load-edn (io/file "env.edn")))
(defn env
  ([key] (env key nil))
  ([key default]
  (let [env-var (str/replace (str/upper-case (.getName key)) "-" "_")]
    (or (System/getenv env-var)
      (key envfile)
      default))))

(defproject pia-server "0.1.1-SNAPSHOT"
  :description "FIXME: write description"
  :dependencies [[org.clojure/clojure "1.10.0"]
                 [metosin/compojure-api "2.0.0-alpha30"]
                 [seancorfield/next.jdbc "1.1.588"]
                 [org.clojure/tools.logging "1.1.0"]
                 [org.postgresql/postgresql "42.2.10"]
                 [hikari-cp "2.13.0"]
                 [environ "1.2.0"]
                 [com.github.precisely/longterm "0.1.2"]]
  :plugins [[lein-environ "1.2.0"]]
  :repositories [["jitpack" {:url      "https://jitpack.io"
                             :username ~(env :jetpack-auth-token)
                             :password "."}]]
  :main pia-server.handler
  :ring {:handler pia-server.handler/app}
  :uberjar-name "server.jar"
  :profiles {:dev {:dependencies [[javax.servlet/javax.servlet-api "3.1.0"]
                                  [ring/ring-mock "0.3.2"]]
                   :plugins      [[lein-ring "0.12.5"]]
                   :env          {:db-username     ~(env :db-username (System/getProperty "user.name"))
                                  :password        ~(env :db-password "")
                                  :database-name   ~(env :db-name "pia_runstore")
                                  :server-name     ~(env :db-server-name "localhost")
                                  :port-number     ~(env :db-port "5432")}}}
  :jvm-opts ["-Dclojure.tools.logging.factory=clojure.tools.logging.impl/slf4j-factory"])