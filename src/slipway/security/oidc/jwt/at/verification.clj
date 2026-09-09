(ns slipway.security.oidc.jwt.at.verification
  (:require [clojure.tools.logging :as log])
  (:import (com.nimbusds.jose JOSEObjectType)
           (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier JOSEObjectTypeVerifier)
           (com.nimbusds.jwt JWTClaimNames JWTClaimsSet$Builder)
           (com.nimbusds.jwt.proc DefaultJWTClaimsVerifier JWTClaimsSetVerifier)
           (java.util Set)))

;; This namespace provides functions that verify OAuth 2.0 Access Tokens via the JOSE Nimbus library

;; The rules for validating tokens per the RFC are broadly:
;; https://datatracker.ietf.org/doc/html/rfc9068#section-2.1, and
;; https://datatracker.ietf.org/doc/html/rfc9068#section-4

;; However, in reality many commercial IDP don't strictly follow these rules, including both
;; Microsoft Entra ID and Keycloak regarding the JWT typ field in the header:
;;
;; https://learn.microsoft.com/en-us/entra/identity-platform/access-token-claims-reference
;; https://www.keycloak.org/2025/04/keycloak-2620-released
;;
;; For this reason, the functions here also default to some generally accepted varations.

(defmulti ^JOSEObjectTypeVerifier type-verifier ::vendor)
(defmulti ^JWTClaimsSetVerifier claims-verifier ::vendor)

(defmethod type-verifier :default
  [{::keys [allowed-types vendor]
    :or    {allowed-types ["JWT" "at+jwt" "application/at+jwt"]}}] ;; we include "JWT" extra to RFC, note above.
  (let [^Set object-types-set (set (map #(JOSEObjectType. %1) allowed-types))]
    (log/debugf "creating default type-verifier with allowed types %s" vendor)
    (DefaultJOSEObjectTypeVerifier. object-types-set)))

(defmethod claims-verifier :default
  [{::keys [required-issuer required-audience required-claims]
    :or    {required-claims #{JWTClaimNames/JWT_ID
                              JWTClaimNames/SUBJECT
                              JWTClaimNames/ISSUED_AT
                              JWTClaimNames/EXPIRATION_TIME}}}]
  (when-not required-issuer (throw (ex-info "missing required configuration: required-issuer" {})))
  (when-not required-audience (throw (ex-info "missing required configuration: required-audience" {})))
  (log/debugf "creating default claims-verifier for issuer %s and audience %s" required-issuer required-audience)
  (DefaultJWTClaimsVerifier.
   required-audience
   (-> (JWTClaimsSet$Builder.)
       (.issuer required-issuer)
       (.build))
   required-claims))

(comment
  #:slipway.security.oidc.jwt.at.verification{::vendor            "(optional) switch to a specific vendor verification implementation"
                                              ::allowed-types     "a sequence of acceptable 'typ' fields, default is ['JWT' 'at+jwt' 'application/at+jwt']"
                                              ::required-issuer   "the issuer identifier for the authorization server, presented as 'iss' in the JWT"
                                              ::required-audience "a resource indicator value corresponding to an identifier the resource server expects for itself, presented as 'aud' in the JWT"
                                              ::required-claims   "set of required JWTClaimNames. Default #{JWTClaimNames/JWT_ID JWTClaimNames/SUBJECT JWTClaimNames/ISSUED_AT JWTClaimNames/EXPIRATION_TIME}"})