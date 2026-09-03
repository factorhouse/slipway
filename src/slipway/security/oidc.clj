(ns slipway.security.oidc
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.request :as request]
            [slipway.security :as security]
            [slipway.user :as user])
  (:import (java.time Instant)
           (org.eclipse.jetty.security Constraint SecurityHandler$PathMapped)
           (org.eclipse.jetty.security.openid OpenIdConfiguration OpenIdConfiguration$Builder OpenIdCredentials)
           (slipway.security.oidc.user.principal OpenIdUserPrincipalWithState)))

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
    :or    {scopes                           ["profile" "email"]
            logout-when-id-token-is-expired? false}}]
  (let [config-builder (cond-> (OpenIdConfiguration$Builder. issuer client-id client-secret)
                         authorization-endpoint (.authorizationEndpoint authorization-endpoint)
                         token-endpoint (.tokenEndpoint token-endpoint)
                         end-session-endpoint (.endSessionEndpoint end-session-endpoint)
                         authentication-method (.authenticationMethod authentication-method)
                         http-client (.httpClient http-client)
                         (some? scopes) (.scopes (some->> scopes (into-array String)))
                         (some? logout-when-id-token-is-expired?) (.logoutWhenIdTokenIsExpired logout-when-id-token-is-expired?)
                         true (.authenticateNewUsers false))]
    (log/debugf "creating openid configuration with scopes %s for %s" (cons "openid" scopes) issuer)
    (.build config-builder)))

(comment
  #:slipway.security.oidc{:authorization-flow               ":authorization-code or :client-credentials (default :authorization-code)"
                          :issuer                           "the URL of the OIDC provider"
                          :client-id                        "OAuth 2.0 Client Identifier valid at the OIDC provider"
                          :client-secret                    "the client secret known only by the Client and the OIDC provider"
                          :authorization-endpoint           "the URL of the OIDC provider's authorization endpoint if configured"
                          :token-endpoint                   "the URL of the OIDC provider's token endpoint if configured"
                          :authentication-method            "authentication method to use with the Token Endpoint"
                          :end-session-endpoint             "the URL of the OIDC provider's end session endpoint if configured"
                          :jwks-endpoint                    "the URL of the OIDC provider's public cryptographic keys for verifying JWT token signatures"
                          :http-client                      "the (optional) HttpClient instance to use"
                          :scopes                           "a sequence of ^String scopes to request, included in addition to 'openid' scope which is always requested, default is ['profile' 'email']"
                          :logout-when-id-token-is-expired? "whether to logout when the ID token is expired, default false"
                          :oidc-redirect-success            "the path where the OIDC provider redirects back to Jetty"
                          :oidc-redirect-error              "optional page where authentication errors are redirected"
                          :oidc-redirect-logout             "optional page where the user is redirected to this page after logout"
                          :identity-fn                      "optional Clojure function applied to user identity post-authentication, pre-user-identity creation"
                          :identity-service                 "a concrete Jetty IdentityService"
                          :constraint-mappings              "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"})

(defmulti ^SecurityHandler$PathMapped flow-handler ::authorization-flow)

(defmethod security/handler :oidc
  [{::keys [authorization-flow constraint-mappings identity-service] :as opts}]
  (log/debugf "creating %s oidc security handler with [%s] constraints" authorization-flow (count constraint-mappings))
  (let [security-handler (flow-handler opts)]
    (doseq [[^String path-spec ^Constraint constraint] constraint-mappings]
      (.put security-handler path-spec constraint))
    (when identity-service
      (log/debugf "identity service %s" (type identity-service))
      (.setIdentityService security-handler identity-service))
    security-handler))

(defn redeem-refresh-token
  [{::keys [^OpenIdUserPrincipalWithState principal]}]
  (when principal
    (.redeemRefreshToken principal)))

(defn principal
  [user]
  (::principal user))

(defn id-token-field
  [{::keys [id-token]} k]
  (get id-token k))

(defn access-token-field
  [{::keys [access-token]} k]
  (get access-token k))

(defn check-credentials
  "Given a request-map holding an authenticated OIDC user principal:
    - Determine the duration in seconds until the user expires
    - Attempt refresh-token redemption if user within refresh-period-s of expiry
    - Invalidate user session if user has expired
   Returns: true if the user has expired"
  [request-map ^Instant now refresh-period-s]
  (let [expires-in (some-> request-map request/user principal p/datafy (user/expires-in now))]
    (log/debugf "expiry check of %s, expires-in %s" (request/user-name request-map) expires-in)
    (when (and (pos? refresh-period-s) (some-> expires-in pos?) (< expires-in refresh-period-s))
      (redeem-refresh-token (request/user request-map)))
    (when (some-> expires-in neg?)
      (request/invalidate-session request-map)
      true)))