(ns http-jetty9-server-test
  (:require [clojure.test :refer :all]
            [slipway.client :as client]
            [slipway.example :as example]
            [slipway.example.app :as app]))

(deftest chsk-post-login

  (try
    (example/http-hash-server)

    (is (= "http://localhost:3000/"
           (client/do-get-login-redirect "http" "localhost" 3000 "/chsk" "admin" "admin")))

    (finally (example/stop-server!))))

(deftest custom-chsk-post-login

  (try
    (example/start-server! (app/handler) (assoc example/hash-opts :slipway.handler/ws-path "/wsx"))

    (is (= "http://localhost:3000/"
           (client/do-get-login-redirect "http" "localhost" 3000 "/wsx" "admin" "admin")))

    (finally (example/stop-server!))))