(ns slipway.security.openid
  (:require [clojure.tools.logging :as log]
            [slipway.security :as security])
  (:import (org.eclipse.jetty.security Constraint SecurityHandler$PathMapped)
           (org.eclipse.jetty.security.openid OpenIdAuthenticator OpenIdConfiguration OpenIdConfiguration$Builder OpenIdLoginService)))

(defn configuration ^OpenIdConfiguration
  [{::keys [issuer client-id client-secret authorization-endpoint token-endpoint end-session-endpoint
            authentication-method http-client scopes logout-when-id-token-is-expired?]}]
  (let [config-builder (cond-> (OpenIdConfiguration$Builder. issuer client-id client-secret)
                         authorization-endpoint (.authorizationEndpoint authorization-endpoint)
                         token-endpoint (.tokenEndpoint token-endpoint)
                         end-session-endpoint (.endSessionEndpoint end-session-endpoint)
                         authentication-method (.authenticationMethod authentication-method)
                         http-client (.httpClient http-client)
                         (some? scopes) (.scopes (some->> scopes (into-array String)))
                         (some? logout-when-id-token-is-expired?) (.logoutWhenIdTokenIsExpired logout-when-id-token-is-expired?))]
    (.build config-builder)))

(defn authenticator ^OpenIdAuthenticator
  [config {::keys [oidc-redirect-success oidc-redirect-error oidc-redirect-logout]
           :or    {oidc-redirect-success OpenIdAuthenticator/J_SECURITY_CHECK}}]
  (OpenIdAuthenticator. config oidc-redirect-success oidc-redirect-error oidc-redirect-logout))

(comment
  #:slipway.security.openid{:issuer                           "the URL of the OpenID provider"
                            :client-id                        "OAuth 2.0 Client Identifier valid at the OpenID provider"
                            :client-secret                    "the client secret known only by the Client and the OpenID"
                            :authorization-endpoint           "the URL of the OpenID provider's authorization endpoint if configured"
                            :token-endpoint                   "the URL of the OpenID provider's token endpoint if configured"
                            :end-session-endpoint             "the URL of the OpenID provider's end session endpoint if configured"
                            :authentication-method            "authentication method to use with the Token Endpoint"
                            :http-client                      "the (optional) HttpClient instance to use"
                            :scopes                           "a sequence of ^String scopes to request"
                            :logout-when-id-token-is-expired? "whether to logout when the ID token is expired"
                            :oidc-redirect-success            "the path where the OIDC provider redirects back to Jetty"
                            :oidc-redirect-error              "optional page where authentication errors are redirected"
                            :oidc-redirect-logout             "optional page where the user is redirected to this page after logout"
                            :identity-service                 "a concrete Jetty IdentityService"
                            :constraint-mappings              "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"})

(defmethod security/handler "openid"
  [{::keys [issuer constraint-mappings identity-service] :as opts}]
  (log/debugf "creating openid security handler with %s constraints" (count constraint-mappings))
  (let [openid-config    (configuration opts)
        login-service    (OpenIdLoginService. openid-config)
        security-handler (doto (SecurityHandler$PathMapped.)
                           (.setAuthenticator (authenticator openid-config opts))
                           (.setLoginService login-service)
                           (.setRealmName issuer))]
    (doseq [[^String path-spec ^Constraint constraint] constraint-mappings]
      (.put security-handler path-spec constraint))
    (when identity-service
      (log/debugf "identity service %s" (type identity-service))
      (.setIdentityService security-handler identity-service))
    security-handler))