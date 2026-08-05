(ns slipway.security.openid.authorization-code-flow
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.principal :as principal]
            [slipway.security.openid :as openid]
            [slipway.security.openid.jwt :as jwt]
            [slipway.security.openid.jwt.refresh :as refresh]
            [slipway.user :as user])
  (:import (java.security Principal)
           (java.time Instant)
           (java.util.function Function)
           (javax.security.auth Subject)
           (org.eclipse.jetty.security IdentityService LoginService SecurityHandler$PathMapped UserIdentity)
           (org.eclipse.jetty.security.openid JwtDecoder OpenIdAuthenticator OpenIdConfiguration OpenIdLoginService OpenIdUserPrincipal)
           (org.eclipse.jetty.server Request)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(defn access-token
  "As this login module is exclusively used in the direct Authorization Code Flow and this access token is intended for
   use in the same JVM as the ID token, we can rely on the proir ID token validation as being sufficient as they are
   both from the same source at the same point in time"
  [access-token-str]
  (try
    (JwtDecoder/decode access-token-str)
    (catch Exception ex
      (log/debug ex "error decoding access_token"))))

(defn username
  [id-token access-token {::jwt/keys [user-id-source user-id-path]
                          :or        {user-id-source :id-token
                                      user-id-path   ["sub"]}}]
  (let [name-token (if (= :access-token user-id-source) access-token id-token)]
    (get-in name-token user-id-path)))

(defn roles
  [id-token access-token {::jwt/keys [user-roles-source user-roles-path]
                          :or        {user-roles-source :access-token
                                      user-roles-path   ["roles"]}}]
  (let [roles-token (if (= :id-token user-roles-source) id-token access-token)
        roles-value (get-in roles-token user-roles-path)]
    (if (string? roles-value) #{roles-value} (set roles-value))))

(defn expiration
  [id-token access-token {::jwt/keys [user-expiration-source]
                          :or        {user-expiration-source :access-token}}]
  (let [expiration-token (if (= :id-token user-expiration-source) id-token access-token)]
    (when-let [exp (get expiration-token "exp")]
      ;; Jetty JWT library provides exp as a java.lang.Long at this point
      (Instant/ofEpochSecond ^Long exp))))

(defn user-state
  [id-token access-token response opts]
  (log/debugf "decoded [%s] claims from access token" (count access-token))
  (let [user-id         (username id-token access-token opts)
        user-roles      (roles id-token access-token opts)
        user-expires-at (expiration id-token access-token opts)]
    (log/debugf "user %s authorized with [%s] roles, expiring at %s" user-id (count user-roles) user-expires-at)
    {::principal/type      ::openid/principal
     ::principal/name      user-id
     ::user/roles          user-roles
     ::user/expires-at     user-expires-at
     ::openid/response     response
     ::openid/id-token     id-token
     ::openid/access-token access-token}))

(defn user-state-fn
  [opts]
  (fn [id-token access-token response]
    (user-state id-token access-token response opts)))

(defn login-module
  "This roles-service is exclusively for OpenID Connect (OIDC) direct Authorization Code flow via the Token endpoint,
   that is how Jetty implements OIDC authentication interactions.
   In that flow you can rely on TLS (HTTPS) to authenticate the issuer instead of verifying the JWT signature.
   While that normally only applies to the ID token, in our case the Access token is intended for local use inside this
   client service JVM, and so the same logic applies."
  ^LoginService [^OpenIdConfiguration openid-config opts]
  (let [id-service-state (atom nil)
        refresh-fns      {:refresh-token-fn (refresh/redeem-token-fn openid-config)
                          :user-state-fn    (user-state-fn opts)}]
    (reify LoginService
      (^String getName [_]
        (str (.getIssuer openid-config) "-roles"))
      (^UserIdentity login [_ ^String _username ^Object _credentials ^Request _request ^Function _get-or-create])
      (^UserIdentity getUserIdentity [_ ^Subject _subject ^Principal user-principal ^boolean _create?]
        (let [user-creds        (.getCredentials ^OpenIdUserPrincipal user-principal)
              {:keys [id-token response]} (p/datafy user-creds)
              user-access-token (access-token (get response "access_token"))
              user-state        (user-state id-token user-access-token response opts)
              new-principal     (OpenIdUserPrincipalWithState. user-creds user-state refresh-fns)
              new-subject       (Subject.)]
          (-> (.getPrincipals new-subject) (.add new-principal))
          (-> (.getPrivateCredentials new-subject) (.add user-creds))
          ;; TODO: Consider: a refresh-token is redeemed and the user's roles (or other claims, name, etc) have changed.
          ;;       if we want to push principal/subject mutation on refresh-token redemption down further into Jetty
          ;;       Security we will have to consider not making the subject readOnly here. (we cant update it later..)
          ;;       It's not clear what the consequence of that is throughout Jetty Security itself, and we rely only
          ;;       on Jetty's constraints for determining if user is authenticated, not if a user is in-role.
          ;;       So for our purposes we can leave this best-behaviour in here for now.
          (.setReadOnly new-subject)
          (.newUserIdentity ^IdentityService @id-service-state
                            new-subject
                            new-principal
                            (into-array String (user/roles user-state)))))
      (^boolean validate [_ ^UserIdentity _user]
        true)
      (^IdentityService getIdentityService [_]
        @id-service-state)
      (^void setIdentityService [_ ^IdentityService identity-service]
        (reset! id-service-state identity-service))
      (^void logout [_ ^UserIdentity _user]))))

(defn authenticator ^OpenIdAuthenticator
  [config {::openid/keys [oidc-redirect-success oidc-redirect-error oidc-redirect-logout]
           :or           {oidc-redirect-success OpenIdAuthenticator/J_SECURITY_CHECK}}]
  (OpenIdAuthenticator. config oidc-redirect-success oidc-redirect-error oidc-redirect-logout))

(defmethod openid/flow-handler :default
  [{::openid/keys [issuer] :as opts}]
  (log/debug "initializing authorization code flow")
  (let [openid-config (openid/configuration opts)]
    (doto (SecurityHandler$PathMapped.)
      (.setAuthenticator (authenticator openid-config opts))
      (.setLoginService (OpenIdLoginService. openid-config (login-module openid-config opts)))
      (.setRealmName issuer))))
