(ns slipway.security.oidc.jwt.at.verification.okta
  (:require [clojure.tools.logging :as log]
            [slipway.security.oidc.jwt.at.verification :as verification])
  (:import (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier)
           (com.nimbusds.jwt JWTClaimNames JWTClaimsSet$Builder)
           (com.nimbusds.jwt.proc DefaultJWTClaimsVerifier)))

;; This namespace provides functions that verify Okta Tokens via the JOSE Nimbus library

;; The rules for validating Okta tokens differ from the OAuth 2.0 JWT RFC:
;;
;; https://support.okta.com/help/s/article/the-typ-field-is-missing-from-the-access-token-header?language=en_US

(defmethod verification/type-verifier :okta
  [_opts]
  (log/debug "creating okta type-verifier")
  (DefaultJOSEObjectTypeVerifier.))                         ;; Okta JWT do not include a 'typ' header

(defmethod verification/claims-verifier :okta
  [{::keys [required-issuer required-audience required-claims]
    :or    {required-claims #{JWTClaimNames/JWT_ID
                              JWTClaimNames/SUBJECT
                              JWTClaimNames/ISSUED_AT
                              JWTClaimNames/EXPIRATION_TIME}}}]
  (when-not required-issuer (throw (ex-info "missing required configuration: required-issuer" {})))
  (when-not required-audience (throw (ex-info "missing required configuration: required-audience" {})))
  (log/debugf "creating okta claims-verifier for issuer %s and audience %s" required-issuer required-audience)
  (DefaultJWTClaimsVerifier.
   required-audience
   (-> (JWTClaimsSet$Builder.)
       (.issuer required-issuer)
       (.build))
   required-claims))