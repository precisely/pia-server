(ns pia-server.common.controls.form-test
  (:require [clojure.test :refer :all]
            [pia-server.common.controls.core :refer :all]
            [pia-server.common.controls.form :refer :all]
            [rapids :refer :all]
            [rapids.language.test :refer [branch]]))


(deftest <*form-test
  (binding [*ignore-permit* true]
    (branch [main (start! <*form [[(select :a [:a :b])
                                   (select :b [:a :b] :optional true)]])]
      "Optional fields"
      (is (= :running (:state main)))
      (branch [input  {:a :a}
               main   (continue! (:id main) :input input)
               result (:result main)]
        "Response"
        (is (= :complete (:state main)))
        (is (= input result))))
    (branch [main (start! <*form [[(select :a [:a :b])
                                   (select :b [:a :b])]])]
      "All required fields"
      (is (= :running (:state main)))
      (branch [input {:a :a}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when required values are not provided"
        (is (= false main)))
      (branch [input  {:a :a
                       :b :a}
               main   (continue! (:id main) :input input)
               result (:result main)]
        "Response"
        (is (= :complete (:state main)))
        (is (= input result))))))

(deftest multiselect-test
  (binding [*ignore-permit* true]
    (branch [main (start! <*form [[(multiselect :a [:a :b :c :d] :min 2 :max 3)]])]
      "Prompt"
      (branch [input  {:a [:a :b :c]}
               main   (continue! (:id main) :input input)
               result (:result main)]
        "Response"
        (is (= :complete (:state main)))
        (is (= input result)))
      (branch [input {:a [:a]}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when less than min values selected"
        (is (= false main)))
      (branch [input {:a [:a :b :c :d]}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when more than max values selected"
        (is (= false main))))))

(deftest number-test
  (binding [*ignore-permit* true]
    (branch [main (start! <*form [[(number :a :min 2 :max 9 :step 0.1)]])]
      "Prompt for double"
      (branch [input  {:a 3.4}
               main   (continue! (:id main) :input input)
               result (:result main)]
        "Response"
        (is (= :complete (:state main)))
        (is (= input result)))
      (branch [input {:a 0.9}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when less than min"
        (is (= false main)))
      (branch [input {:a 9.1}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when more than max"
        (is (= false main))))
    (branch [main (start! <*form [[(number :a :min 2 :max 9)]])]
      "Prompt for integer"
      (branch [input  {:a 3}
               main   (continue! (:id main) :input input)
               result (:result main)]
        "Response"
        (is (= :complete (:state main)))
        (is (= input result)))
      (branch [input {:a 1}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when less than min"
        (is (= false main)))
      (branch [input {:a 10}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when more than max"
        (is (= false main)))
      (branch [input {:a 3.2}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when decimal points"
        (is (= false main))))))

(deftest select-test
  (binding [*ignore-permit* true]
    (branch [main (start! <*form [[(select :a [:a :b])]])]
      "Prompt"
      (branch [input  {:a :a}
               main   (continue! (:id main) :input input)
               result (:result main)]
        "Response"
        (is (= :complete (:state main)))
        (is (= input result))))))

(deftest slider-test
  (binding [*ignore-permit* true]
    (branch [main (start! <*form [[(slider :a :min 2 :max 9 :step 0.1)]])]
      "Prompt for double"
      (branch [input  {:a 3.4}
               main   (continue! (:id main) :input input)
               result (:result main)]
        "Response"
        (is (= :complete (:state main)))
        (is (= input result)))
      (branch [input {:a 0.9}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when less than min"
        (is (= false main)))
      (branch [input {:a 9.1}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when more than max"
        (is (= false main))))
    (branch [main (start! <*form [[(slider :a :min 2 :max 9)]])]
      "Prompt for integer"
      (branch [input  {:a 3}
               main   (continue! (:id main) :input input)
               result (:result main)]
        "Response"
        (is (= :complete (:state main)))
        (is (= input result)))
      (branch [input {:a 1}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when less than min"
        (is (= false main)))
      (branch [input {:a 10}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when more than max"
        (is (= false main)))
      (branch [input {:a 3.2}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when decimal points"
        (is (= false main))))))

(deftest text-input-test
  (binding [*ignore-permit* true]
    (branch [main (start! <*form [[(text-input :a :min 1 :max 10)]])]
      "Prompt"
      (branch [input  {:a "text"}
               main   (continue! (:id main) :input input)
               result (:result main)]
        "Response"
        (is (= :complete (:state main)))
        (is (= input result)))
      (branch [input {:a ""}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when less than min"
        (is (= false main)))
      (branch [input {:a "testing string"}
               main  (try (continue! (:id main) :input input) (catch Exception _ false))]
        "Error when more than max"
        (is (= false main))))))

(deftest yesno-test
  (binding [*ignore-permit* true]
    (branch [main (start! <*form [[(yesno :a)]])]
      "Prompt"
      (branch [input  {:a true}
               main   (continue! (:id main) :input input)
               result (:result main)]
        "Response"
        (is (= :complete (:state main)))
        (is (= input result))))))