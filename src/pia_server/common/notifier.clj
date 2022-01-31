(ns pia-server.common.notifier
  (:require [clojure.core.async :as async]
            [clojure.data.json :as json]
            [rapids :refer :all]))


;;; --- SSE channel registry

(defonce sse-registry (atom {}))

;; TODO: This currently only supports one client connection per entity-id.
(defn sse-register [entity-type entity-id channel]
  (swap! sse-registry assoc-in [entity-type entity-id] channel))

(defn sse-unregister [entity-type entity-id]
  (swap! sse-registry update entity-type dissoc entity-id))

;; TODO: There should be a thread sending heartbeats to every client registered
;; with SSE every 30 seconds. This solves potential Heroku disconnects, and can
;; detect dropped connections and clean them up.


;;; --- main interface function

(defn notify [entity message & {:keys [run-id]
                                ;;:or   {run-id (current-run :id)}
                                }]
  {:pre [(map? entity) (contains? entity :id) (contains? entity :type)]}
  (let [payload (str "data:" (json/write-str message) "\n\n")
        msg (str {:message message, :run-id run-id})]
    (when-let [ch (get-in @sse-registry [(:type entity) (:id entity)])]
      (async/go
        ;; try to send the message; if the channel was closed, clean up
        (when-not (async/>! ch payload)
          (sse-unregister (:type entity) (:id entity)))))
    (println ">>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>>")
    (println "SENDING NOTIFICATION => " (:type entity) (:id entity) msg)))
