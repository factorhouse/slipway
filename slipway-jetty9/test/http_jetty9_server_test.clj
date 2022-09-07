(ns http-jetty9-server-test
  (:require [clojure.test :refer :all]
            [slipway.client :as client]
            [slipway.example :as example]))

(deftest chsk-post-login

  (try
    (example/http-hash-server)

    (is (= "http://localhost:3000/"
           (client/do-get-login-redirect "http" "localhost" 3000 "/chsk" "admin" "admin")))

    (finally (example/stop-server!))))