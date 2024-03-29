(ns pia-server.common.notifier
  (:require [clojure.core.async :as async]
            [clojure.data.json :as json]
            [rapids :refer :all]
            [taoensso.timbre :as log]))


;;; --- SSE channel registry

(defonce sse-registry (atom {}))

(defn sse-register [entity-type entity-id channel]
  (swap! sse-registry update-in [entity-type entity-id]
         (fn [channels]
           (conj (if (empty? channels) #{} channels) channel))))

(defn sse-unregister [entity-type entity-id channel]
  (swap! sse-registry update-in [entity-type entity-id]
         (fn [channels]
           (remove #{channel} channels)))
  ;; remove the entry altogether if all channels are closed
  (when (empty? (get-in @sse-registry [entity-type entity-id]))
    (swap! sse-registry update entity-type dissoc entity-id))
  @sse-registry)

;; TODO: There should be a thread sending heartbeats to every client registered
;; with SSE every 30 seconds. This solves potential Heroku disconnects, and can
;; detect dropped connections and clean them up.


;;; --- main interface function

(defn notify [entity message & {:keys [run-id]
                                :or   {run-id (try (current-run :id) (catch Exception e) (catch AssertionError e))}
                                }]
  {:pre [(map? entity) (contains? entity :id) (contains? entity :type)]}
  (let [payload (str "data:" (json/write-str {:message message, :run-id (str run-id)}) "\n\n")
        {type :type id :id} entity]
    (when-let [channels (get-in @sse-registry [type id])]
      (async/go
        ;; try to send the message to all channels; if the channel was closed, clean up
        (doseq [ch channels]
          (when-not (async/>! ch payload)
            (sse-unregister type id ch)))))
    (log/info "Sending notification" {:type type :id id :message message :run-id run-id})))
