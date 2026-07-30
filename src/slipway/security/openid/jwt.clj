(ns slipway.security.openid.jwt
  (:require [slipway.security.openid.jwks :as jwks]
            [slipway.security.openid.jws :as jws])
  (:import (com.nimbusds.jose JOSEObjectType)
           (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier)
           (com.nimbusds.jwt JWTClaimNames JWTClaimsSet$Builder)
           (com.nimbusds.jwt.proc DefaultJWTClaimsVerifier DefaultJWTProcessor)
           (slipway.security.openid.jwt JWTProcessorBean)))

(defn type-verifier
  [_opts]
  (DefaultJOSEObjectTypeVerifier. #{(JOSEObjectType. "application/at+jwt")
                                    (JOSEObjectType. "at+jwt")
                                    (JOSEObjectType. "JWT")}))

(defn claims-verifier
  [_opts]
  (DefaultJWTClaimsVerifier.
   (-> (JWTClaimsSet$Builder.)
       (.issuer "http://localhost:8080/realms/master")
       (.build))
   #{JWTClaimNames/SUBJECT
     JWTClaimNames/ISSUED_AT
     JWTClaimNames/EXPIRATION_TIME
     JWTClaimNames/JWT_ID}))

(defn start-fn
  [opts]
  (fn []
    (let [jwt-processor (DefaultJWTProcessor.)
          key-source    (jwks/source opts)]
      (.setJWSTypeVerifier jwt-processor (type-verifier opts))
      (.setJWSKeySelector jwt-processor (jws/key-selector key-source opts))
      (.setJWTClaimsSetVerifier jwt-processor (claims-verifier opts))
      {:processor  jwt-processor
       :key-source key-source})))

(defn processor-bean
  [opts]
  (JWTProcessorBean. (start-fn opts) jws/stop))