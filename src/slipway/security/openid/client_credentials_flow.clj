(ns slipway.security.openid.client-credentials-flow
  (:require [clojure.tools.logging :as log]
            [slipway.principal :as principal]
            [slipway.security.openid :as openid]
            [slipway.security.openid.bearer-token :as bearer-token]
            [slipway.security.openid.jwks]
            [slipway.security.openid.jwt :as openid.jwt]
            [slipway.security.openid.jwt.at :as openid.jwt.at]
            [slipway.user :as user])
  (:import (com.nimbusds.jwt.proc JWTProcessor)
           (java.security Principal)
           (java.util Date)
           (java.util.function Function)
           (javax.security.auth Subject)
           (org.eclipse.jetty.security IdentityService LoginService SecurityHandler$PathMapped UserIdentity)
           (org.eclipse.jetty.security.openid OpenIdCredentials)
           (org.eclipse.jetty.server Request)
           (slipway.security.openid.jwt JWTProcessorBean)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(defn access-token
  [^JWTProcessor jwt-processor ^String credentials]
  (try
    (-> (.process jwt-processor credentials nil)
        (.getClaims))
    (catch Exception ex
      (log/debug ex "error decoding access_token"))))

(defn roles
  [access-token {::openid.jwt/keys [user-roles-path]
                 :or               {user-roles-path ["roles"]}}]
  (let [roles-value (get-in access-token user-roles-path)]
    (if (string? roles-value) #{roles-value} (set roles-value))))

(defn user-state
  [access-token {::openid.jwt/keys [user-id-path]
                 :or               {user-id-path ["sub"]}
                 :as               opts}]
  (let [user-id         (get-in access-token user-id-path)
        user-roles      (roles access-token opts)
        user-expires-at (when-let [access-token-exp (get access-token "exp")]
                          ;; JOSE Nimbus library provides exp as a java.util.Date at this point
                          (.toInstant ^Date access-token-exp))]
    (log/debugf "user %s authorized with [%s] roles, expiring at %s" user-id (count user-roles) user-expires-at)
    {::principal/type      ::openid/principal
     ::principal/name      user-id
     ::user/roles          user-roles
     ::user/expires-at     user-expires-at
     ::openid/access-token access-token}))

(defn login-service
  ^LoginService [realm ^JWTProcessorBean processor-bean opts]
  (let [id-service-state (atom nil)]
    (reify LoginService
      (^String getName [_]
        (str realm "-bearer"))
      (^UserIdentity login [_ ^String _username ^Object credentials ^Request _request ^Function _get-or-create]
        (when-let [user-access-token (-> (.getProcessor processor-bean) (access-token credentials))]
          (log/debugf "decoded [%s] claims from access token" (count user-access-token))
          (let [user-state       (user-state user-access-token opts)
                user-credentials (OpenIdCredentials. {"request" {"access-token" credentials}})
                new-principal    (OpenIdUserPrincipalWithState. user-credentials user-state nil)
                new-subject      (Subject.)]
            (-> (.getPrincipals new-subject) (.add new-principal))
            (-> (.getPrivateCredentials new-subject) (.add user-credentials))
            (.setReadOnly new-subject)
            (.newUserIdentity ^IdentityService @id-service-state
                              new-subject
                              new-principal
                              (into-array String (user/roles user-state))))))
      (^UserIdentity getUserIdentity [_ ^Subject _subject ^Principal _user-principal ^boolean _create?])
      (^boolean validate [_ ^UserIdentity _user]
        true)
      (^IdentityService getIdentityService [_]
        @id-service-state)
      (^void setIdentityService [_ ^IdentityService identity-service]
        (reset! id-service-state identity-service))
      (^void logout [_ ^UserIdentity _user]))))

(defmethod openid/flow-handler :client-credentials
  [{::openid/keys [issuer] :as opts}]
  (log/debug "initializing client credentials flow")
  (let [jwt-processor-bean (openid.jwt.at/processor-bean opts)]
    (doto (SecurityHandler$PathMapped.)
      (.setAuthenticator (bearer-token/authenticator))
      (.setLoginService (login-service issuer jwt-processor-bean opts))
      (.addBean jwt-processor-bean)
      (.setRealmName issuer))))
