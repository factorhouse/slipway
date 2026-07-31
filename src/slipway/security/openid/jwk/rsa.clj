(ns slipway.security.openid.jwk.rsa
  (:require [slipway.security.openid.jwk :as jwk])
  (:import (com.nimbusds.jose.jwk JWK JWKSet KeyUse)
           (com.nimbusds.jose.jwk.gen RSAKeyGenerator)
           (com.nimbusds.jose.jwk.source ImmutableJWKSet JWKSource)
           (com.nimbusds.jose.proc JWKSecurityContext SecurityContext)
           (java.util Date)))

(defn jwk ^JWK
  [{::keys [size id issue-time]
    :or    {size 2048}}]
  (-> (RSAKeyGenerator. size)
      (.keyUse KeyUse/SIGNATURE)
      (.keyID (or id (str (random-uuid))))
      (.issueTime (or issue-time (Date.)))
      (.generate)
      (.toPublicJWK)))

(defn security-context ^SecurityContext
  [key]
  (JWKSecurityContext. [key]))

(defmethod jwk/source :rsa ^JWKSource
  [{::keys [key]}]
  (ImmutableJWKSet. (JWKSet. ^JWK key)))