(ns slipway.test-server
  (:require [slipway :as slipway]
            [slipway.connector.http :as http]
            [slipway.context :as context]
            [slipway.example.app :as app]
            [slipway.security :as security]
            [slipway.security.oidc :as oidc]
            [slipway.security.oidc.jwks :as oidc.jwks]
            [slipway.security.oidc.jwt :as oidc.jwt]
            [slipway.security.oidc.jwt.at.verification :as oidc.jwt.at.verification]
            [slipway.sente]
            [slipway.server :as server]
            [slipway.session :as session]))

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

;; ./scripts/keycloak.sh
(defn start-with-openid-auth-code!
  []
  (start! #::server{:connector     {::http/port 3000}
                    :handler       {::context/ring-handler       (app/handler)
                                    ::security/handler           :oidc
                                    ::oidc/issuer                "http://localhost:8080/realms/master"
                                    ::oidc/client-id             "slipway"
                                    ::oidc/client-secret         "81a0d6ea-1468-4b20-b115-fa68a8df9cf8"
                                    ::oidc/constraint-mappings   app/constraints
                                    ::oidc.jwt/user-id-path      ["name"]

                                    ;; The following three parameters are derived automatically from the issuer
                                    ;; E.g. http://localhost:8080/realms/master/.well-known/openid-configuration
                                    ;; Jetty looks them all up when only ::oidc/issuer is provided
                                    ;; Optional specific endpoint testing
                                    ;::oidc/authorization-endpoint "http://localhost:8080/realms/master/protocol/openid-connect/auth"
                                    ;::oidc/token-endpoint         "http://localhost:8080/realms/master/protocol/openid-connect/token"
                                    ;::oidc/end-session-endpoint   "http://localhost:8080/realms/master/protocol/openid-connect/logout"

                                    ;; Optional redirect testing
                                    ::oidc.jwt/user-roles-path   ["realm_access" "roles"] ;; defaults to /j_security_check but is configurable
                                    ::oidc/oidc-redirect-success "/oauth2/openid/callback" ;; `http://localhost:3000/login-error?error_description_jetty=ID+Token+has+expired` when token expired mid-auth-flow
                                    ::oidc/oidc-redirect-error   "/login-error" ;; redirected to post-logout

                                    ::oidc/oidc-redirect-logout  "/logout-success"}
                    :error-handler app/server-error-handler}))

;; ./scripts/keycloak.sh
;; ./scripts/keycloak-get-token.sh
;; ./scripts/keycloak-use-token.sh
(defn start-with-openid-client-creds!
  []
  (start! #::server{:connector     {::http/port 3000}
                    :handler       {::context/ring-handler               (app/handler)
                                    ::security/handler                   :oidc
                                    ::session/enabled?                   false
                                    ::oidc/authorization-flow            :client-credentials
                                    ::oidc/constraint-mappings           app/constraints
                                    ::oidc.jwks/endpoint                 "http://localhost:8080/realms/master/protocol/openid-connect/certs"
                                    ::oidc.jwt.at.verification/exact-iss "http://localhost:8080/realms/master" ;; <-- set in keycloak-realms-with-client.json
                                    ::oidc.jwt.at.verification/exact-aud "https://slipway.io/api"
                                    ::oidc.jwt/user-id-path              ["preferred_username"]
                                    ::oidc.jwt/user-roles-path           ["realm_access" "roles"]}
                    :error-handler app/server-error-handler}))