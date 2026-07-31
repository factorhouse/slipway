(ns slipway.security.openid.jwk.rsa
  (:require [clojure.tools.logging :as log]
            [slipway.security.openid.jwk :as jwk])
  (:import (com.nimbusds.jose.jwk JWK JWKSet KeyUse RSAKey)
           (com.nimbusds.jose.jwk.gen RSAKeyGenerator)
           (com.nimbusds.jose.jwk.source ImmutableJWKSet JWKSource)
           (com.nimbusds.jose.proc JWKSecurityContext SecurityContext)
           (java.util Date)))

(defn jwk ^RSAKey
  [{::keys [size id issue-time]
    :or    {size 2048}}]
  (-> (RSAKeyGenerator. size)
      (.keyUse KeyUse/SIGNATURE)
      (.keyID (or id (str (random-uuid))))
      (.issueTime (or issue-time (Date.)))
      (.generate)))

(defn security-context ^SecurityContext
  [key]
  (JWKSecurityContext. [key]))

(defmethod jwk/source :rsa ^JWKSource
  [{::keys [key]}]
  (log/debug "creating jwk-rsa source")
  (ImmutableJWKSet. (JWKSet. ^JWK key)))