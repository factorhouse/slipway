(ns slipway.security.openid.jwt.verification
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
  [{::keys [exact-typ]
    :or    {exact-typ ["at+jwt" "application/at+jwt"]}}]
  (let [^Set object-types-set (set (map #(JOSEObjectType. %1) exact-typ))]
    (log/debugf "creating type-verifier with types %s" (mapv #(.getType %1) object-types-set))
    (DefaultJOSEObjectTypeVerifier. object-types-set)))

(defn claims-verifier
  [{::keys [exact-iss exact-aud]}]
  (when-not (and exact-aud exact-iss)
    (throw (ex-info "exact-iss and exact-aud are required fields" {:exact-iss exact-iss ::exact-aud exact-aud})))
  (log/debugf "creating claims-verifier for issuer %s and aud %s" exact-iss exact-aud)
  (DefaultJWTClaimsVerifier.
   (-> (JWTClaimsSet$Builder.)
       (.issuer exact-iss)
       (.audience ^String exact-aud)
       (.build))
   #{JWTClaimNames/JWT_ID
     JWTClaimNames/SUBJECT
     JWTClaimNames/ISSUED_AT
     JWTClaimNames/EXPIRATION_TIME}))

(comment
  #:slipway.security.openid.jwt.verification{::exact-typ "a sequence of acceptable 'typ' fields, default is ['at+jwt' 'application/at+jwt']"
                                             ::exact-iss "required: the URL of the OpenID provider"
                                             ::exact-aud "required: the audience of this service to match the 'aud' field in the jwt"
                                             ::required-claims "set of required JWTClaimNames. Default #{JWTClaimNames/JWT_ID JWTClaimNames/SUBJECT JWTClaimNames/ISSUED_AT JWTClaimNames/EXPIRATION_TIME}"})