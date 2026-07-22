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

;; It's not really necessary for us to test all of Jetty's OpenID integration.
;; However to confirm integration basic you can use this function along with ./scripts/keycloak.sh
(defn start-with-openid!
  []
  (start! #::server{:connector     {::http/port 3000}
                    :handler       {::context/ring-handler         (app/handler)
                                    ::security/handler             "openid"
                                    ::openid/issuer                "http://localhost:8080/realms/master"
                                    ::openid/client-id             "slipway"
                                    ::openid/client-secret         "81a0d6ea-1468-4b20-b115-fa68a8df9cf8"
                                    ::openid/scopes                ["email"]
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