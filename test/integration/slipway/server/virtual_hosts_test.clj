(ns slipway.server.virtual-hosts-test
  (:require [clojure.test :refer [deftest is testing]]
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

(deftest virtual-hosts

  (testing "multiple contexts with mixed auth, pinned to different connectors"

    ;; In this test, we run three contexts, two pinned to connector on :3000, one pinned to connector on :3001
    ;;  - On :3000 at default context, the example application with hash-user and form authentication
    ;;  - On :3000 at /metrics context, the example application with custom in-memory hash-user and basic authentication
    ;;  - On :3001 at default context, the example application with (different) custom in-memory hash-user and form authentication
    ;;
    ;; Typically you would run different applications, but we can test the variance with one app deployed three times
    ;; and demonstrate that the correct context takes precedence with different user-auth applied in each case and
    ;; the handlers are correctly pinned to their connectors on different ports

    (try
      (test-server/start!
       #::server{:connectors    [{::http/name "connector-3000"
                                  ::http/port 3000}
                                 {::http/name "connector-3001"
                                  ::http/port 3001}]
                 :handler       {::server/handler-type ::context/handler-collection
                                 ::context/handlers    [{::context/ring-handler     (app/handler)
                                                         ::context/virtual-hosts    ["@connector-3000"]
                                                         ::security/handler         :hash
                                                         ::hash/realm               "slipway"
                                                         ::hash/user-file           "dev-resources/jaas/hash-realm.properties"
                                                         ::hash/authenticator       (FormAuthenticator. "/login" "/login-retry" false)
                                                         ::hash/constraint-mappings app/constraints}
                                                        {::context/path             "/metrics"
                                                         ::context/virtual-hosts    ["@connector-3000"]
                                                         ::context/ring-handler     (app/handler)
                                                         ::security/handler         :hash
                                                         ::hash/realm               "slipway"
                                                         ::hash/users               [["prometheus" "password" ["metrics"]]]
                                                         ::hash/authenticator       (BasicAuthenticator.)
                                                         ::hash/constraint-mappings app/constraints}
                                                        {::context/virtual-hosts    ["@connector-3001"]
                                                         ::context/ring-handler     (app/handler)
                                                         ::security/handler         :hash
                                                         ::hash/realm               "slipway"
                                                         ::hash/users               [["x-user" "x-password" ["x-role"]]]
                                                         ::hash/authenticator       (FormAuthenticator. "/login" "/login-retry" false)
                                                         ::hash/constraint-mappings app/constraints}]}
                 :error-handler app/server-error-handler})

      (testing "port :3000, default context, form authentication"

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
                     (dissoc :body))))                      ;; can't compare home html due to csrf token

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
                       (select-keys [:protocol-version :status :reason-phrase])))))))

      (testing "port :3000 metrics context, basic authentication"

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
                 (-> (client/do-get "http" "localhost" 3000 "/metrics")
                     (select-keys of-interest))))

          (is (= 401 (:status (client/do-get "http" "localhost" 3000 "/metrics"))))
          (is (= 401 (:status (client/do-get "http" "localhost" 3000 "/metrics/user")))))

        (testing "credentials provided"

          (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
                  :status                200
                  :reason-phrase         "OK"
                  :orig-content-encoding "gzip"
                  :headers               {"Connection"   "close"
                                          "Content-Type" "text/html"
                                          "Vary"         "Accept-Encoding"}}
                 (-> (client/do-get "http" "prometheus:password@localhost" 3000 "/metrics/")
                     (select-keys of-interest)
                     (dissoc :body))))

          (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
                  :status                200
                  :reason-phrase         "OK"
                  :orig-content-encoding "gzip"
                  :headers               {"Connection"   "close"
                                          "Content-Type" "text/html"
                                          "Vary"         "Accept-Encoding"}
                  :body                  (html/user-page {::user/identity {::principal/name "prometheus"
                                                                           ::user/roles     #{"metrics"}}})}
                 (-> (client/do-get "http" "prometheus:password@localhost" 3000 "/metrics/user")
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
                 (-> (client/do-get "http" "user:wrong@localhost" 3000 "/metrics/user")
                     (select-keys of-interest))))))

      (testing "port :3001, default context, form authentication"

        (testing "constraints"

          ;; wrong port / scheme
          (is (thrown? ConnectException (:status (client/do-get "http" "localhost" 2999 ""))))
          (is (thrown? SSLException (:status (client/do-get "https" "localhost" 3001 ""))))

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
                 (-> (client/do-get "http" "localhost" 3001 "/up")
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
                                          "Location"       "http://localhost:3001/login"
                                          "Vary"           "Accept-Encoding"}}
                 (-> (client/do-get "http" "localhost" 3001 "")
                     (select-keys of-interest))))

          (is (= 302 (:status (client/do-get "http" "localhost" 3001 "/"))))
          (is (= 302 (:status (client/do-get "http" "localhost" 3001 "/user"))))

          ;; auth redirect goes to expected login page
          (is (= "http://localhost:3001/login" (get-in (client/do-get "http" "localhost" 3001 "") [:headers "Location"])))

          ;; login / login-retry don't redirect
          (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
                  :status                200
                  :reason-phrase         "OK"
                  :orig-content-encoding "gzip"
                  :headers               {"Connection"   "close"
                                          "Content-Type" "text/html"
                                          "Vary"         "Accept-Encoding"}
                  :body                  (html/login-page false)}
                 (-> (client/do-get "http" "localhost" 3001 "/login")
                     (select-keys of-interest))))

          (is (= 200 (:status (client/do-get "http" "localhost" 3001 "/login-retry"))))

          ;; jetty nukes session and redirects to /login regardless
          (is (= 302 (:status (client/do-get "http" "localhost" 3001 "/logout")))))

        (testing "login"

          ;; root without '/' (tests jetty nullPathInfo)
          (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
                  :status                200
                  :reason-phrase         "OK"
                  :orig-content-encoding "gzip"
                  :headers               {"Connection"   "close"
                                          "Content-Type" "text/html"
                                          "Vary"         "Accept-Encoding"}}
                 (-> (client/do-login "http" "localhost" 3001 "" "x-user" "x-password")
                     :ring
                     (select-keys of-interest)
                     (dissoc :body))))                      ;; can't compare home html due to csrf token

          ;; root with '/' (tests jetty nullPathInfo)
          (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
                  :status                200
                  :reason-phrase         "OK"
                  :orig-content-encoding "gzip"
                  :headers               {"Connection"   "close"
                                          "Content-Type" "text/html"
                                          "Vary"         "Accept-Encoding"}}
                 (-> (client/do-login "http" "localhost" 3001 "/user" "x-user" "x-password")
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
                 (-> (client/do-login "http" "localhost" 3001 "/user" "x-user" "wrong")
                     :ring
                     (select-keys of-interest)))))

        (testing "post-login-redirect"

          (is (= "http://localhost:3001/"
                 (client/do-get-login-redirect "http" "localhost" 3001 "" "x-user" "x-password")))

          (is (= "http://localhost:3001/"
                 (client/do-get-login-redirect "http" "localhost" 3001 "/" "x-user" "x-password")))

          (is (= "http://localhost:3001/user"
                 (client/do-get-login-redirect "http" "localhost" 3001 "/user" "x-user" "x-password"))))

        (testing "post-login-redirect-null-request-context"

          ;; if we start our session on the login page we have no post-login request context we fallback
          ;; to the default context, this tests a default context is in place in the handler chain
          (is (= "http://localhost:3001/"
                 (client/do-get-login-redirect "http" "localhost" 3001 "/login" "x-user" "x-password"))))

        (testing "session-continuation"

          (is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
                  :status                200
                  :reason-phrase         "OK"
                  :orig-content-encoding "gzip"
                  :headers               {"Connection"   "close"
                                          "Content-Type" "text/html"
                                          "Vary"         "Accept-Encoding"}
                  :body                  (html/user-page {::user/identity {::principal/name "x-user"
                                                                           ::user/roles     #{"x-role"}}})}
                 (let [session (-> (client/do-login "http" "localhost" 3001 "" "x-user" "x-password")
                                   (select-keys [:cookies]))]
                   (-> (client/do-get "http" "localhost" 3001 "/user" session)
                       (select-keys of-interest))))))

        (testing "logout"

          (is (= {:protocol-version {:name "HTTP" :major 1 :minor 1}
                  :reason-phrase    "Found"
                  :status           302}
                 (let [session (-> (client/do-login "http" "localhost" 3001 "" "x-user" "x-password")
                                   (select-keys [:cookies]))]
                   (client/do-get "http" "localhost" 3001 "/logout" session)
                   (-> (client/do-get "http" "localhost" 3001 "/" session)
                       (select-keys [:protocol-version :status :reason-phrase])))))))

      (finally (test-server/stop!)))))