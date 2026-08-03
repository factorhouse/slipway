(ns slipway.server.http-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.compression :as compression]
            [slipway.connector.http :as http]
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
           (javax.net.ssl SSLException)
           (org.eclipse.jetty.security.authentication BasicAuthenticator FormAuthenticator)))

(def of-interest [:protocol-version :status :reason-phrase :body :headers :orig-content-encoding])

(deftest simple-http

  (try
    (test-server/start!
     #::server{:connector     {::http/port 3000}
               :handler       {::context/ring-handler (app/handler)}
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
           (-> (client/do-get "http://localhost:3000/user" {})
               (select-keys of-interest))))

    ;; we can turn off accept-encodign of gzip/deflate and see the
    ;; non-compressed response, for some reason this flag also renders
    ;; headers in lower-case - this is a clj-http thing and nothing to be concerned about
    (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
            :status           200
            :reason-phrase    "OK"
            :headers          {"connection"     "close"
                               "content-length" "2961"      ;; this is the uncompressed bytes-size of content
                               "content-type"   "text/html"
                               "vary"           "Accept-Encoding"}
            :body             (html/user-page {})}
           (-> (client/do-get "http://localhost:3000/user" {:decompress-body false})
               (select-keys of-interest))))

    (finally (test-server/stop!))))

(deftest compression

  (try
    (test-server/start!
     #::server{:connector     {::http/port 3000}
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
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
            :status           200
            :reason-phrase    "OK"
            :headers          {"connection"     "close"
                               "content-length" "2479"
                               "content-type"   "text/html"
                               "vary"           "Accept-Encoding"}
            :body             (html/login-page false)}
           (-> (client/do-get "http" "localhost" 3000 "/login" {:decompress-body false})
               (select-keys of-interest))))

    (finally (test-server/stop!)))

  (try
    (test-server/start!
     #::server{:connector     {::http/port 3000}
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
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
            :status           200
            :reason-phrase    "OK"
            :headers          {"connection"     "close"
                               "content-length" "2479"
                               "content-type"   "text/html"
                               "vary"           "Accept-Encoding"}
            :body             (html/login-page false)}
           (-> (client/do-get "http" "localhost" 3000 "/login" {:decompress-body false})
               (select-keys of-interest))))

    (finally (test-server/stop!)))

  (try
    (test-server/start!
     #::server{:connector     {::http/port 3000}
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
           (-> (client/do-get "http" "localhost" 3000 "/login")
               (select-keys of-interest))))

    ;; these tests prove the lower-casing of headers is entirely within clj-http and switches
    ;; on :decompress-body (oddly enough) as they're both effectively the same except for that param
    (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
            :status           200
            :reason-phrase    "OK"
            :headers          {"connection"     "close"
                               "content-length" "2479"
                               "content-type"   "text/html"}
            :body             (html/login-page false)}
           (-> (client/do-get "http" "localhost" 3000 "/login" {:decompress-body false})
               (select-keys of-interest))))

    (finally (test-server/stop!))))

(deftest form-authentication

  (try
    (test-server/start!
     #::server{:connector     {::http/port 3000}
               :handler       {::context/ring-handler     (app/handler)
                               ::security/handler         :hash
                               ::hash/realm               "slipway"
                               ::hash/user-file           "dev-resources/jaas/hash-realm.properties"
                               ::hash/authenticator       (FormAuthenticator. "/login" "/login-retry" false)
                               ::hash/constraint-mappings app/constraints}
               :error-handler app/server-error-handler})

    (testing "constraints"

      ;; wrong port / scheme
      (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 ""))))
      (is (thrown? SSLException (:status (client/do-get "https" "localhost" 3000 ""))))

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
             (-> (client/do-get "http" "localhost" 3000 "/up")
                 (select-keys of-interest))))

      ;; requires authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                302
              :reason-phrase         "Found"
              :orig-content-encoding nil
              :body                  ""
              :headers               {"Connection"     "close"
                                      "Content-Length" "0"
                                      "Expires"        "Thu, 01 Jan 1970 00:00:00 GMT"
                                      "Location"       "http://localhost:3000/login"
                                      "Vary"           "Accept-Encoding"}}
             (-> (client/do-get "http" "localhost" 3000 "")
                 (select-keys of-interest))))

      (is (= 302 (:status (client/do-get "http" "localhost" 3000 "/"))))
      (is (= 302 (:status (client/do-get "http" "localhost" 3000 "/user"))))

      ;; auth redirect goes to expected login page
      (is (= "http://localhost:3000/login" (get-in (client/do-get "http" "localhost" 3000 "") [:headers "Location"])))

      ;; login / login-retry don't redirect
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}
              :body                  (html/login-page false)}
             (-> (client/do-get "http" "localhost" 3000 "/login")
                 (select-keys of-interest))))

      (is (= 200 (:status (client/do-get "http" "localhost" 3000 "/login-retry"))))

      ;; jetty nukes session and redirects to /login regardless
      (is (= 302 (:status (client/do-get "http" "localhost" 3000 "/logout")))))

    (testing "login"

      ;; root without '/' (tests jetty nullPathInfo)
      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}}
             (-> (client/do-login "http" "localhost" 3000 "" "admin" "admin")
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
             (-> (client/do-login "http" "localhost" 3000 "/" "admin" "admin")
                 :ring
                 (select-keys of-interest)
                 (dissoc :body)))))

    (testing "wrong-credentials"

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}
              :body                  (html/login-page true)}
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
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}
              :body                  (html/user-page {::user/identity {::principal/name "user"
                                                                       ::user/roles     #{"user"}}})}
             (let [session (-> (client/do-login "http" "localhost" 3000 "" "user" "password")
                               (select-keys [:cookies]))]
               (-> (client/do-get "http" "localhost" 3000 "/user" session)
                   (select-keys of-interest))))))

    (testing "logout"

      (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
              :reason-phrase    "Found"
              :status           302}
             (let [session (-> (client/do-login "http" "localhost" 3000 "" "admin" "admin")
                               (select-keys [:cookies]))]
               (client/do-get "http" "localhost" 3000 "/logout" session)
               (-> (client/do-get "http" "localhost" 3000 "/" session)
                   (select-keys [:protocol-version :status :reason-phrase]))))))

    (finally (test-server/stop!))))

(deftest basic-authentication

  (try
    (test-server/start!
     #::server{:connector     {::http/port 3000}
               :handler       {::context/ring-handler     (app/handler)
                               ::security/handler         :hash
                               ::hash/realm               "slipway"
                               ::hash/user-file           "dev-resources/jaas/hash-realm.properties"
                               ::hash/authenticator       (BasicAuthenticator.)
                               ::hash/constraint-mappings app/constraints}
               :error-handler app/server-error-handler})

    (testing "constraints"

      ;; wrong port / scheme
      (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 ""))))
      (is (thrown? SSLException (:status (client/do-get "https" "localhost" 3000 ""))))

      ;; does not require authentication
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding nil
              :body                  ""
              :headers               {"Connection"     "close"
                                      "Content-Length" "0"
                                      "Content-Type"   "text/plain"
                                      "Vary"           "Accept-Encoding"}}
             (-> (client/do-get "http" "localhost" 3000 "/up")
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
             (-> (client/do-get "http" "localhost" 3000 "")
                 (select-keys of-interest))))

      (is (= 401 (:status (client/do-get "http" "localhost" 3000 "/"))))
      (is (= 401 (:status (client/do-get "http" "localhost" 3000 "/user")))))

    (testing "credentials provided"

      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}}
             (-> (client/do-get "http" "admin:admin@localhost" 3000 "")
                 (select-keys of-interest)
                 (dissoc :body))))

      (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}
              :body                  (html/user-page {::user/identity {::principal/name "user"
                                                                       ::user/roles     #{"user"}}})}
             (-> (client/do-get "http" "user:password@localhost" 3000 "/user")
                 (select-keys of-interest)))))

    (testing "incorrect-password"

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
             (-> (client/do-get "http" "user:wrong@localhost" 3000 "/user")
                 (select-keys of-interest)))))

    (finally (test-server/stop!))))