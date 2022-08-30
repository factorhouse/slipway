(ns slipway.server-test
  (:require [clj-http.client :as client]
            [clojure.test :refer :all]
            [slipway.example.handler :as handler]
            [slipway.example.server.ssl :as ssl]
            [slipway.server :as slipway]))

(deftest http-server-happy-days
  (let [server (slipway/start-jetty handler/hello {})
        resp   (client/get "http://localhost:3000/")]
    (is (= 200 (:status resp)))
    (is (= handler/hello-html (:body resp)))
    (slipway/stop-jetty server)))

(deftest https-server-happy-days
  (let [server (ssl/server)
        resp   (client/get "https://localhost:3000/" {:insecure? true})]
    (is (= 200 (:status resp)))
    (is (= handler/hello-html (:body resp)))
    (is (thrown? Exception (client/get "http://localhost:3000/")))
    (slipway/stop-jetty server)))