(ns slipway.https-server-test
  (:require [clojure.test :refer :all]
            [slipway.client :as client]
            [slipway.example :as example]
            [slipway.example.app :as app])
  (:import (java.net ConnectException)
           (org.apache.http ProtocolException)))

(def of-interest [:protocol-version :status :reason-phrase :body :orig-content-encoding])

(deftest simple-https

  (try
    (example/https-server)

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (app/user-html {})}
           (-> (client/do-get "https://localhost:3000/user" {:insecure? true})
               (select-keys of-interest))))

    (is (thrown? Exception (client/do-get "http://localhost:3000/" {})))

    (finally (example/stop-server!))))

(deftest compression

  (try
    (example/start-server! (app/handler) (assoc (merge example/https-opts example/hash-opts)
                                                :slipway.server/gzip? nil))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (app/login-html false)}
           (-> (client/do-get "https" "localhost" 3000 "/login" {:insecure? true})
               (select-keys of-interest))))

    (finally (example/stop-server!)))

  (try
    (example/start-server! (app/handler) (assoc (merge example/https-opts example/hash-opts)
                                                :slipway.server/gzip? true))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :body                  (app/login-html false)}
           (-> (client/do-get "https" "localhost" 3000 "/login" {:insecure? true})
               (select-keys of-interest))))

    (finally (example/stop-server!)))

  (try
    (example/start-server! (app/handler) (assoc (merge example/https-opts example/hash-opts)
                                                :slipway.server/gzip? false))

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding nil
            :body                  (app/login-html false)}
           (-> (client/do-get "https" "localhost" 3000 "/login" {:insecure? true})
               (select-keys of-interest))))

    (finally (example/stop-server!))))

(deftest form-authentication

  (try
    (example/https-hash-server)

    (testing "constraints"

      ;; wrong port / scheme
      (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 "" {:insecure? true}))))
      (is (thrown? ProtocolException (client/do-get "http" "localhost" 3000 "" {:insecure? true})))

      ;; does not require authentication
      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :status           200
              :reason-phrase    "OK"
              ;:orig-content-encoding nil - note jvm11 returns nil, jvm18 returns "gzip", so we ignore in this case
              :body             ""}
             (-> (client/do-get "https" "localhost" 3000 "/up" {:insecure? true})
                 (select-keys (vec (butlast of-interest))))))

      ;; requires authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                303
              :reason-phrase         "See Other"
              :orig-content-encoding nil
              :body                  ""}
             (-> (client/do-get "https" "localhost" 3000 "" {:insecure? true})
                 (select-keys of-interest))))

      (is (= 303 (:status (client/do-get "https" "localhost" 3000 "/" {:insecure? true}))))
      (is (= 303 (:status (client/do-get "https" "localhost" 3000 "/user" {:insecure? true}))))

      ;; auth redirect goes to expected login page
      (is (= "https://localhost:3000/login" (get-in (client/do-get "https" "localhost" 3000 "" {:insecure? true})
                                                    [:headers "Location"])))

      ;; login / login-retry don't redirect
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (app/login-html false)}
             (-> (client/do-get "https" "localhost" 3000 "/login" {:insecure? true})
                 (select-keys of-interest))))

      (is (= 200 (:status (client/do-get "https" "localhost" 3000 "/login-retry" {:insecure? true}))))

      ;; jetty nukes session and redirects to /login regardless
      (is (= 303 (:status (client/do-get "https" "localhost" 3000 "/logout" {:insecure? true})))))

    (testing "login"

      ;; root without '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"}
             (-> (client/do-login "https" "localhost" 3000 "" "admin" "admin" {:insecure? true})
                 :ring
                 (select-keys of-interest)
                 (dissoc :body))))                          ;; can't compare home html due to csrf token

      ;; root with '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"}
             (-> (client/do-login "https" "localhost" 3000 "/" "admin" "admin" {:insecure? true})
                 :ring
                 (select-keys of-interest)
                 (dissoc :body)))))

    (testing "incorrect-password"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (app/login-html true)}
             (-> (client/do-login "https" "localhost" 3000 "/user" "admin" "wrong" {:insecure? true})
                 :ring
                 (select-keys of-interest)))))

    (testing "post-login-redirect"

      (is (= "https://localhost:3000/"
             (client/do-get-login-redirect "https" "localhost" 3000 "" "admin" "admin" {:insecure? true})))

      (is (= "https://localhost:3000/"
             (client/do-get-login-redirect "https" "localhost" 3000 "/" "admin" "admin" {:insecure? true})))

      (is (= "https://localhost:3000/user"
             (client/do-get-login-redirect "https" "localhost" 3000 "/user" "admin" "admin" {:insecure? true}))))

    (testing "post-login-redirect-null-request-context"

      ;; if we start our session on the login page we have no post-login request context we fallback
      ;; to the default context, this tests a default context is in place in the handler chain
      (is (= "https://localhost:3000/"
             (client/do-get-login-redirect "https" "localhost" 3000 "/login" "admin" "admin" {:insecure? true}))))

    (testing "session-continuation"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (app/user-html {:slipway.user/identity {:name "user" :roles #{"user"}}})}
             (let [session (-> (client/do-login "https" "localhost" 3000 "" "user" "password" {:insecure? true})
                               :anon
                               (select-keys [:cookies])
                               (merge {:insecure? true}))]
               (-> (client/do-get "https" "localhost" 3000 "/user" session)
                   (select-keys of-interest))))))

    (testing "logout"

      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :reason-phrase    "See Other"
              :status           303}
             (let [session (-> (client/do-login "https" "localhost" 3000 "" "admin" "admin" {:insecure? true})
                               (select-keys [:cookies])
                               (merge {:insecure? true}))]
               (client/do-get "https" "localhost" 3000 "/logout" session)
               (-> (client/do-get "https" "localhost" 3000 "/" session)
                   (select-keys [:protocol-version :status :reason-phrase]))))))

    (finally (example/stop-server!))))

(deftest basic-authentication

  (try
    (example/https-hash-basic-server)

    (testing "constraints"

      ;; wrong port / scheme
      (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 "" {:insecure? true}))))
      (is (thrown? ProtocolException (client/do-get "http" "localhost" 3000 "" {:insecure? true})))

      ;; does not require authentication
      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :status           200
              :reason-phrase    "OK"
              ;:orig-content-encoding nil - note jvm11 returns nil, jvm18 returns "gzip", so we ignore in this case
              :body             ""}
             (-> (client/do-get "https" "localhost" 3000 "/up" {:insecure? true})
                 (select-keys (vec (butlast of-interest))))))

      ;; requires authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                401
              :reason-phrase         "Unauthorized"
              :orig-content-encoding nil
              :body                  (app/error-html 401 "Server Error" "Unauthorized")}
             (-> (client/do-get "https" "localhost" 3000 "" {:insecure? true})
                 (select-keys of-interest))))

      (is (= 401 (:status (client/do-get "https" "localhost" 3000 "/" {:insecure? true}))))
      (is (= 401 (:status (client/do-get "https" "localhost" 3000 "/user" {:insecure? true})))))

    (testing "credentials provided"

      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"}
             (-> (client/do-get "https" "admin:admin@localhost" 3000 "" {:insecure? true})
                 (select-keys of-interest)
                 (dissoc :body))))

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :body                  (app/user-html {:slipway.user/identity {:name "user" :roles #{"user"}}})}
             (-> (client/do-get "https" "user:password@localhost" 3000 "/user" {:insecure? true})
                 (select-keys of-interest)))))

    (testing "incorrect password"

      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                401
              :reason-phrase         "Unauthorized"
              :body                  (app/error-html 401 "Server Error" "Unauthorized")
              :orig-content-encoding nil}
             (-> (client/do-get "https" "user:wrong@localhost" 3000 "/user" {:insecure? true})
                 (select-keys of-interest)))))

    (finally (example/stop-server!))))