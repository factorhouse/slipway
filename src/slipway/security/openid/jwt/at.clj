(ns slipway.security.openid.jwt.at
  (:require [slipway.security.openid.jwk :as jwk]
            [slipway.security.openid.jwk.source :as jwk.source]
            [slipway.security.openid.jws :as jws]
            [slipway.security.openid.jwt.at.verification :as verification])
  (:import (com.nimbusds.jwt.proc DefaultJWTProcessor JWTProcessor)
           (slipway.security.openid.jwt JWTProcessorBean)))

(defn processor ^JWTProcessor
  [jwk-source opts]
  (let [jwt-processor (DefaultJWTProcessor.)]
    (.setJWSKeySelector jwt-processor (jws/key-selector jwk-source opts))
    (.setJWSTypeVerifier jwt-processor (verification/type-verifier opts))
    (.setJWTClaimsSetVerifier jwt-processor (verification/claims-verifier opts))
    jwt-processor))

(defn processor-bean
  [opts]
  (JWTProcessorBean. (fn []
                       (let [jwk-source (jwk/source opts)]
                         {:processor  (processor jwk-source opts)
                          :jwk-source jwk-source}))
                     jwk.source/stop))

