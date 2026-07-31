(ns slipway.security.openid.jwt
  (:require [slipway.security.openid.jwk :as jwk]
            [slipway.security.openid.jwk.source :as jwk.source]
            [slipway.security.openid.jws :as jws]
            [slipway.security.openid.jwt.verification :as verification])
  (:import (com.nimbusds.jwt.proc DefaultJWTProcessor JWTProcessor)
           (slipway.security.openid.jwt JWTProcessorBean)))

(defn processor ^JWTProcessor
  [jwk-source opts]
  (let [jwt-processor (DefaultJWTProcessor.)]
    (.setJWSTypeVerifier jwt-processor (verification/type-verifier opts))
    (.setJWSKeySelector jwt-processor (jws/key-selector jwk-source opts))
    (.setJWTClaimsSetVerifier jwt-processor (verification/claims-verifier opts))
    jwt-processor))

(defn processor-bean
  [opts]
  (JWTProcessorBean. (fn []
                       (let [jwk-source (jwk/source opts)]
                         {:processor  (processor jwk-source opts)
                          :jwk-source jwk-source}))
                     jwk.source/stop))

(comment
  #:slipway.security.openid.jwt{:user-roles-source "the token containing user roles, either :access-token or :id-token (default is :access-token)"
                                :user-roles-path   "the path within the :roles token to find user roles, default is [\"roles\"]"
                                :user-id-source    "the token containing user id, either :access-token or :id-token (default is :id-token)"
                                :user-id-path      "the path within the :id-token token to find user name, default is [\"sub\"]"})