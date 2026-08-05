(ns slipway.server.https-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.compression :as compression]
            [slipway.connector.https :as https]
            [slipway.context :as context]
            [slipway.example.app :as app]
            [slipway.example.html :as html]
            [slipway.principal :as principal]
            [slipway.security :as security]
            [slipway.security.hash :as hash]
            [slipway.server :as server]
            [slipway.test-client :as client]
            [slipway.test-server :as test-server]
            [slipway.user :as user])
  (:import (java.net ConnectException)
           (org.apache.http ProtocolException)
           (org.eclipse.jetty.security.authentication BasicAuthenticator FormAuthenticator)))

(def of-interest [:protocol-version :status :reason-phrase :body :headers :orig-content-encoding])

(deftest simple-https

  (try
    (test-server/start!
     #::server{:connector     #::https{:port                3443
                                       :keystore            "dev-resources/my-keystore.jks"
                                       :keystore-type       "PKCS12"
                                       :keystore-password   "password"
                                       :truststore          "dev-resources/my-truststore.jks"
                                       :truststore-password "password"
                                       :truststore-type     "PKCS12"}
               :handler       #::context{:ring-handler (app/handler)}
               :error-handler app/server-error-handler})

    ;; gzip/deflate accept-encodings are the default
    ;; jetty 12 defaults to chunked encoding for compressed payloads
    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :headers               {"Connection"   "close"
                                    "Content-Type" "text/html"
                                    "Vary"         "Accept-Encoding"}
            :body                  (html/user-page {})}
           (-> (client/do-get "https://localhost:3443/user" {:insecure? true})
               (select-keys of-interest))))

    ;; we can turn off accept-encodign of gzip/deflate and see the
    ;; non-compressed response, for some reason this flag also renders
    ;; headers in lower-case - this is a clj-http thing and nothing to be concerned about
    (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
            :status           200
            :reason-phrase    "OK"
            :headers          {"connection"     "close"
                               "content-length" "3422"      ;; this is the uncompressed bytes-size of content
                               "content-type"   "text/html"
                               "vary"           "Accept-Encoding"}
            :body             (html/user-page {})}
           (-> (client/do-get "https://localhost:3443/user" {:insecure?       true
                                                             :decompress-body false})
               (select-keys of-interest))))

    (is (thrown? Exception (client/do-get "http://localhost:3443/" {})))

    (finally (test-server/stop!))))

(deftest compression

  (try
    (test-server/start!
     #::server{:connector     #::https{:port                3443
                                       :keystore            "dev-resources/my-keystore.jks"
                                       :keystore-type       "PKCS12"
                                       :keystore-password   "password"
                                       :truststore          "dev-resources/my-truststore.jks"
                                       :truststore-password "password"
                                       :truststore-type     "PKCS12"}
               :handler       {::context/ring-handler (app/handler)
                               ::compression/enabled? nil}
               :error-handler app/server-error-handler})

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :headers               {"Connection"   "close"
                                    "Content-Type" "text/html"
                                    "Vary"         "Accept-Encoding"}
            :body                  (html/login-page false)}
           (-> (client/do-get "https" "localhost" 3443 "/login" {:insecure? true})
               (select-keys of-interest))))

    (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
            :status           200
            :reason-phrase    "OK"
            :headers          {"connection"     "close"
                               "content-length" "2479"
                               "content-type"   "text/html"
                               "vary"           "Accept-Encoding"}
            :body             (html/login-page false)}
           (-> (client/do-get "https" "localhost" 3443 "/login" {:decompress-body false
                                                                 :insecure?       true})
               (select-keys of-interest))))

    (finally (test-server/stop!)))

  (try
    (test-server/start!
     #::server{:connector     #::https{:port                3443
                                       :keystore            "dev-resources/my-keystore.jks"
                                       :keystore-type       "PKCS12"
                                       :keystore-password   "password"
                                       :truststore          "dev-resources/my-truststore.jks"
                                       :truststore-password "password"
                                       :truststore-type     "PKCS12"}
               :handler       {::context/ring-handler (app/handler)
                               ::compression/enabled? true}
               :error-handler app/server-error-handler})

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding "gzip"
            :headers               {"Connection"   "close"
                                    "Content-Type" "text/html"
                                    "Vary"         "Accept-Encoding"}
            :body                  (html/login-page false)}
           (-> (client/do-get "https" "localhost" 3443 "/login" {:insecure? true})
               (select-keys of-interest))))

    (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
            :status           200
            :reason-phrase    "OK"
            :headers          {"connection"     "close"
                               "content-length" "2479"
                               "content-type"   "text/html"
                               "vary"           "Accept-Encoding"}
            :body             (html/login-page false)}
           (-> (client/do-get "https" "localhost" 3443 "/login" {:decompress-body false
                                                                 :insecure?       true})
               (select-keys of-interest))))

    (finally (test-server/stop!)))

  (try
    (test-server/start!
     #::server{:connector     #::https{:port                3443
                                       :keystore            "dev-resources/my-keystore.jks"
                                       :keystore-type       "PKCS12"
                                       :keystore-password   "password"
                                       :truststore          "dev-resources/my-truststore.jks"
                                       :truststore-password "password"
                                       :truststore-type     "PKCS12"}
               :handler       {::context/ring-handler (app/handler)
                               ::compression/enabled? false}
               :error-handler app/server-error-handler})

    (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
            :status                200
            :reason-phrase         "OK"
            :orig-content-encoding nil
            :headers               {"Connection"     "close"
                                    "Content-Length" "2479"
                                    "Content-Type"   "text/html"}
            :body                  (html/login-page false)}
           (-> (client/do-get "https" "localhost" 3443 "/login" {:insecure? true})
               (select-keys of-interest))))

    (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
            :status           200
            :reason-phrase    "OK"
            :headers          {"connection"     "close"
                               "content-length" "2479"
                               "content-type"   "text/html"}
            :body             (html/login-page false)}
           (-> (client/do-get "https" "localhost" 3443 "/login" {:decompress-body false
                                                                 :insecure?       true})
               (select-keys of-interest))))

    (finally (test-server/stop!))))

(deftest form-authentication

  (try
    (test-server/start!
     #::server{:connector     #::https{:port                3443
                                       :keystore            "dev-resources/my-keystore.jks"
                                       :keystore-type       "PKCS12"
                                       :keystore-password   "password"
                                       :truststore          "dev-resources/my-truststore.jks"
                                       :truststore-password "password"
                                       :truststore-type     "PKCS12"}
               :handler       {::context/ring-handler     (app/handler)
                               ::security/handler         :hash
                               ::hash/realm               "slipway"
                               ::hash/user-file           "dev-resources/jaas/hash-realm.properties"
                               ::hash/authenticator       (FormAuthenticator. "/login" "/login-retry" false)
                               ::hash/constraint-mappings app/constraints}
               :error-handler app/server-error-handler})

    (testing "constraints"

      ;; wrong port / scheme
      (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 "" {:insecure? true}))))
      (is (thrown? ProtocolException (client/do-get "http" "localhost" 3443 "" {:insecure? true})))

      ;; does not require authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding nil
              :headers               {"Connection"     "close"
                                      "Content-Length" "0"
                                      "Content-Type"   "text/plain"
                                      "Vary"           "Accept-Encoding"}
              :body                  ""}
             (-> (client/do-get "https" "localhost" 3443 "/up" {:insecure? true})
                 (select-keys of-interest))))

      ;; requires authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                302
              :reason-phrase         "Found"
              :orig-content-encoding nil
              :headers               {"Connection"     "close"
                                      "Content-Length" "0"
                                      "Expires"        "Thu, 01 Jan 1970 00:00:00 GMT"
                                      "Location"       "https://localhost:3443/login"
                                      "Vary"           "Accept-Encoding"}
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
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}
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
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}}
             (-> (client/do-login "https" "localhost" 3443 "" "admin" "admin" {:insecure? true})
                 :ring
                 (select-keys of-interest)
                 (dissoc :body))))                          ;; can't compare home html due to csrf token

      ;; root with '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}}
             (-> (client/do-login "https" "localhost" 3443 "/" "admin" "admin" {:insecure? true})
                 :ring
                 (select-keys of-interest)
                 (dissoc :body)))))

    (testing "incorrect-password"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}
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
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}
              :body                  (html/user-page {::user/identity {::principal/type  ::user/principal
                                                                       ::principal/name  "user"
                                                                       ::user/roles      #{"user"}
                                                                       ::user/expires-at nil}})}
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

    (finally (test-server/stop!))))

(deftest basic-authentication

  (try
    (test-server/start!
     #::server{:connector     #::https{:port                3443
                                       :keystore            "dev-resources/my-keystore.jks"
                                       :keystore-type       "PKCS12"
                                       :keystore-password   "password"
                                       :truststore          "dev-resources/my-truststore.jks"
                                       :truststore-password "password"
                                       :truststore-type     "PKCS12"}
               :handler       {::context/ring-handler     (app/handler)
                               ::security/handler         :hash
                               ::hash/realm               "slipway"
                               ::hash/user-file           "dev-resources/jaas/hash-realm.properties"
                               ::hash/authenticator       (BasicAuthenticator.)
                               ::hash/constraint-mappings app/constraints}
               :error-handler app/server-error-handler})

    (testing "constraints"

      ;; wrong port / scheme
      (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 "" {:insecure? true}))))
      (is (thrown? ProtocolException (client/do-get "http" "localhost" 3443 "" {:insecure? true})))

      ;; does not require authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding nil
              :headers               {"Connection"     "close"
                                      "Content-Length" "0"
                                      "Content-Type"   "text/plain"
                                      "Vary"           "Accept-Encoding"}
              :body                  ""}
             (-> (client/do-get "https" "localhost" 3443 "/up" {:insecure? true})
                 (select-keys of-interest))))

      ;; requires authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                401
              :reason-phrase         "Unauthorized"
              :orig-content-encoding nil
              :headers               {"Cache-Control"    "must-revalidate,no-cache,no-store"
                                      "Connection"       "close"
                                      "Content-Length"   "1484"
                                      "Content-Type"     "text/html;charset=iso-8859-1"
                                      "Vary"             "Accept-Encoding"
                                      "WWW-Authenticate" "Basic realm=\"slipway\""}
              :body                  (html/error-page 401 "Server Error" "Unauthorized")}
             (-> (client/do-get "https" "localhost" 3443 "" {:insecure? true})
                 (select-keys of-interest))))

      (is (= 401 (:status (client/do-get "https" "localhost" 3443 "/" {:insecure? true}))))
      (is (= 401 (:status (client/do-get "https" "localhost" 3443 "/user" {:insecure? true})))))

    (testing "credentials provided"

      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}}
             (-> (client/do-get "https" "admin:admin@localhost" 3443 "" {:insecure? true})
                 (select-keys of-interest)
                 (dissoc :body))))

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}
              :body                  (html/user-page {::user/identity {::principal/type  ::user/principal
                                                                       ::principal/name  "user"
                                                                       ::user/roles      #{"user"}
                                                                       ::user/expires-at nil}})}
             (-> (client/do-get "https" "user:password@localhost" 3443 "/user" {:insecure? true})
                 (select-keys of-interest)))))

    (testing "incorrect password"

      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                401
              :reason-phrase         "Unauthorized"
              :body                  (html/error-page 401 "Server Error" "Unauthorized")
              :orig-content-encoding nil
              :headers               {"Cache-Control"    "must-revalidate,no-cache,no-store"
                                      "Connection"       "close"
                                      "Content-Length"   "1484"
                                      "Content-Type"     "text/html;charset=iso-8859-1"
                                      "Vary"             "Accept-Encoding"
                                      "WWW-Authenticate" "Basic realm=\"slipway\""}}
             (-> (client/do-get "https" "user:wrong@localhost" 3443 "/user" {:insecure? true})
                 (select-keys of-interest)))))

    (finally (test-server/stop!))))

(deftest strict-transport-security

  (testing "no hsts configuration"

    (try
      (test-server/start!
       #::server{:connector     #::https{:port                3443
                                         :keystore            "dev-resources/my-keystore.jks"
                                         :keystore-type       "PKCS12"
                                         :keystore-password   "password"
                                         :truststore          "dev-resources/my-truststore.jks"
                                         :truststore-password "password"
                                         :truststore-type     "PKCS12"}
                 :handler       #::context{:ring-handler (app/handler)}
                 :error-handler app/server-error-handler})

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

      (finally (test-server/stop!))))

  (testing "sts-max-age and subdomains"

    (try
      (test-server/start!
       #::server{:connector     #::https{:port                    3443
                                         :keystore                "dev-resources/my-keystore.jks"
                                         :keystore-type           "PKCS12"
                                         :keystore-password       "password"
                                         :truststore              "dev-resources/my-truststore.jks"
                                         :truststore-password     "password"
                                         :truststore-type         "PKCS12"
                                         :sts-max-age-s           31536000
                                         :sts-include-subdomains? true}
                 :handler       #::context{:ring-handler (app/handler)}
                 :error-handler app/server-error-handler})

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

      (finally (test-server/stop!))))

  (testing "sts-max-age without subdomains"

    (try
      (test-server/start!
       #::server{:connector     #::https{:port                3443
                                         :keystore            "dev-resources/my-keystore.jks"
                                         :keystore-type       "PKCS12"
                                         :keystore-password   "password"
                                         :truststore          "dev-resources/my-truststore.jks"
                                         :truststore-password "password"
                                         :truststore-type     "PKCS12"
                                         :sts-max-age-s       31536000}
                 :handler       #::context{:ring-handler (app/handler)}
                 :error-handler app/server-error-handler})

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

      (finally (test-server/stop!))))

  (testing "hsts no max age (incorrect configuration, no header included)"

    (try
      (test-server/start!
       #::server{:connector     #::https{:port                    3443
                                         :keystore                "dev-resources/my-keystore.jks"
                                         :keystore-type           "PKCS12"
                                         :keystore-password       "password"
                                         :truststore              "dev-resources/my-truststore.jks"
                                         :truststore-password     "password"
                                         :truststore-type         "PKCS12"
                                         :sts-include-subdomains? true}
                 :handler       #::context{:ring-handler (app/handler)}
                 :error-handler app/server-error-handler})

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

      (finally (test-server/stop!)))))