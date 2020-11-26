(ns pia-server.handler-test
  (:require [cheshire.core :as cheshire]
            [clojure.test :refer :all]
            [pia-server.core :refer :all]
            [longterm :refer :all]
            [ring.mock.request :as mock]
            [pia-server.flows.cfsdemo :refer :all]))

(defn parse-body [body]
  (cheshire/parse-string (slurp body) true))

;(deftest a-test
;  (testing "Test GET request to /hello?name={a-name} returns expected response"
;    (let [response (app (-> (mock/request :get  "/api/runs/123-123-123")))
;          body     (parse-body (:body response))]
;      (is (= (:status response) 200))
;      (is (= (:result body) nil)))))

(deftest foo-test
  (testing "Can start a flow"
    (let [run (start! foo)]
      (is (run-in-state? run :suspended))
      (is (= (:response run) '["hello"]))

      (testing "can continue a flow"
        (let [run (continue! (:id run) :foo "cruel")]
          (is (run-in-state? run :complete))
          (is (= (:response run) '["cruel world!"])))))))

(deftest home-test
  (testing "Attempting running the Main"
    (printf (str "before starting run"))
    (let [run (start! home fatigue-flows)]
      (printf (str "this is just before the response collection"))
      (printf (str (:response run) " yes\n"))
      (is (run-in-state? run :suspended))
      (testing "Answering motivation question"
        (printf (str (:id run) "\n"))
        (let [run (continue! (:id run))]
          (printf (str (:response run) " motivation"))
          (is (run-in-state? run :suspended))
          ))
      )))