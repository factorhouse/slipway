(ns slipway.https-server-test
  (:require [clojure.test :refer :all]
            [slipway.client :as client]
            [slipway.example :as example]
            [slipway.example.html :as html])
  (:import (java.net ConnectException)
           (org.apache.http ProtocolException)))

(def of-interest [:protocol-version :status :reason-phrase :body :orig-content-encoding])

(deftest simple-https

  (try
    (example/start! [:https])

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (html/user-page {})}
           (-> (client/do-get "https://localhost:3443/user" {:insecure? true})
               (select-keys of-interest))))

    (is (thrown? Exception (client/do-get "http://localhost:3443/" {})))

    (finally (example/stop!))))

(deftest compression

  (try
    (example/start! [:https :gzip-nil])

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (html/login-page false)}
           (-> (client/do-get "https" "localhost" 3443 "/login" {:insecure? true})
               (select-keys of-interest))))

    (finally (example/stop!)))

  (try
    (example/start! [:https :gzip-true])

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (html/login-page false)}
           (-> (client/do-get "https" "localhost" 3443 "/login" {:insecure? true})
               (select-keys of-interest))))

    (finally (example/stop!)))

  (try
    (example/start! [:https :gzip-false])

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
    (example/start! [:https :hash-auth])

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
              :status                302
              :reason-phrase         "Found"
              :orig-content-encoding nil
              :body                  ""}
             (-> (client/do-get "https" "localhost" 3443 "" {:insecure? true})
                 (select-keys of-interest))))

      (is (= 302 (:status (client/do-get "https" "localhost" 3443 "/" {:insecure? true}))))
      (is (= 302 (:status (client/do-get "https" "localhost" 3443 "/user" {:insecure? true}))))

      ;; auth redirect goes to expected login page
      (is (= "https://localhost:3443/login" (get-in (client/do-get "https" "localhost" 3443 "" {:insecure? true})
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
      (is (= 302 (:status (client/do-get "https" "localhost" 3443 "/logout" {:insecure? true})))))

    (testing "login"

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

    (testing "incorrect-password"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (html/login-page true)}
             (-> (client/do-login "https" "localhost" 3443 "/user" "admin" "wrong" {:insecure? true})
                 :ring
                 (select-keys of-interest)))))

    (testing "post-login-redirect"

      (is (= "https://localhost:3443/"
             (client/do-get-login-redirect "https" "localhost" 3443 "" "admin" "admin" {:insecure? true})))

      (is (= "https://localhost:3443/"
             (client/do-get-login-redirect "https" "localhost" 3443 "/" "admin" "admin" {:insecure? true})))

      (is (= "https://localhost:3443/user"
             (client/do-get-login-redirect "https" "localhost" 3443 "/user" "admin" "admin" {:insecure? true}))))

    (testing "post-login-redirect-null-request-context"

      ;; if we start our session on the login page we have no post-login request context we fallback
      ;; to the default context, this tests a default context is in place in the handler chain
      (is (= "https://localhost:3443/"
             (client/do-get-login-redirect "https" "localhost" 3443 "/login" "admin" "admin" {:insecure? true}))))

    (testing "session-continuation"

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
              :reason-phrase    "Found"
              :status           302}
             (let [session (-> (client/do-login "https" "localhost" 3443 "" "admin" "admin" {:insecure? true})
                               (select-keys [:cookies])
                               (merge {:insecure? true}))]
               (client/do-get "https" "localhost" 3443 "/logout" session)
               (-> (client/do-get "https" "localhost" 3443 "/" session)
                   (select-keys [:protocol-version :status :reason-phrase]))))))

    (finally (example/stop!))))

(deftest basic-authentication

  (try
    (example/start! [:https :hash-auth :basic-auth])

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

(deftest strict-transport-security

  (testing "no hsts configuration"

    (try
      (example/start! [:https])

      (let [result     (-> (client/do-get "https://localhost:3443/user" {:insecure? true})
                           (select-keys (conj of-interest :headers)))
            sts-header (get-in result [:headers "Strict-Transport-Security"])
            result     (dissoc result :headers)]

        (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
                :status                200
                :reason-phrase         "OK"
                :orig-content-encoding "gzip"
                :body                  (html/user-page {})}
               result))

        (is (= nil sts-header)))

      (finally (example/stop!))))

  (testing "sts-max-age and subdomains"

    (try
      (example/start! [:hsts])

      (let [result     (-> (client/do-get "https://localhost:3443/user" {:insecure? true})
                           (select-keys (conj of-interest :headers)))
            sts-header (get-in result [:headers "Strict-Transport-Security"])
            result     (dissoc result :headers)]

        (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
                :status                200
                :reason-phrase         "OK"
                :orig-content-encoding "gzip"
                :body                  (html/user-page {})}
               result))

        (is (= "max-age=31536000; includeSubDomains" sts-header)))

      (finally (example/stop!))))

  (testing "sts-max-age without subdomains"

    (try
      (example/start! [:hsts-no-subdomains])

      (let [result     (-> (client/do-get "https://localhost:3443/user" {:insecure? true})
                           (select-keys (conj of-interest :headers)))
            sts-header (get-in result [:headers "Strict-Transport-Security"])
            result     (dissoc result :headers)]

        (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
                :status                200
                :reason-phrase         "OK"
                :orig-content-encoding "gzip"
                :body                  (html/user-page {})}
               result))

        (is (= "max-age=31536000" sts-header)))

      (finally (example/stop!))))

  (testing "hsts no max age (incorrect configuration, no header included)"

    (try
      (example/start! [:hsts-no-max-age])

      (let [result     (-> (client/do-get "https://localhost:3443/user" {:insecure? true})
                           (select-keys (conj of-interest :headers)))
            sts-header (get-in result [:headers "Strict-Transport-Security"])
            result     (dissoc result :headers)]

        (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
                :status                200
                :reason-phrase         "OK"
                :orig-content-encoding "gzip"
                :body                  (html/user-page {})}
               result))

        (is (= nil sts-header)))

      (finally (example/stop!)))))