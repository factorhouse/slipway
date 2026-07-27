(ns slipway.security.openid.client-credentials-flow
  (:require [clojure.tools.logging :as log]
            [slipway.security.openid :as openid]
            [slipway.security.openid.bearer-token :as bearer-token])
  (:import (com.nimbusds.jose JOSEObjectType JWSAlgorithm)
           (com.nimbusds.jose.jwk.source JWKSourceBuilder)
           (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier JWSVerificationKeySelector)
           (com.nimbusds.jwt JWTClaimNames JWTClaimsSet$Builder)
           (com.nimbusds.jwt.proc DefaultJWTClaimsVerifier DefaultJWTProcessor JWTProcessor)
           (java.net URI)
           (java.security Principal)
           (java.util.function Function)
           (javax.security.auth Subject)
           (org.eclipse.jetty.security IdentityService LoginService SecurityHandler$PathMapped UserIdentity)
           (org.eclipse.jetty.security.openid OpenIdCredentials)
           (org.eclipse.jetty.server Request)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(defn access-token
  [^JWTProcessor jwt-processor ^String credentials]
  (try
    (-> (.process jwt-processor credentials nil)
        (.getClaims))
    (catch Exception ex
      (log/debug ex "error decoding access_token"))))

(defn roles
  [access-token {::openid/keys [user-roles-path]
                 :or           {user-roles-path ["roles"]}}]
  (let [roles-value (get-in access-token user-roles-path)]
    (if (string? roles-value) #{roles-value} (set roles-value))))

(defn state
  [access-token {::openid/keys [user-id-path]
                 :or           {user-id-path ["sub"]}
                 :as           opts}]
  (let [user-id    (get-in access-token user-id-path)
        user-roles (roles access-token opts)]
    (log/debugf "user %s authorized with [%s] roles" user-id (count user-roles))
    {:name                 user-id
     :roles                user-roles
     ::openid/access-token access-token}))

(defn login-service
  ^LoginService [realm jwt-processor opts]
  (let [id-service-state (atom nil)]
    (reify LoginService
      (^String getName [_]
        (str realm "-bearer"))
      (^UserIdentity login [_ ^String _username ^Object credentials ^Request _request ^Function _get-or-create]
        (when-let [user-access-token (access-token jwt-processor credentials)]
          (log/debugf "decoded [%s] claims from access token" (count user-access-token))
          (let [user-state       (state user-access-token opts)
                user-credentials (OpenIdCredentials. {"request" {"access-token" credentials}})
                new-principal    (OpenIdUserPrincipalWithState. user-credentials user-state)
                new-subject      (Subject.)]
            (-> (.getPrincipals new-subject) (.add new-principal))
            (-> (.getPrivateCredentials new-subject) (.add user-credentials))
            (.setReadOnly new-subject)
            (.newUserIdentity ^IdentityService @id-service-state new-subject new-principal (into-array String (:roles user-state))))))
      (^UserIdentity getUserIdentity [_ ^Subject _subject ^Principal _user-principal ^boolean _create?])
      (^boolean validate [_ ^UserIdentity _user]
        true)
      (^IdentityService getIdentityService [_]
        @id-service-state)
      (^void setIdentityService [_ ^IdentityService identity-service]
        (reset! id-service-state identity-service))
      (^void logout [_ ^UserIdentity _user]))))

(defn jwt-processor
  [{::openid/keys [jwks-endpoint]}]
  (let [jwt-processor (DefaultJWTProcessor.)]
    (.setJWSTypeVerifier jwt-processor (DefaultJOSEObjectTypeVerifier. #{(JOSEObjectType. "JWT")}))
    ;; TODO lifecycle management of this key-source (bind into Jetty Container doStart/doStop)
    ;;      we can do that by adding it to the SecurityHandler as a bean, below (and implement Lifecycle interface)
    (let [key-source (-> (JWKSourceBuilder/create (.toURL (URI. jwks-endpoint)))
                         (.cache 300000 30000)              ;; TODO: configurability
                         (.retrying true)
                         (.build))]
      (.setJWSKeySelector jwt-processor
                          (JWSVerificationKeySelector. JWSAlgorithm/RS256 key-source))
      (.setJWTClaimsSetVerifier jwt-processor
                                (DefaultJWTClaimsVerifier.
                                 (-> (JWTClaimsSet$Builder.)
                                     (.issuer "http://localhost:8080/realms/master")
                                     (.build))
                                 #{JWTClaimNames/SUBJECT
                                   JWTClaimNames/ISSUED_AT
                                   JWTClaimNames/EXPIRATION_TIME
                                   JWTClaimNames/JWT_ID}))
      jwt-processor)))

(defmethod openid/handler :client-credentials
  [{::openid/keys [issuer] :as opts}]
  (log/debug "initializing client credentials flow")
  (doto (SecurityHandler$PathMapped.)
    (.setAuthenticator (bearer-token/authenticator))
    (.setLoginService (login-service issuer (jwt-processor opts) opts))
    (.setRealmName issuer)))
