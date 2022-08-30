(ns slipway.jaas-test
  (:require [clojure.test :refer :all]
            [slipway.client :as client]
            [slipway.example.server.jaas :as jaas]
            [slipway.server :as slipway])
  (:import (java.net ConnectException)
           (javax.net.ssl SSLException)))

(deftest login

  (let [server (jaas/server)]

    ;; wrong port / scheme
    (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 ""))))
    (is (thrown? SSLException (:status (client/do-get "https" "localhost" 3000 ""))))

    ;; does not require authentication
    (is (= 200 (:status (client/do-get "http" "localhost" 3000 "/up"))))

    ;; requires authentication
    (is (= 303 (:status (client/do-get "http" "localhost" 3000 ""))))
    (is (= 303 (:status (client/do-get "http" "localhost" 3000 "/"))))
    (is (= 303 (:status (client/do-get "http" "localhost" 3000 "/user"))))

    ;; auth redirect goes to expected login page
    (is (= "http://localhost:3000/login" (get-in (client/do-get "http" "localhost" 3000 "") [:headers "Location"])))

    ;; login / login-retry don't redirect
    (is (= 200 (:status (client/do-get "http" "localhost" 3000 "/login"))))
    (is (= 200 (:status (client/do-get "http" "localhost" 3000 "/login-retry"))))

    ;; jetty nukes session and redirects to /login regardless
    (is (= 303 (:status (client/do-get "http" "localhost" 3000 "/logout"))))

    (slipway/stop-jetty server)))
