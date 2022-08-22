(ns slipway.server-test
  (:require [clj-http.client :as client]
            [clojure.test :refer :all]
            [slipway.example :as example]
            [slipway.server :as slipway]))

(deftest server-test--happy-days
  (let [server (slipway/run-jetty example/handler-hello {})
        resp   (client/get "http://localhost:3000/")]
    (is (= 200 (:status resp)))
    (is (= "Hello world" (:body resp)))
    (.stop server)))

(deftest server-test--ssl-happy-days
  (let [server (slipway/run-jetty example/handler-hello example/server-ssl)
        resp   (client/get "https://localhost:3000/" {:insecure? true})]
    (is (= 200 (:status resp)))
    (is (= "Hello world" (:body resp)))
    (is (thrown? Exception (client/get "http://localhost:3000/")))
    (.stop server)))