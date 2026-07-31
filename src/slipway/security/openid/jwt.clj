(ns slipway.security.openid.jwt
  (:require [slipway.security.openid.jwk :as jwk]
            [slipway.security.openid.jwk.source :as jwk.source]
            [slipway.security.openid.jws :as jws]
            [slipway.security.openid.jwt.verification :as verification])
  (:import (com.nimbusds.jwt.proc DefaultJWTProcessor)
           (slipway.security.openid.jwt JWTProcessorBean)))

(defn start-fn
  [opts]
  (fn []
    (let [jwt-processor (DefaultJWTProcessor.)
          jwk-source    (jwk/source opts)]
      (.setJWSTypeVerifier jwt-processor (verification/type-verifier opts))
      (.setJWSKeySelector jwt-processor (jws/key-selector jwk-source opts))
      (.setJWTClaimsSetVerifier jwt-processor (verification/claims-verifier opts))
      {:processor  jwt-processor
       :jwk-source jwk-source})))

(defn processor-bean
  [opts]
  (JWTProcessorBean. (start-fn opts) jwk.source/stop))