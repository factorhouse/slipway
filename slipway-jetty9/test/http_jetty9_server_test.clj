(ns http-jetty9-server-test
  (:require [clojure.test :refer :all]
            [slipway.client :as client]
            [slipway.example :as example]))

(deftest chsk-post-login

  (try
    (example/start! [:http :hash-auth])

    (is (= "http://localhost:3000/"
           (client/do-get-login-redirect "http" "localhost" 3000 "/chsk" "admin" "admin")))

    (finally (example/stop!))))

(deftest custom-chsk-post-login

  (try
    (example/start! [:http :hash-auth :custom-ws])

    (is (= "http://localhost:3000/"
           (client/do-get-login-redirect "http" "localhost" 3000 "/wsx" "admin" "admin")))

    (finally (example/stop!))))