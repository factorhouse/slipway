(ns slipway.test-server
  (:require [slipway :as slipway]
            [slipway.connector.http :as http]
            [slipway.context :as context]
            [slipway.example.app :as app]
            [slipway.security :as security]
            [slipway.security.openid :as openid]
            [slipway.sente]
            [slipway.server :as server]))

(def state (atom nil))

(defn stop!
  []
  (when-let [server @state]
    (slipway/stop server)))

(defn start!
  "To run a JAAS authenticated server, start a REPL with the following JVM JAAS parameter:
   - Hash User Auth  ->  -Djava.security.auth.login.config=/dev-resources/jaas/hash-jaas.conf
   - LDAP Auth       ->  -Djava.security.auth.login.config=/dev-resources/jaas/ldap-jaas.conf"
  [config]
  (stop!)
  (reset! state (slipway/start config)))

(defn start-with-openid-auth-code!
  []
  (start! #::server{:connector     {::http/port 3000}
                    :handler       {::context/ring-handler         (app/handler)
                                    ::security/handler             "openid"
                                    ::openid/issuer                "http://localhost:8080/realms/master"
                                    ::openid/client-id             "slipway"
                                    ::openid/client-secret         "81a0d6ea-1468-4b20-b115-fa68a8df9cf8"
                                    ::openid/scopes                ["profile" "email"]
                                    ::openid/user-id-path          ["name"]
                                    ::openid/user-roles-path       ["realm_access" "roles"]

                                    ;; The following three parameters are derived automatically from the issuer
                                    ;; E.g. http://localhost:8080/realms/master/.well-known/openid-configuration
                                    ;; Jetty looks them all up when only ::openid/issuer is provided
                                    ;; Optional specific endpoint testing
                                    ;::openid/authorization-endpoint "http://localhost:8080/realms/master/protocol/openid-connect/auth"
                                    ;::openid/token-endpoint         "http://localhost:8080/realms/master/protocol/openid-connect/token"
                                    ;::openid/end-session-endpoint   "http://localhost:8080/realms/master/protocol/openid-connect/logout"

                                    ;; Optional redirect testing
                                    ::openid/oidc-redirect-success "/oauth2/openid/callback" ;; defaults to /j_security_check but is configurable
                                    ::openid/oidc-redirect-error   "/login-error" ;; `http://localhost:3000/login-error?error_description_jetty=ID+Token+has+expired` when token expired mid-auth-flow
                                    ::openid/oidc-redirect-logout  "/logout-success" ;; redirected to post-logout

                                    ::openid/constraint-mappings   app/constraints}
                    :error-handler app/server-error-handler}))

;; Curl a client-credentials access token from local keycloak
;
;curl -v --location --request POST 'http://localhost:8080/realms/master/protocol/openid-connect/token' \
;--header 'Content-Type: application/x-www-form-urlencoded' \
;--data-urlencode 'grant_type=client_credentials' \
;--data-urlencode 'client_id=slipway' \
;--data-urlencode 'client_secret=81a0d6ea-1468-4b20-b115-fa68a8df9cf8'

;; Curl a request to slipway operating with :client-credentials flow
; curl -v localhost:3000/user \
; -H "Authorization: Bearer eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICJJd3BkaHMtTkxST3pZc1hXN0NEbzA5eHkzU2Fkc2pFQlE4c09nUlFBSTBZIn0.eyJleHAiOjE3ODUxMzc2NzQsImlhdCI6MTc4NTEzNzM3NCwianRpIjoidHJydGNjOjM2YmU4NmQzLWQ4N2YtYzBkZC1iMWJiLTYxNGQ5NjJkNjI4MiIsImlzcyI6Imh0dHA6Ly9sb2NhbGhvc3Q6ODA4MC9yZWFsbXMvbWFzdGVyIiwiYXVkIjoiYWNjb3VudCIsInN1YiI6IjU2OGRhNTc0LTUwZTgtNDk4Zi1iNzYzLTY1NWE0YjVlNTU1ZCIsInR5cCI6IkJlYXJlciIsImF6cCI6InNsaXB3YXkiLCJhY3IiOiIxIiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImRlZmF1bHQtcm9sZXMtbWFzdGVyIiwib2ZmbGluZV9hY2Nlc3MiLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwicmVzb3VyY2VfYWNjZXNzIjp7ImFjY291bnQiOnsicm9sZXMiOlsibWFuYWdlLWFjY291bnQiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoiZW1haWwgcHJvZmlsZSIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwiY2xpZW50SG9zdCI6IjE3Mi4xNy4wLjEiLCJwcmVmZXJyZWRfdXNlcm5hbWUiOiJzZXJ2aWNlLWFjY291bnQtc2xpcHdheSIsImNsaWVudEFkZHJlc3MiOiIxNzIuMTcuMC4xIiwiY2xpZW50X2lkIjoic2xpcHdheSJ9.rggo7RTFSa5vCCT8IlULSK4QTXQYVYUjszHbgF3qE1r-QIAsHpVgOcIMqgABcEAUH1LyeWI4Bn25hs04Cx4_s12wkRDSQbLDSEKjuLEwQ0ovMSav67vi3WjNoKX1i8B1bHorgBjwBSuh7WMPgU4Tl_w58HQ_vLmnRLNIG_8UOfjBhFf7RD6bME5GMY0FR3efpbO2-dXZK93fMvFBtNfbCJshC-Xp86kbkA7qm_K7OtW9UECGvLB799ubGgBpL5Pp8ImkwiWcaWDFyOX7b2XkLkdNediTymvOGg2us51g6ptjuISbceDBmOFRfjab9XRm_OVrLvF4l_QR5HXL6n9HNg"

(defn start-with-openid-client-creds!
  []
  (start! #::server{:connector     {::http/port 3000}
                    :handler       {::context/ring-handler       (app/handler)
                                    ::security/handler           "openid"
                                    ::openid/authorization-flow  :client-credentials
                                    ::openid/jwks-endpoint       "http://localhost:8080/realms/master/protocol/openid-connect/certs"
                                    ::openid/issuer              "http://localhost:8080/realms/master"
                                    ::openid/user-id-path        ["preferred_username"]
                                    ::openid/user-roles-path     ["realm_access" "roles"]
                                    ::openid/constraint-mappings app/constraints}
                    :error-handler app/server-error-handler}))