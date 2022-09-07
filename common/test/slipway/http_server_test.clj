(ns slipway.http-server-test
  (:require [clojure.test :refer :all]
            [slipway.client :as client]
            [slipway.example :as example]
            [slipway.example.app :as app])
  (:import (java.net ConnectException)
           (javax.net.ssl SSLException)))

(def of-interest [:protocol-version :status :reason-phrase :body :orig-content-encoding])

(deftest simple-http

  (try
    (example/http-server)

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (app/user-html {})}
           (-> (client/do-get "http://localhost:3000/user" {})
               (select-keys of-interest))))

    (finally (example/stop-server!))))

(deftest compression

  (try
    (example/start-server! (app/handler) (assoc example/hash-opts :slipway.server/gzip? nil))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (app/login-html false)}
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    (finally (example/stop-server!)))

  (try
    (example/start-server! (app/handler) (assoc example/hash-opts :slipway.server/gzip? true))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (app/login-html false)}
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    (finally (example/stop-server!)))

  (try
    (example/start-server! (app/handler) (assoc example/hash-opts :slipway.server/gzip? false))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding nil
            :body                  (app/login-html false)}
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    (finally (example/stop-server!))))

(deftest form-authentication

  (try
    (example/http-hash-server)

    (testing "constraints"

      ;; wrong port / scheme
      (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 ""))))
      (is (thrown? SSLException (:status (client/do-get "https" "localhost" 3000 ""))))

      ;; does not require authentication
      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :status           200
              :reason-phrase    "OK"
              ;:orig-content-encoding nil - note jvm11 returns nil, jvm18 returns "gzip", so we ignore in this case
              :body             ""}
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
              :body                  (app/login-html false)}
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
              :orig-content-encoding "gzip"}
             (-> (client/do-login "http" "localhost" 3000 "" "admin" "admin")
                 :ring
                 (select-keys of-interest)
                 (dissoc :body))))                          ;; can't compare home html due to csrf token

      ;; root with '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"}
             (-> (client/do-login "http" "localhost" 3000 "/" "admin" "admin")
                 :ring
                 (select-keys of-interest)
                 (dissoc :body)))))

    (testing "wrong-credentials"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (app/login-html true)}
             (-> (client/do-login "http" "localhost" 3000 "/user" "admin" "wrong")
                 :ring
                 (select-keys of-interest)))))

    (testing "post-login-redirect"

      (is (= "http://localhost:3000/"
             (client/do-get-login-redirect "http" "localhost" 3000 "" "admin" "admin")))

      (is (= "http://localhost:3000/"
             (client/do-get-login-redirect "http" "localhost" 3000 "/" "admin" "admin")))

      (is (= "http://localhost:3000/user"
             (client/do-get-login-redirect "http" "localhost" 3000 "/user" "admin" "admin"))))

    (testing "post-login-redirect-null-request-context"

      ;; if we start our session on the login page we have no post-login request context we fallback
      ;; to the default context, this tests a default context is in place in the handler chain
      (is (= "http://localhost:3000/"
             (client/do-get-login-redirect "http" "localhost" 3000 "/login" "admin" "admin"))))

    (testing "session-continuation"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (app/user-html {:slipway.user/identity {:name "user" :roles #{"user"}}})}
             (let [session (-> (client/do-login "http" "localhost" 3000 "" "user" "password")
                               (select-keys [:cookies]))]
               (-> (client/do-get "http" "localhost" 3000 "/user" session)
                   (select-keys of-interest))))))

    (testing "logout"

      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :reason-phrase    "See Other"
              :status           303}
             (let [session (-> (client/do-login "http" "localhost" 3000 "" "admin" "admin")
                               (select-keys [:cookies]))]
               (client/do-get "http" "localhost" 3000 "/logout" session)
               (-> (client/do-get "http" "localhost" 3000 "/" session)
                   (select-keys [:protocol-version :status :reason-phrase]))))))

    (finally (example/stop-server!))))

(deftest basic-authentication

  (try
    (example/http-hash-basic-server)

    (testing "constraints"

      ;; wrong port / scheme
      (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 ""))))
      (is (thrown? SSLException (:status (client/do-get "https" "localhost" 3000 ""))))

      ;; does not require authentication
      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :status           200
              :reason-phrase    "OK"
              ;:orig-content-encoding nil - note jvm11 returns nil, jvm18 returns "gzip", so we ignore in this case
              :body             ""}
             (-> (client/do-get "http" "localhost" 3000 "/up")
                 (select-keys (vec (butlast of-interest))))))

      ;; requires authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                401
              :reason-phrase         "Unauthorized"
              :orig-content-encoding nil
              :body                  (app/error-html 401 "Server Error" "Unauthorized")}
             (-> (client/do-get "http" "localhost" 3000 "")
                 (select-keys of-interest))))

      (is (= 401 (:status (client/do-get "http" "localhost" 3000 "/"))))
      (is (= 401 (:status (client/do-get "http" "localhost" 3000 "/user")))))

    (testing "credentials provided"

      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"}
             (-> (client/do-get "http" "admin:admin@localhost" 3000 "")
                 (select-keys of-interest)
                 (dissoc :body))))

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (app/user-html {:slipway.user/identity {:name "user" :roles #{"user"}}})}
             (-> (client/do-get "http" "user:password@localhost" 3000 "/user")
                 (select-keys of-interest)))))

    (testing "incorrect-password"

      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                401
              :reason-phrase         "Unauthorized"
              :body                  (app/error-html 401 "Server Error" "Unauthorized")
              :orig-content-encoding nil}
             (-> (client/do-get "http" "user:wrong@localhost" 3000 "/user")
                 (select-keys of-interest)))))

    (finally (example/stop-server!))))