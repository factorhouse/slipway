(ns slipway.server-test
  (:require [clojure.test :refer :all]
            [clj-http.client :as client]
            [slipway.server :as slipway]))

(defn handler [_]
  {:status 200 :body "Hello world"})

(deftest server-test--happy-days
  (let [server (slipway/run-jetty handler {})
        resp   (client/get "http://localhost:3000/")]
    (is (= 200 (:status resp)))
    (is (= "Hello world" (:body resp)))
    (.stop server)))

(def ssl-opts
  {:ssl?            true
   :http?           false
   :ssl-port        3000
   :keystore        "dev-resources/my-keystore.jks"
   :keystore-type   "PKCS12"
   :key-password    "password"
   :truststore      "dev-resources/my-truststore.jks"
   :trust-password  "password"
   :truststore-type "PKCS12"})

(deftest server-test--ssl-happy-days
  (let [server (slipway/run-jetty handler ssl-opts)
        resp   (client/get "https://localhost:3000/" {:insecure? true})]
    (is (= 200 (:status resp)))
    (is (= "Hello world" (:body resp)))
    (is (thrown? Exception (client/get "http://localhost:3000/")))
    (.stop server)))