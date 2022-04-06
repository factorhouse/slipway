(ns slipway.websockets-test
  (:require [clojure.test :refer :all]
            [slipway.websockets :as ws]))

(require '[slipway.auth.constraints :as constraints])

{:auth-method         "basic"                               ;; either "basic" or "form"
 :auth-type           "jaas"                                ;; either "jaas" or "hash"
 :login-uri           "/login"                              ;; the URI where the login form is hosted
 :login-retry-uri     "/login-retry"
 :realm               "my-app"
 :logout-uri          "/logout"
 :post-login-uri-attr "org.eclipse.jetty.security.form_URI"
 :constraint-mappings (constraints/constraint-mappings
                       ;; /css/* is not protected. Everyone (including unauthenticated users) can access
                       ["/css/*" (constraints/no-auth)]
                       ;; /api/* is protected. Any authenticated user can access
                       ["/api/*" (constraints/basic-auth-any-constraint)])}


(deftest websocket-req
  (is (false? (ws/upgrade-request? {})))
  (is (false? (ws/upgrade-request? {:headers {"connection" "upgrade"}})))
  (is (false? (ws/upgrade-request? {:headers {"upgrade" "websocket"}})))
  (is (false? (ws/upgrade-request? {:headers {"connection" "a"
                                              "upgrade"    "b"}})))
  (is (ws/upgrade-request? {:headers {"connection" "upgrade"
                                      "upgrade"    "websocket"}}))
  (is (ws/upgrade-request? {:headers {"connection" "Upgrade"
                                      "upgrade"    "Websocket"}})))

(deftest ws-response
  (is (false? (ws/upgrade-response? {:ws {}})))
  (is (false? (ws/upgrade-response? {:status 200 :body "Hello world"})))
  (is (ws/upgrade-response? (ws/upgrade-response {}))))