(ns slipway.security.oidc.jwt.at.verification
  (:require [clojure.tools.logging :as log])
  (:import (com.nimbusds.jose JOSEObjectType)
           (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier JOSEObjectTypeVerifier)
           (com.nimbusds.jwt JWTClaimNames JWTClaimsSet$Builder)
           (com.nimbusds.jwt.proc DefaultJWTClaimsVerifier)
           (java.util Set)))

;; https://datatracker.ietf.org/doc/html/rfc9068#section-2.1
;; typ MUST conform to "application/at+jwt", RECOMMENDED that "application/" be ommitted
;; Keycloak (and possibly other IdP) encodes "JWT" at token type, you can encode that here or:
;;  - https://www.keycloak.org/2025/04/keycloak-2620-released
;;  - see: New client configuration for access token header type
;;  - set "access.token.header.type.rfc9068": "true" in ["client" "attributes"] to have Keycloak conform to the RFC

(defn type-verifier ^JOSEObjectTypeVerifier
  [{::keys [allowed-types]
    :or    {allowed-types ["at+jwt" "application/at+jwt"]}}]
  (let [^Set object-types-set (set (map #(JOSEObjectType. %1) allowed-types))]
    (log/debugf "creating type-verifier with allowed types %s" (mapv #(.getType %1) object-types-set))
    (DefaultJOSEObjectTypeVerifier. object-types-set)))

(defn claims-verifier
  "A claims verifier for OAuth 2.0 access tokens"
  [{::keys [required-issuer required-audience required-claims]
    :or    {required-claims #{JWTClaimNames/JWT_ID
                              JWTClaimNames/SUBJECT
                              JWTClaimNames/ISSUED_AT
                              JWTClaimNames/EXPIRATION_TIME}}}]
  (when-not required-issuer (throw (ex-info "missing required configuration: required-issuer" {})))
  (when-not required-audience (throw (ex-info "missing required configuration: required-audience" {})))
  (log/debugf "creating claims-verifier for issuer %s and audience %s" required-issuer required-audience)
  (DefaultJWTClaimsVerifier.
   required-audience
   (-> (JWTClaimsSet$Builder.)
       (.issuer required-issuer)
       (.build))
   required-claims))

(comment
  #:slipway.security.oidc.jwt.at.verification{::allowed-types     "a sequence of acceptable 'typ' fields, default is ['at+jwt' 'application/at+jwt']"
                                              ::required-issuer   "required: the URL of the OIDC provider"
                                              ::required-audience "required: the audience of this service to match the 'aud' field in the jwt"
                                              ::required-claims   "set of required JWTClaimNames. Default #{JWTClaimNames/JWT_ID JWTClaimNames/SUBJECT JWTClaimNames/ISSUED_AT JWTClaimNames/EXPIRATION_TIME}"})