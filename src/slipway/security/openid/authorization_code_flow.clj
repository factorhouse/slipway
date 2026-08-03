(ns slipway.security.openid.authorization-code-flow
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.security.openid :as openid]
            [slipway.security.openid.jwt :as openid.jwt])
  (:import (java.security Principal)
           (java.util.function Function)
           (javax.security.auth Subject)
           (org.eclipse.jetty.security IdentityService LoginService SecurityHandler$PathMapped UserIdentity)
           (org.eclipse.jetty.security.openid JwtDecoder OpenIdAuthenticator OpenIdConfiguration OpenIdConfiguration$Builder OpenIdCredentials OpenIdLoginService OpenIdUserPrincipal)
           (org.eclipse.jetty.server Request)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(defn access-token
  "As this login module is exclusively used in the direct Authorization Code Flow and this access token is intended for
   use in the same JVM as the ID token, we can rely on the proir ID token validation as being sufficient as they are
   both from the same source at the same point in time"
  [^OpenIdCredentials creds]
  (try
    (JwtDecoder/decode (get (.getResponse creds) "access_token"))
    (catch Exception ex
      (log/debug ex "error decoding access_token"))))

(defn username
  [id-token access-token {::openid.jwt/keys [user-id-source user-id-path]
                          :or               {user-id-source :id-token
                                             user-id-path   ["sub"]}}]
  (let [name-token (if (= :access-token user-id-source) access-token id-token)]
    (get-in name-token user-id-path)))

(defn roles
  [id-token access-token {::openid.jwt/keys [user-roles-source user-roles-path]
                          :or               {user-roles-source :access-token
                                             user-roles-path   ["roles"]}}]
  (let [roles-token (if (= :id-token user-roles-source) id-token access-token)
        roles-value (get-in roles-token user-roles-path)]
    (if (string? roles-value) #{roles-value} (set roles-value))))

(defn state
  [creds access-token opts]
  (log/debugf "decoded [%s] claims from access token" (count access-token))
  (let [{:keys [id-token response]} (p/datafy creds)
        user-id    (username id-token access-token opts)
        user-roles (roles id-token access-token opts)]
    (log/debugf "user %s authorized with [%s] roles" user-id (count user-roles))
    {:name                  user-id
     :roles                 user-roles
     ::openid/response      response
     ::openid/id-token      id-token
     ::openid/access-token  access-token
     ::openid/refresh-token (get response "refresh_token")}))

(defn login-module
  "This roles-service is exclusively for OpenID Connect (OIDC) direct Authorization Code flow via the Token endpoint,
   that is how Jetty implements OIDC authentication interactions.
   In that flow you can rely on TLS (HTTPS) to authenticate the issuer instead of verifying the JWT signature.
   While that normally only applies to the ID token, in our case the Access token is intended for local use inside this
   client service JVM, and so the same logic applies."
  ^LoginService [realm opts]
  (let [id-service-state (atom nil)]
    (reify LoginService
      (^String getName [_]
        (str realm "-roles"))
      (^UserIdentity login [_ ^String _username ^Object _credentials ^Request _request ^Function _get-or-create])
      (^UserIdentity getUserIdentity [_ ^Subject _subject ^Principal user-principal ^boolean _create?]
        (let [user-creds        (.getCredentials ^OpenIdUserPrincipal user-principal) ;; roles-service is only used with OpenID
              user-access-token (access-token user-creds)
              user-state        (state user-creds user-access-token opts)
              new-principal     (OpenIdUserPrincipalWithState. user-creds user-state)
              new-subject       (Subject.)]
          (-> (.getPrincipals new-subject) (.add new-principal))
          (-> (.getPrivateCredentials new-subject) (.add user-creds))
          (.setReadOnly new-subject)
          (.newUserIdentity ^IdentityService @id-service-state new-subject new-principal (into-array String (:roles user-state)))))
      (^boolean validate [_ ^UserIdentity _user]
        true)
      (^IdentityService getIdentityService [_]
        @id-service-state)
      (^void setIdentityService [_ ^IdentityService identity-service]
        (reset! id-service-state identity-service))
      (^void logout [_ ^UserIdentity _user]))))

(defn configuration ^OpenIdConfiguration
  [{::openid/keys [issuer client-id client-secret authorization-endpoint token-endpoint end-session-endpoint
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
  [config {::openid/keys [oidc-redirect-success oidc-redirect-error oidc-redirect-logout]
           :or           {oidc-redirect-success OpenIdAuthenticator/J_SECURITY_CHECK}}]
  (OpenIdAuthenticator. config oidc-redirect-success oidc-redirect-error oidc-redirect-logout))

(defmethod openid/flow-handler :default
  [{::openid/keys [issuer] :as opts}]
  (log/debug "initializing authorization code flow")
  (let [openid-config (configuration opts)]
    (doto (SecurityHandler$PathMapped.)
      (.setAuthenticator (authenticator openid-config opts))
      (.setLoginService (OpenIdLoginService. openid-config (login-module issuer opts)))
      (.setRealmName issuer))))
