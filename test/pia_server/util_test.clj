(ns pia-server.util-test
  (:require [clojure.test :refer :all]
            [pia-server.util :refer :all]))


(deftest assoc-if-test
  "Nil values are ignored"
  (is (= {:b 0 :c 1} (assoc-if {} :a nil :b 0 :c 1)))
  "No values are added"
  (is (= {} (assoc-if {} (fn [_ _] false) :a nil :b 0 :c 1)))
  "All values are added"
  (is (= {:a nil :b 0 :c 1} (assoc-if {} (fn [_ _] true) :a nil :b 0 :c 1))))