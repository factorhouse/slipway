(ns slipway.security.openid.jws
  (:require [clojure.tools.logging :as log])
  (:import (com.nimbusds.jose JWSAlgorithm)
           (com.nimbusds.jose.jwk.source JWKSource)
           (com.nimbusds.jose.proc JWSVerificationKeySelector)
           (java.io Closeable)
           (java.util Set)))

;; defined in com.nimbusds.jose.JWSAlgorithm
(def recognized-algorithms
  #{"HS256" "HS384" "HS512" "RS256" "RS384" "RS512" "ES256" "ES256K" "ES384" "ES512" "PS256" "PS384" "PS512" "EdDSA"
    "Ed25519" "Ed448"})

(defn algorithm ^JWSAlgorithm
  [{::keys [algorithm]
    :or    {algorithm "RS256"}}]
  (if (recognized-algorithms algorithm)
    (JWSAlgorithm/parse algorithm)
    (throw (ex-info (str "unknown algorithm " algorithm)
                    {::algorithm             algorithm
                     ::recognized-algorithms recognized-algorithms}))))

(defn stop
  [^JWKSource key-source]
  ;; Some key-source are not closeable
  (when (instance? Closeable key-source)
    (log/debug "stopping key-source" key-source)
    (.close ^Closeable key-source)))

(comment
  #:slipway.security.openid.jws{:algorithm  "JSON Web Signature (JWS) algorithm name, represents the alg header parameter in JWS objects. Default is RS256."
                                :algorithms "A sequence of :algorithm if accepting multiple JWS algorithms."})

(defn key-selector ^JWSVerificationKeySelector
  [^JWKSource key-source {::keys [algorithms] :as opts}]
  (if algorithms
    (let [^Set algorithm-set (into #{} (map #(algorithm {::algorithm %1}) algorithms))]
      (log/debugf "creating key-selector with algorithms %s" (mapv #(.getName %1) algorithm-set))
      (JWSVerificationKeySelector. algorithm-set key-source))
    (let [^JWSAlgorithm algorithm (algorithm opts)]
      (log/debugf "creating key-selector with algorithm %s" algorithm)
      (JWSVerificationKeySelector. algorithm key-source))))