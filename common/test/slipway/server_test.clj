(ns slipway.server-test
  (:require [clj-http.client :as client]
            [clojure.test :refer :all]
            [slipway.example.handler :as handler]
            [slipway.example.server.ssl :as server.ssl]
            [slipway.server :as slipway]))

(deftest server-test--happy-days
  (let [server (slipway/run-jetty handler/hello {})
        resp   (client/get "http://localhost:3000/")]
    (is (= 200 (:status resp)))
    (is (= handler/hello-html (:body resp)))
    (.stop server)))

(deftest server-test--ssl-happy-days
  (let [server (slipway/run-jetty handler/hello server.ssl/opts)
        resp   (client/get "https://localhost:3000/" {:insecure? true})]
    (is (= 200 (:status resp)))
    (is (= handler/hello-html (:body resp)))
    (is (thrown? Exception (client/get "http://localhost:3000/")))
    (.stop server)))