(ns slipway.security.oidc.jwt.at.verification.cognito
  (:require [clojure.tools.logging :as log]
            [slipway.security.oidc.jwt.at.verification :as verification])
  (:import (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier)
           (com.nimbusds.jwt JWTClaimNames JWTClaimsSet$Builder)
           (com.nimbusds.jwt.proc DefaultJWTClaimsVerifier)))

;; This namespace provides functions that verify Amazon Cognito Access Tokens via the JOSE Nimbus library

;; The rules for validating Amazon Cognito tokens differ from the OAuth 2.0 JWT RFC:
;;
;; See: 'validate the JWT' and 'verify the claims'
;;   https://docs.aws.amazon.com/cognito/latest/developerguide/amazon-cognito-user-pools-using-tokens-verifying-a-jwt.html

;; See: 'Verified Permissions compares this list of app client IDs to the ID token aud claim or the access token client_id claim'
;;   https://docs.aws.amazon.com/verifiedpermissions/latest/userguide/cognito-validation.html

(defmethod verification/type-verifier :amazon-cognito
  [_opts]
  (log/debug "creating amazon cognito type-verifier")
  ;; Amazon Cognito JWT do not include a 'typ' header
  (DefaultJOSEObjectTypeVerifier.))

(defmethod verification/claims-verifier :amazon-cognito
  [{::verification/keys [required-issuer required-audience required-claims]
    :or                 {required-claims #{JWTClaimNames/JWT_ID
                                           JWTClaimNames/SUBJECT
                                           JWTClaimNames/ISSUED_AT
                                           JWTClaimNames/EXPIRATION_TIME}}}]
  (when-not required-issuer (throw (ex-info "missing required configuration: required-issuer" {})))
  (when-not required-audience (throw (ex-info "missing required configuration: required-audience" {})))
  (log/debugf "creating amazon cognito claims-verifier for issuer %s and client_id %s" required-issuer required-audience)
  (DefaultJWTClaimsVerifier.
   (-> (JWTClaimsSet$Builder.)
       (.issuer required-issuer)
       (.claim "client_id" required-audience)               ;; Amazon Cognito AT include the audience as the 'client_id' claim
       (.claim "token_use" "access")                        ;; Amazon Cognito AT require the verification of the 'token_use' field
       (.build))
   required-claims))