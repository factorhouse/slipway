(ns slipway.forwarded-server-test
  (:require [clojure.test :refer :all]
            [slipway.client :as client]
            [slipway.example :as example]
            [slipway.example.html :as html])
  (:import (java.net ConnectException)
           (javax.net.ssl SSLException)
           (org.apache.http ProtocolException)))

(def of-interest [:protocol-version :status :reason-phrase :body :orig-content-encoding])

(deftest simple

  (try
    (example/start! [:http+https+forwarded])

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (html/user-page {})}
           (-> (client/do-get "http://localhost:3000/user" {})
               (select-keys of-interest))))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (html/user-page {})}
           (-> (client/do-get "https://localhost:3443/user" {:insecure? true})
               (select-keys of-interest))))

    (finally (example/stop!))))

(deftest compression

  (try
    (example/start! [:http+https+forwarded :gzip-nil])

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (html/login-page false)}
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (html/login-page false)}
           (-> (client/do-get "https" "localhost" 3443 "/login" {:insecure? true})
               (select-keys of-interest))))

    (finally (example/stop!)))

  (try
    (example/start! [:http+https+forwarded :gzip-true])

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (html/login-page false)}
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (html/login-page false)}
           (-> (client/do-get "https" "localhost" 3443 "/login" {:insecure? true})
               (select-keys of-interest))))

    (finally (example/stop!)))

  (try
    (example/start! [:http+https+forwarded :gzip-false])

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding nil
            :body                  (html/login-page false)}
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding nil
            :body                  (html/login-page false)}
           (-> (client/do-get "https" "localhost" 3443 "/login" {:insecure? true})
               (select-keys of-interest))))

    (finally (example/stop!))))

(deftest form-authentication

  (try
    (example/start! [:http+https+forwarded :hash-auth])

    (testing "constraints http"

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
      (is (= "http://localhost:3000/login" (get-in (client/do-get "http" "localhost" 3000 "")
                                                   [:headers "Location"])))

      ;; NOTE: this is the test that ForwardedRequestCustomizer is configured and working as expected.
      ;; https://www.eclipse.org/jetty/documentation/jetty-10/operations-guide/index.html#og-protocols-proxy-forwarded
      (is (= "https://localhost:3000/login" (get-in (client/do-get
                                                     "http://localhost:3000/"
                                                     {:headers {"Forwarded" "for=2.36.72.144:21216;proto=https"}})
                                                    [:headers "Location"])))

      ;; login / login-retry don't redirect
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (html/login-page false)}
             (-> (client/do-get "http" "localhost" 3000 "/login")
                 (select-keys of-interest))))

      (is (= 200 (:status (client/do-get "http" "localhost" 3000 "/login-retry"))))

      ;; jetty nukes session and redirects to /login regardless
      (is (= 303 (:status (client/do-get "http" "localhost" 3000 "/logout")))))

    (testing "constraints https"

      ;; wrong port / scheme
      (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 "" {:insecure? true}))))
      (is (thrown? ProtocolException (client/do-get "http" "localhost" 3443 "" {:insecure? true})))

      ;; does not require authentication
      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :status           200
              :reason-phrase    "OK"
              ;:orig-content-encoding nil - note jvm11 returns nil, jvm18 returns "gzip", so we ignore in this case
              :body             ""}
             (-> (client/do-get "https" "localhost" 3443 "/up" {:insecure? true})
                 (select-keys (vec (butlast of-interest))))))

      ;; requires authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                303
              :reason-phrase         "See Other"
              :orig-content-encoding nil
              :body                  ""}
             (-> (client/do-get "https" "localhost" 3443 "" {:insecure? true})
                 (select-keys of-interest))))

      (is (= 303 (:status (client/do-get "https" "localhost" 3443 "/" {:insecure? true}))))
      (is (= 303 (:status (client/do-get "https" "localhost" 3443 "/user" {:insecure? true}))))

      ;; auth redirect goes to expected login page
      (is (= "https://localhost:3443/login" (get-in (client/do-get "https" "localhost" 3443 "" {:insecure? true})
                                                    [:headers "Location"])))

      ;; NOTE: this is the test that ForwardedRequestCustomizer is configured and working as expected.
      ;; https://www.eclipse.org/jetty/documentation/jetty-10/operations-guide/index.html#og-protocols-proxy-forwarded
      (is (= "http://localhost:3443/login" (get-in (client/do-get
                                                    "https://localhost:3443/"
                                                    {:headers   {"Forwarded" "for=2.36.72.144:21216;proto=http"}
                                                     :insecure? true})
                                                   [:headers "Location"])))

      ;; login / login-retry don't redirect
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (html/login-page false)}
             (-> (client/do-get "https" "localhost" 3443 "/login" {:insecure? true})
                 (select-keys of-interest))))

      (is (= 200 (:status (client/do-get "https" "localhost" 3443 "/login-retry" {:insecure? true}))))

      ;; jetty nukes session and redirects to /login regardless
      (is (= 303 (:status (client/do-get "https" "localhost" 3443 "/logout" {:insecure? true})))))

    (testing "login http"

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

    (testing "login https"

      ;; root without '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"}
             (-> (client/do-login "https" "localhost" 3443 "" "admin" "admin" {:insecure? true})
                 :ring
                 (select-keys of-interest)
                 (dissoc :body))))                          ;; can't compare home html due to csrf token

      ;; root with '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"}
             (-> (client/do-login "https" "localhost" 3443 "/" "admin" "admin" {:insecure? true})
                 :ring
                 (select-keys of-interest)
                 (dissoc :body)))))

    (testing "wrong-credentials"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (html/login-page true)}
             (-> (client/do-login "http" "localhost" 3000 "/user" "admin" "wrong")
                 :ring
                 (select-keys of-interest))))

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (html/login-page true)}
             (-> (client/do-login "https" "localhost" 3443 "/user" "admin" "wrong" {:insecure? true})
                 :ring
                 (select-keys of-interest)))))

    (testing "post-login-redirect"

      (is (= "http://localhost:3000/"
             (client/do-get-login-redirect "http" "localhost" 3000 "" "admin" "admin")))

      (is (= "http://localhost:3000/"
             (client/do-get-login-redirect "http" "localhost" 3000 "/" "admin" "admin")))

      (is (= "http://localhost:3000/user"
             (client/do-get-login-redirect "http" "localhost" 3000 "/user" "admin" "admin")))

      (is (= "https://localhost:3443/"
             (client/do-get-login-redirect "https" "localhost" 3443 "" "admin" "admin" {:insecure? true})))

      (is (= "https://localhost:3443/"
             (client/do-get-login-redirect "https" "localhost" 3443 "/" "admin" "admin" {:insecure? true})))

      (is (= "https://localhost:3443/user"
             (client/do-get-login-redirect "https" "localhost" 3443 "/user" "admin" "admin" {:insecure? true}))))

    (testing "post-login-redirect-null-request-context"

      ;; if we start our session on the login page we have no post-login request context we fallback
      ;; to the default context, this tests a default context is in place in the handler chain
      (is (= "http://localhost:3000/"
             (client/do-get-login-redirect "http" "localhost" 3000 "/login" "admin" "admin")))

      (is (= "https://localhost:3443/"
             (client/do-get-login-redirect "https" "localhost" 3443 "/login" "admin" "admin" {:insecure? true}))))

    (testing "session-continuation"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (html/user-page {:slipway.user/identity {:name "user" :roles #{"user"}}})}
             (let [session (-> (client/do-login "http" "localhost" 3000 "" "user" "password")
                               (select-keys [:cookies]))]
               (-> (client/do-get "http" "localhost" 3000 "/user" session)
                   (select-keys of-interest)))))

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (html/user-page {:slipway.user/identity {:name "user" :roles #{"user"}}})}
             (let [session (-> (client/do-login "https" "localhost" 3443 "" "user" "password" {:insecure? true})
                               (merge {:insecure? true}))]
               (-> (client/do-get "https" "localhost" 3443 "/user" session)
                   (select-keys of-interest))))))

    (testing "logout"

      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :reason-phrase    "See Other"
              :status           303}
             (let [session (-> (client/do-login "http" "localhost" 3000 "" "admin" "admin")
                               (select-keys [:cookies]))]
               (client/do-get "http" "localhost" 3000 "/logout" session)
               (-> (client/do-get "http" "localhost" 3000 "/" session)
                   (select-keys [:protocol-version :status :reason-phrase])))))

      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :reason-phrase    "See Other"
              :status           303}
             (let [session (-> (client/do-login "https" "localhost" 3443 "" "admin" "admin" {:insecure? true})
                               (select-keys [:cookies])
                               (merge {:insecure? true}))]
               (client/do-get "https" "localhost" 3443 "/logout" session)
               (-> (client/do-get "https" "localhost" 3443 "/" session)
                   (select-keys [:protocol-version :status :reason-phrase]))))))

    (finally (example/stop!))))

(deftest basic-authentication-http

  (try
    (example/start! [:http+https+forwarded :hash-auth :basic-auth])

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
              :body                  (html/error-page 401 "Server Error" "Unauthorized")}
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
              :body                  (html/user-page {:slipway.user/identity {:name "user" :roles #{"user"}}})}
             (-> (client/do-get "http" "user:password@localhost" 3000 "/user")
                 (select-keys of-interest)))))

    (testing "incorrect-password"

      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                401
              :reason-phrase         "Unauthorized"
              :body                  (html/error-page 401 "Server Error" "Unauthorized")
              :orig-content-encoding nil}
             (-> (client/do-get "http" "user:wrong@localhost" 3000 "/user")
                 (select-keys of-interest)))))

    (finally (example/stop!))))

(deftest basic-authentication-https

  (try
    (example/start! [:http+https+forwarded :hash-auth :basic-auth])

    (testing "constraints"

      ;; wrong port / scheme
      (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 "" {:insecure? true}))))
      (is (thrown? ProtocolException (client/do-get "http" "localhost" 3443 "" {:insecure? true})))

      ;; does not require authentication
      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :status           200
              :reason-phrase    "OK"
              ;:orig-content-encoding nil - note jvm11 returns nil, jvm18 returns "gzip", so we ignore in this case
              :body             ""}
             (-> (client/do-get "https" "localhost" 3443 "/up" {:insecure? true})
                 (select-keys (vec (butlast of-interest))))))

      ;; requires authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                401
              :reason-phrase         "Unauthorized"
              :orig-content-encoding nil
              :body                  (html/error-page 401 "Server Error" "Unauthorized")}
             (-> (client/do-get "https" "localhost" 3443 "" {:insecure? true})
                 (select-keys of-interest))))

      (is (= 401 (:status (client/do-get "https" "localhost" 3443 "/" {:insecure? true}))))
      (is (= 401 (:status (client/do-get "https" "localhost" 3443 "/user" {:insecure? true})))))

    (testing "credentials provided"

      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"}
             (-> (client/do-get "https" "admin:admin@localhost" 3443 "" {:insecure? true})
                 (select-keys of-interest)
                 (dissoc :body))))

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (html/user-page {:slipway.user/identity {:name "user" :roles #{"user"}}})}
             (-> (client/do-get "https" "user:password@localhost" 3443 "/user" {:insecure? true})
                 (select-keys of-interest)))))

    (testing "incorrect password"

      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                401
              :reason-phrase         "Unauthorized"
              :body                  (html/error-page 401 "Server Error" "Unauthorized")
              :orig-content-encoding nil}
             (-> (client/do-get "https" "user:wrong@localhost" 3443 "/user" {:insecure? true})
                 (select-keys of-interest)))))

    (finally (example/stop!))))