(ns pia-server.app-test
  (:require [clojure.test :as test]
            [pia-server.app :refer :all]
            [pia-server.test :refer :all]
            [cheshire.core :refer [generate-string parse-string]]
            [ring.mock.request :as mock]
            [rapids :refer :all]))

;; (test/run-tests)

(defn json-request [method resource & {:keys [params body] :or {params []}}]
  (let [response (app
                   (-> (mock/request method resource)
                       (mock/content-type "application/json")
                       (mock/body (generate-string body))))]
    (update response :body #(-> % slurp (parse-string keyword)))))

(deflow simple-flow []
  (*> "hi")
  (let [result (<*)]
    (*> (str "I received " result))))

(deflow error-flow []
  (ex-info "Ooops!" {:type :oops}))

(deftest AppExceptionTest
  (binding [flows {:simple-flow #'simple-flow
                   :error-flow  #'error-flow}]
    (testing "input error while starting a flow"
      (let [{status :status, body :body} (json-request :post "/api/runs/simple-flow" :body "not-an-array")]
        (is (= status 400))
        (is (= (:type body) "input"))))

    (testing "starting a flow"
      (let [{status :status, {id :id, state :state, next_id :next_id} :body}
            (json-request :post "/api/runs/simple-flow" :body [])]
        (is (= status 200))
        (is (string? id))
        (is (= id next_id))
        (is (= state "suspended"))

        (testing "continuing a flow"
          (let [{status :status, {id :id, state :state, next_id :next-id, :as body} :body}
                (json-request :post (str "/api/runs/" next_id "/continue")
                              :body {:data "FOO"})]
            (is (= status 200))
            (is (= state "complete"))
            (is (nil? next_id))
            (is (= (:response body) ["I received FOO"]))))))))
