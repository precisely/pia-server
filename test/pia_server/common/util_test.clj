(ns pia-server.common.util-test
  (:require [clojure.test :refer :all]
            [pia-server.common.util :refer :all]))

 (deftest unit-tests
        (letfn [(test-range-case [target]
                                 (range-case target
                                             [0 < 1] :strongly-disagree
                                             [< 2]   :disagree
                                             [< 3]   :neutral
                                             [< 4]   :agree
                                             [5]     :strongly-agree
                                             42      :the-answer
                                             :else   :do-not-care))]
      (is (= (test-range-case 0) :strongly-disagree))
      (is (= (test-range-case 0.5) :strongly-disagree))
      (is (= (test-range-case 1) :disagree))
      (is (= (test-range-case 1.5) :disagree))
      (is (= (test-range-case 2) :neutral))
      (is (= (test-range-case 2.5) :neutral))
      (is (= (test-range-case 3) :agree))
      (is (= (test-range-case 3.5) :agree))
      (is (= (test-range-case 4) :strongly-agree))
      (is (= (test-range-case 4.5) :strongly-agree))
      (is (= (test-range-case 5) :strongly-agree))
      (is (= (test-range-case 42) :the-answer))
      (is (= (test-range-case -1) :do-not-care))))

(deftest )