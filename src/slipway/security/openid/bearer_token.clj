(ns slipway.security.openid.bearer-token
  (:require [clojure.tools.logging :as log]
            [slipway.security.openid :as openid])
  (:import (com.nimbusds.jose JOSEObjectType JWSAlgorithm)
           (com.nimbusds.jose.jwk.source JWKSetBasedJWKSource JWKSourceBuilder)
           (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier JWSVerificationKeySelector)
           (com.nimbusds.jwt JWTClaimNames JWTClaimsSet$Builder)
           (com.nimbusds.jwt.proc DefaultJWTClaimsVerifier DefaultJWTProcessor)
           (java.net URI)
           (org.eclipse.jetty.http HttpHeader HttpStatus)
           (org.eclipse.jetty.security AuthenticationState)
           (org.eclipse.jetty.security.authentication LoginAuthenticator LoginAuthenticator$UserAuthenticationSucceeded)
           (org.eclipse.jetty.server Request Response)
           (org.eclipse.jetty.util Callback)
           (slipway.lifecycle ManagedState)))

(def authentication-type "OPENID_BEARER_TOKEN")
(def prefix "bearer ")

(defn is-bearer-auth-type?
  [^String credentials]
  (and credentials (.regionMatches credentials true 0 prefix 0 7)))

(defn authenticator
  []
  (proxy [LoginAuthenticator] []

    ;; only really used when configuring jetty.ini or web.xml, not so important for embedded servers
    (^boolean getAuthenticationType []
      authentication-type)

    (^AuthenticationState validateRequest [^Request request ^Response response ^Callback cb]
      (or (let [^String credentials (-> (.getHeaders request) (.get HttpHeader/AUTHORIZATION))]
            (when (is-bearer-auth-type? credentials)
              (when-let [user-identity (proxy-super login nil (-> (.substring credentials 7) (.trim)) request response)]
                (LoginAuthenticator$UserAuthenticationSucceeded. authentication-type user-identity))))
          (do
            (Response/writeError request response cb HttpStatus/UNAUTHORIZED_401)
            AuthenticationState/SEND_FAILURE)))))

(defn start-processor-fn
  [{::openid/keys [jwks-endpoint]}]
  (fn []
    (log/debugf "starting bearer-token processor with jwks-endpoint: %s" jwks-endpoint)
    (let [jwt-processor (DefaultJWTProcessor.)]
      (.setJWSTypeVerifier jwt-processor (DefaultJOSEObjectTypeVerifier. #{(JOSEObjectType. "JWT")})) ;; TODO: configurability
      ;; TODO lifecycle management of this key-source (bind into Jetty Container doStart/doStop)
      ;;      we can do that by adding it to the SecurityHandler as a bean, below (and implement Lifecycle interface)
      (let [key-source (-> (JWKSourceBuilder/create (.toURL (URI. jwks-endpoint)))
                           (.cache 300000 30000)            ;; TODO: configurability
                           (.retrying true)
                           (.build))]
        (.setJWSKeySelector jwt-processor
                            (JWSVerificationKeySelector. JWSAlgorithm/RS256 key-source)) ;; TODO: configurable alg
        (.setJWTClaimsSetVerifier jwt-processor
                                  (DefaultJWTClaimsVerifier.
                                   (-> (JWTClaimsSet$Builder.)
                                       (.issuer "http://localhost:8080/realms/master")
                                       (.build))
                                   #{JWTClaimNames/SUBJECT
                                     JWTClaimNames/ISSUED_AT
                                     JWTClaimNames/EXPIRATION_TIME
                                     JWTClaimNames/JWT_ID}))
        jwt-processor))))

(defn stop-processor-fn
  [^DefaultJWTProcessor processor]
  (let [key-source (.getJWKSource ^JWSVerificationKeySelector (.getJWSKeySelector processor))]
    (log/debugf "stopping bearer-token %s" processor)
    (.close ^JWKSetBasedJWKSource key-source)))

(defn processor-bean
  [opts]
  (ManagedState. (start-processor-fn opts) stop-processor-fn))