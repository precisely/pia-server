(ns pia-server.app-test
  (:require [pia-server.server :refer :all]
            [pia-server.test :refer :all]
            [cheshire.core :refer [generate-string parse-string]]
            [ring.mock.request :as mock]
            [rapids :refer :all]))

;; (test/run-tests)

(defn json-request [method resource & {:keys [params body] :or {params []}}]
  (let [response (server
                   (-> (mock/request method resource)
                       (mock/content-type "application/json")
                       (mock/body (generate-string body))))]
    (update response :body #(try (some-> % slurp (parse-string keyword))
                                 (catch Exception e)))))

(deflow simple-flow []
  (>* "hi")
  (let [result (<*)]
    (>* (str "I received " result))))

(deflow error-flow []
  (ex-info "Ooops!" {:type :oops}))

(defdbtest AppExceptionTest
  (binding [flows {:simple-flow #'simple-flow
                   :error-flow  #'error-flow}]
    (testing "input error while starting a flow"
      (let [{status :status, body :body} (json-request :post "/api/runs/start/simple-flow" :body "not-an-array")]
        (is (= status 400))
        (is (= (:type body) "input"))))

    (testing "starting a flow"
      (let [{status :status, {id :id, state :state} :body}
            (json-request :post "/api/runs/start/simple-flow" :body [])]
        (is (= status 200))
        (is (string? id))
        (is (= state "running"))

        (testing "continuing a flow"
          (let [{status :status, {id :id, state :state, :as body} :body}
                (json-request :post (str "/api/runs/continue/" id)
                              :body {:input "FOO"})]
            (is (= status 200))
            (is (= state "complete"))
            (is (= (:output body) ["I received FOO"]))))))))
