(ns pia-server.apps.preop.common-test
  (:require [clojure.test :refer :all]
            [rapids :refer :all]
            [pia-server.apps.preop.common :refer :all]
            [rapids.language.test :refer [with-test-env branch keys-match flush-cache!] :as rt]
            [rapids.active-doc :as adoc]
            [pia-server.common.controls.form :refer :all]
            [java-time :as t]))

(deftest valid-time?-test
  "Not in time frame"
  (is (= (valid-time? (t/minus (t/instant) (t/days 2)) (t/days 1)) false))
  "In time frame"
  (is (= (valid-time? (t/minus (t/instant) (t/days 7)) (t/weeks 2)) true))
  "No timestamp provided"
  (is (= (valid-time? nil (t/weeks 2)) false))
  "No interval provided"
  (is (= (valid-time? nil nil) true)))

(deftest get-cached-data-test
  (branch [doc  (adoc/create!)
           data (get-cached-data doc [:key] :interval (t/days 2))]
    "Data doesn't exist"
    (is (= nil data)))

  (branch [ts   (t/minus (t/instant) (t/weeks 1))
           doc  (adoc/create! :data {:foo 1 :timestamp ts})
           data (get-cached-data doc [] :interval (t/days 1))]
    "Data is expired"
    (is (= nil data)))

  (branch [ts   (t/minus (t/instant) (t/weeks 1))
           doc  (adoc/create! :data {:foo 1 :timestamp ts})
           data (get-cached-data doc [] :interval (t/days 1))]
    "Data is expired"
    (is (= nil data)))

  (branch [ts       (t/minus (t/instant) (t/days 1))
           raw-data {:bar 1 :timestamp ts}
           doc      (adoc/create! :data raw-data)
           data     (get-cached-data doc [] :interval (t/weeks 1))]
    "Data is within interval"
    (is (= raw-data data)))

  (branch [ts       (t/minus (t/instant) (t/days 1))
           raw-data {:bar 1 :timestamp ts}
           doc      (adoc/create! :data raw-data)
           data     (get-cached-data doc [])]
    "Data has no interval"
    (is (= raw-data data)))

  (branch [raw-data {:bar 1}
           doc      (adoc/create! :data raw-data)
           data     (get-cached-data doc [] :interval (t/weeks 1))]
    "Timestamp doesn't exist"
    (is (= nil data))))

(deftest update-doc!-test
  (branch [doc      (adoc/create!)
           raw-data {:foo {:bar "bar"}}
           data     (update-doc! doc [:foo] raw-data)]
    "No existing data"
    (is (= raw-data (dissoc data :timestamp)))
    (is (= (adoc/get-data doc [:foo]) data)))

  (branch [doc      (adoc/create! :data {:foo {:bar "bar"}})
           raw-data {:bar "baz"}
           data     (update-doc! doc [:foo] raw-data)]
    "Add data using map"
    (is (= raw-data (dissoc data :timestamp)))
    (is (= (adoc/get-data doc [:foo]) data)))

  (branch [doc  (adoc/create! :data {:foo {:bar "bar"}})
           data (update-doc! doc [:foo :bar] "baz")]
    "Add data using value"
    (is (= "baz" data))
    (is (= (adoc/get-data doc [:foo :bar]) data))))


(deftest module-test
  (branch [doc  (adoc/create!)
           main (start! module [doc [:test] `(do {:out 1})])
           data (adoc/get-data doc [:test])]
    "No existing data"
    (is (= :complete (:state main)))
    (is (= data (:result main)))
    (is (= {:out 1} (dissoc data :timestamp))))

  (branch [doc  (adoc/create! :data {:test {:out 2}})
           main (start! module [doc [:test] `(do ~{:out 1})])
           data (adoc/get-data doc [:test])]
    "Has existing data, no interval"
    (is (= :complete (:state main)))
    (is (= data (:result main)))
    (is (= {:out 2} (dissoc data :timestamp))))

  (branch [doc  (adoc/create! :data {:test {:out 2}})
           main (start! module [doc [:test] `(do ~{:out 1}) :interval (t/days 1)])
           data (adoc/get-data doc [:test])]
    "Has existing data, expired"
    (is (= :complete (:state main)))
    (is (= data (:result main)))
    (is (= {:out 1} (dissoc (adoc/get-data doc [:test]) :timestamp))))
  )

(deftest <*ask-test
  (branch [doc  (adoc/create!)
           q1   (yesno :q1)
           q2   (multiple-choice :q2 [:a :b])
           main (start! <*ask [doc [:test] [q1 q2]])]
    "Asks questions"
    (is (= :running (:state main)))
    (branch [main (continue! (:id main) :input {:q1 true :q2 :a})]
      "Answers questions"
      (is (= :complete (:state main)))
      (is (= {:q1 true :q2 :a} (:result main)))
      (branch [q3   (number :q3)
               main (start! <*ask [doc [:test] [q3]])
               main (continue! (:id main) :input {:q3 1})
               data (adoc/get-data doc [:test :questions])]
        "Merges answers"
        (is (= :complete (:state main)))
        (is (contains? data :q1))
        (is (contains? data :q2))
        (is (contains? data :q3))))))