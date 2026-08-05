(ns slipway.security.openid
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.security :as security])
  (:import (org.eclipse.jetty.security Constraint SecurityHandler$PathMapped)
           (org.eclipse.jetty.security.openid OpenIdConfiguration OpenIdConfiguration$Builder OpenIdCredentials)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(extend-protocol p/Datafiable

  OpenIdCredentials
  (datafy [credentials]
    {:id-token (.getClaims credentials)
     :response (into {} (.getResponse credentials))})

  OpenIdUserPrincipalWithState
  (datafy [principal]
    (assoc (.getState principal) ::principal principal)))

(defn configuration ^OpenIdConfiguration
  [{::keys [issuer client-id client-secret authorization-endpoint token-endpoint end-session-endpoint
            authentication-method http-client scopes logout-when-id-token-is-expired?]
    :or    {scopes ["profile" "email"]}}]
  (let [config-builder (cond-> (OpenIdConfiguration$Builder. issuer client-id client-secret)
                         authorization-endpoint (.authorizationEndpoint authorization-endpoint)
                         token-endpoint (.tokenEndpoint token-endpoint)
                         end-session-endpoint (.endSessionEndpoint end-session-endpoint)
                         authentication-method (.authenticationMethod authentication-method)
                         http-client (.httpClient http-client)
                         (some? scopes) (.scopes (some->> scopes (into-array String)))
                         (some? logout-when-id-token-is-expired?) (.logoutWhenIdTokenIsExpired logout-when-id-token-is-expired?))]
    (log/debugf "creating openid configuration for %s with scopes %s" issuer (cons "openid" scopes))
    (.build config-builder)))

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
                            :scopes                           "a sequence of ^String scopes to request, included in addition to \"openid\" scope which is always requested, default is [\"profile\" \"email\"]"
                            :logout-when-id-token-is-expired? "whether to logout when the ID token is expired, default false"
                            :oidc-redirect-success            "the path where the OIDC provider redirects back to Jetty"
                            :oidc-redirect-error              "optional page where authentication errors are redirected"
                            :oidc-redirect-logout             "optional page where the user is redirected to this page after logout"
                            :identity-service                 "a concrete Jetty IdentityService"
                            :constraint-mappings              "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"})

(defmulti ^SecurityHandler$PathMapped flow-handler ::authorization-flow)

(defmethod security/handler :openid
  [{::keys [constraint-mappings identity-service] :as opts}]
  (log/debugf "creating openid security handler with %s constraints" (count constraint-mappings))
  (let [security-handler (flow-handler opts)]
    (doseq [[^String path-spec ^Constraint constraint] constraint-mappings]
      (.put security-handler path-spec constraint))
    (when identity-service
      (log/debugf "identity service %s" (type identity-service))
      (.setIdentityService security-handler identity-service))
    security-handler))