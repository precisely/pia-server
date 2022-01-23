(ns pia-server.handler-test
  (:require [clojure.test :refer :all]
            [pia-server.app :refer :all]
            [rapids :refer :all]))

(deftest foo-test
  (testing "Can start a flow"
    (let [run (start! foo)]
      (is (run? run))
      (is (= :running (:state run)))
      (is (= (:output run) '["hello"]))

      (testing "can continue a flow"
        (let [run (continue! (:id run) :input "cruel" :permit "the-permit")]
          (is (run? run))
          (is (= :complete (:state run)))
          (is (= (:output run) '["cruel world!"])))))))


;(def log-level-map
;  {:off     Level/OFF,
;   :severe  Level/SEVERE,
;   :warning Level/WARNING,
;   :info    Level/INFO,
;   :config  Level/CONFIG,
;   :fine    Level/FINE,
;   :finer   Level/FINER,
;   :finest  Level/FINEST,
;   :all     Level/ALL})
;
;(defn set-logger-level [logger-name level]
;  (.setLevel (Logger/getLogger logger-name) (or (log-level-map level) level)))
;
;(defn get-logger-level [logger-name]
;  (.getLevel (Logger/getLogger logger-name)))
;
;(defmacro with-loggers-at-level [[loggers level] & body]
;  `(let [loggers#    ~loggers
;         pre-levels# (map get-logger-level ~loggers)]
;     (doseq [logger# ~loggers] (set-logger-level logger# ~level))
;     (try
;       ~@body
;       (finally
;         (prn (interleave loggers# pre-levels#))
;         #_(doseq [[logger# level#] (interleave loggers# pre-levels#)]
;             (if level# (set-logger-level logger# level#)))))))
