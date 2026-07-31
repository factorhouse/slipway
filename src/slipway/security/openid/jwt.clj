(ns slipway.security.openid.jwt
  (:require [slipway.security.openid.jwks :as jwks]
            [slipway.security.openid.jws :as jws]
            [slipway.security.openid.jwt.verification :as verification])
  (:import (com.nimbusds.jwt.proc DefaultJWTProcessor)
           (slipway.security.openid.jwt JWTProcessorBean)))

(defn start-fn
  [opts]
  (fn []
    (let [jwt-processor (DefaultJWTProcessor.)
          key-source    (jwks/source opts)]
      (.setJWSTypeVerifier jwt-processor (verification/type-verifier opts))
      (.setJWSKeySelector jwt-processor (jws/key-selector key-source opts))
      (.setJWTClaimsSetVerifier jwt-processor (verification/claims-verifier opts))
      {:processor  jwt-processor
       :key-source key-source})))

(defn processor-bean
  [opts]
  (JWTProcessorBean. (start-fn opts) jws/stop))