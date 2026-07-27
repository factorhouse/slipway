(ns slipway.security.openid
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.security :as security])
  (:import (org.eclipse.jetty.security Constraint SecurityHandler$PathMapped)
           (org.eclipse.jetty.security.openid OpenIdCredentials)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(extend-protocol p/Datafiable

  OpenIdCredentials
  (datafy [credentials]
    {:id-token (.getClaims credentials)
     :response (.getResponse credentials)})

  OpenIdUserPrincipalWithState
  (datafy [principal]
    (merge {:type ::principal}
           (.getState principal))))

(comment
  #:slipway.security.openid{:authorization-flow               ":authorization-code or :client-credentials (default :authorization-code)"
                            :issuer                           "the URL of the OpenID provider"
                            :client-id                        "OAuth 2.0 Client Identifier valid at the OpenID provider"
                            :client-secret                    "the client secret known only by the Client and the OpenID"
                            :jwks-endpoint                    "the URL of the OpenID provider's public cryptographic keys for verifying JWT token signatures"
                            :authorization-endpoint           "the URL of the OpenID provider's authorization endpoint if configured"
                            :token-endpoint                   "the URL of the OpenID provider's token endpoint if configured"
                            :end-session-endpoint             "the URL of the OpenID provider's end session endpoint if configured"
                            :authentication-method            "authentication method to use with the Token Endpoint"
                            :http-client                      "the (optional) HttpClient instance to use"
                            :scopes                           "a sequence of ^String scopes to request, included in addition to \"openid\" scope which is always requested"
                            :user-roles-source                "the token containing user roles, either :access-token or :id-token (default is :access-token)"
                            :user-roles-path                  "the path within the :roles token to find user roles, default is [\"roles\"]"
                            :user-id-path                     "the path within the :id-token token to find user name, default is [\"sub\"]"
                            :logout-when-id-token-is-expired? "whether to logout when the ID token is expired"
                            :oidc-redirect-success            "the path where the OIDC provider redirects back to Jetty"
                            :oidc-redirect-error              "optional page where authentication errors are redirected"
                            :oidc-redirect-logout             "optional page where the user is redirected to this page after logout"
                            :identity-service                 "a concrete Jetty IdentityService"
                            :constraint-mappings              "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"})

(defmulti ^SecurityHandler$PathMapped handler ::authorization-flow)

(defmethod security/handler "openid"
  [{::keys [constraint-mappings identity-service] :as opts}]
  (log/debugf "creating openid security handler with %s constraints" (count constraint-mappings))
  (let [security-handler (handler opts)]
    (doseq [[^String path-spec ^Constraint constraint] constraint-mappings]
      (.put security-handler path-spec constraint))
    (when identity-service
      (log/debugf "identity service %s" (type identity-service))
      (.setIdentityService security-handler identity-service))
    security-handler))