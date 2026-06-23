(ns slipway.security.openid
  (:require [clojure.tools.logging :as log])
  (:import (org.eclipse.jetty.security Constraint SecurityHandler SecurityHandler$PathMapped)
           (org.eclipse.jetty.security.openid OpenIdAuthenticator OpenIdConfiguration OpenIdConfiguration$Builder OpenIdLoginService)))

(defn configuration ^OpenIdConfiguration
  [{::keys [issuer client-id client-secret authorization-endpoint token-endpoint end-session-endpoint
            authentication-method http-client scopes logout-when-id-token-is-expired?]}]
  (-> (OpenIdConfiguration$Builder. issuer client-id client-secret)
      (.authorizationEndpoint authorization-endpoint)
      (.tokenEndpoint token-endpoint)
      (.endSessionEndpoint end-session-endpoint)
      (.authenticationMethod authentication-method)
      (.httpClient http-client)
      (.scopes (some->> scopes (into-array String)))
      (.logoutWhenIdTokenIsExpired logout-when-id-token-is-expired?)))

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

(defn handler ^SecurityHandler
  [{::keys [issuer constraint-mappings identity-service] :as opts}]
  (log/debugf "creating openid security handler with %s constraints" (count constraint-mappings))
  (let [config           (configuration opts)
        login-service    (OpenIdLoginService configuration)
        security-handler (doto (SecurityHandler$PathMapped.)
                           (.setAuthenticator (authenticator config opts))
                           (.setLoginService login-service)
                           (.setRealmName issuer))]
    (doseq [[^String path-spec ^Constraint constraint] constraint-mappings]
      (.put security-handler path-spec constraint))
    (when identity-service
      (log/debugf "identity service %s" (type identity-service))
      (.setIdentityService security-handler identity-service))
    security-handler))