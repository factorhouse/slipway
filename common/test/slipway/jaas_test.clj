(ns slipway.jaas-test
  (:require [clojure.test :refer :all]
            [slipway.client :as client]
            [slipway.example.handler :as handler]
            [slipway.example.server.jaas :as jaas]
            [slipway.server :as slipway])
  (:import (java.net ConnectException)
           (javax.net.ssl SSLException)))

(deftest authentication

  (let [server (jaas/server)]

    (testing "constraints"

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
      (is (= 303 (:status (client/do-get "http" "localhost" 3000 "/logout")))))

    (testing "login"

      ;; root without '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version {:name "HTTP", :major 1, :minor 1}
              :status           200
              :reason-phrase    "OK"
              :headers          {"Connection" "close", "Content-Type" "text/html", "Content-Length" "2504"}
              :length           2504
              :body             handler/home-html}
             (-> (client/do-login "http" "localhost" 3000 "" "admin" "admin")
                 :ring
                 (select-keys [:protocol-version :status :reason-phrase :headers :length :body]))))

      ;; root with '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version {:name "HTTP", :major 1, :minor 1}
              :status           200
              :reason-phrase    "OK"
              :headers          {"Connection" "close", "Content-Type" "text/html", "Content-Length" "2504"}
              :length           2504
              :body             handler/home-html}
             (-> (client/do-login "http" "localhost" 3000 "/" "admin" "admin")
                 :ring
                 (select-keys [:protocol-version :status :reason-phrase :headers :length :body])))))

    (testing "post-login-redirect"

      (is (= {:protocol-version {:name "HTTP", :major 1, :minor 1}
              :status           200
              :reason-phrase    "OK"
              :headers          {"Connection" "close", "Content-Type" "text/html", "Content-Length" "3048"}
              :length           3048
              :body             (handler/user-html {:slipway.common.auth/user {:name  "admin"
                                                                               :roles #{"admin"
                                                                                        "content-administrator"
                                                                                        "server-administrator"
                                                                                        "user"}}})}
             (-> (client/do-login "http" "localhost" 3000 "/user" "admin" "admin")
                 :ring
                 (select-keys [:protocol-version :status :reason-phrase :headers :length :body])))))

    (slipway/stop-jetty server)))