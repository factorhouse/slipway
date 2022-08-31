(ns slipway.server-test
  (:require [clojure.test :refer :all]
            [slipway.client :as client]
            [slipway.example.handler :as handler]
            [slipway.example.server :as server])
  (:import (java.net ConnectException)
           (javax.net.ssl SSLException)))

(def of-interest [:protocol-version :status :reason-phrase :body :orig-content-encoding])

(deftest basic-http

  (try
    (server/basic-http!)

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  "<html><h1>Hello world</h1></html>"}
           (-> (client/do-get "http://localhost:3000/" {})
               (select-keys of-interest))))

    (finally (server/stop!))))

(deftest basic-https

  (try
    (server/basic-https!)

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  "<html><h1>Hello world</h1></html>"}
           (-> (client/do-get "https://localhost:3000/" {:insecure? true})
               (select-keys of-interest))))

    (is (thrown? Exception (client/do-get "http://localhost:3000/" {})))

    (finally (server/stop!))))

(deftest compression

  (try
    (server/start! (assoc server/hash-opts :gzip? nil))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (handler/login-html false)}
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    (finally (server/stop!)))

  (try
    (server/start! (assoc server/hash-opts :gzip? true))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (handler/login-html false)}
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    (finally (server/stop!)))

  (try
    (server/start! (assoc server/hash-opts :gzip? false))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding nil
            :body                  (handler/login-html false)}
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    (finally (server/stop!))))

(deftest authentication

  (try
    (server/hash-form-auth!)

    (testing "constraints"

      ;; wrong port / scheme
      (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 ""))))
      (is (thrown? SSLException (:status (client/do-get "https" "localhost" 3000 ""))))

      ;; does not require authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              ;:orig-content-encoding nil - note jvm11 returns nil, jvm18 returns "gzip", so we ignore in this case
              :body                  ""}
             (-> (client/do-get "http" "localhost" 3000 "/up")
                 (select-keys (vec (butlast of-interest))))))

      ;; requires authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                303
              :reason-phrase         "See Other"
              :orig-content-encoding nil
              :body                  ""}
             (-> (client/do-get "http" "localhost" 3000 "")
                 (select-keys of-interest))))

      (is (= 303 (:status (client/do-get "http" "localhost" 3000 "/"))))
      (is (= 303 (:status (client/do-get "http" "localhost" 3000 "/user"))))

      ;; auth redirect goes to expected login page
      (is (= "http://localhost:3000/login" (get-in (client/do-get "http" "localhost" 3000 "") [:headers "Location"])))

      ;; login / login-retry don't redirect
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (handler/login-html false)}
             (-> (client/do-get "http" "localhost" 3000 "/login")
                 (select-keys of-interest))))

      (is (= 200 (:status (client/do-get "http" "localhost" 3000 "/login-retry"))))

      ;; jetty nukes session and redirects to /login regardless
      (is (= 303 (:status (client/do-get "http" "localhost" 3000 "/logout")))))

    (testing "login"

      ;; root without '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  handler/home-html}
             (-> (client/do-login "http" "localhost" 3000 "" "admin" "admin")
                 :ring
                 (select-keys of-interest))))

      ;; root with '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  handler/home-html}
             (-> (client/do-login "http" "localhost" 3000 "/" "admin" "admin")
                 :ring
                 (select-keys of-interest)))))

    (testing "post-login-redirect"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (handler/user-html {:slipway.user/credentials
                                                         {:name  "admin"
                                                          :roles #{"admin"
                                                                   "content-administrator"
                                                                   "server-administrator"
                                                                   "user"}}})}
             (-> (client/do-login "http" "localhost" 3000 "/user" "admin" "admin")
                 :ring
                 (select-keys of-interest)))))

    (testing "post-login-redirect-null-request-context"

      ;; if we start our session on the login page we have no post-login request context we fallback
      ;; to the default context, this tests a default context is in place in the handler chain
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  handler/home-html}
             (-> (client/do-login "http" "localhost" 3000 "/login" "admin" "admin")
                 :ring
                 (select-keys of-interest)))))

    (testing "session-continuation"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (handler/user-html {:slipway.user/credentials {:name "user" :roles #{"user"}}})}
             (let [session (-> (client/do-login "http" "localhost" 3000 "" "user" "password")
                               :jetty
                               (select-keys [:cookies]))]
               (-> (client/do-get "http" "localhost" 3000 "/user" session)
                   (select-keys of-interest))))))

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

    (finally (server/stop!))))