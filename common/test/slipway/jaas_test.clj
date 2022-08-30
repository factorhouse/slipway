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
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :length                761
              :body                  handler/home-html}
             (-> (client/do-login "http" "localhost" 3000 "" "admin" "admin")
                 :ring
                 (select-keys [:protocol-version :status :reason-phrase :length :body :orig-content-encoding]))))

      ;; root with '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :length                761
              :body                  handler/home-html}
             (-> (client/do-login "http" "localhost" 3000 "/" "admin" "admin")
                 :ring
                 (select-keys [:protocol-version :status :reason-phrase :length :body :orig-content-encoding])))))

    (testing "post-login-redirect"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :length                889
              :body                  (handler/user-html {:slipway.common.auth/user {:name  "admin"
                                                                                    :roles #{"admin"
                                                                                             "content-administrator"
                                                                                             "server-administrator"
                                                                                             "user"}}})}
             (-> (client/do-login "http" "localhost" 3000 "/user" "admin" "admin")
                 :ring
                 (select-keys [:protocol-version :status :reason-phrase :length :body :orig-content-encoding])))))

    (testing "post-login-redirect-null-request-context"

      ;; if we start our session on the login page we have no post-login request context we fallback
      ;; to the default context, this tests a default context is in place in the handler chain

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :length                761
              :body                  handler/home-html}
             (-> (client/do-login "http" "localhost" 3000 "/login" "admin" "admin")
                 :ring
                 (select-keys [:protocol-version :status :reason-phrase :length :body :orig-content-encoding])))))

    (testing "cookie-session"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :length                761
              :body                  handler/home-html}
             (let [session (-> (client/do-login "http" "localhost" 3000 "" "admin" "admin")
                               :jetty
                               (select-keys [:cookies]))]
               (-> (client/do-get "http" "localhost" 3000 "/" session)
                   (select-keys [:protocol-version :status :reason-phrase :length :body :orig-content-encoding]))))))

    (testing "logout"

      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :reason-phrase    "See Other"
              :status           303}
             (let [session (-> (client/do-login "http" "localhost" 3000 "" "admin" "admin")
                               :jetty
                               (select-keys [:cookies]))]
               (client/do-get "http" "localhost" 3000 "/logout" session)
               (-> (client/do-get "http" "localhost" 3000 "/" session)
                   (select-keys [:protocol-version :status :reason-phrase]))))))

    (slipway/stop-jetty server)))