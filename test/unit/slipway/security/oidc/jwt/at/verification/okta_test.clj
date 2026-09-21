(ns slipway.security.oidc.jwt.at.verification.okta-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.security.oidc.jwt.at.verification :as verification]
            [slipway.security.oidc.jwt.at.verification.okta])
  (:import (clojure.lang ExceptionInfo)
           (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier)
           (com.nimbusds.jwt JWTClaimNames)
           (com.nimbusds.jwt.proc DefaultJWTClaimsVerifier)))

(deftest type-verifier

  ;; default allowable 'typ' fields
  (is (= #{nil}                                             ;; not quite sure why the odd #{nil} here but it's the impl
         (->> ^DefaultJOSEObjectTypeVerifier (verification/type-verifier {::verification/vendor :okta})
              (.getAllowedTypes))))

  ;; configured allowed-types are ignored for okta
  (is (= #{nil}
         (->> ^DefaultJOSEObjectTypeVerifier (verification/type-verifier {::verification/vendor        :okta
                                                                          ::verification/allowed-types ["abc" "efg"]})
              (.getAllowedTypes)))))


(deftest claims-verifier

  ;; All tests the same as default claims verification
  (testing "both required available"
    (is (not (nil? (verification/claims-verifier {::verification/vendor            :okta
                                                  ::verification/required-issuer   "http://oidc-idp"
                                                  ::verification/required-audience "http://slipway-api"})))))

  (testing "required issuer throws"
    (is (thrown? ExceptionInfo (verification/claims-verifier {::verification/vendor            :okta
                                                              ::verification/required-audience "http://slipway-api"}))))

  (testing "required audience throws"
    (is (thrown? ExceptionInfo (verification/claims-verifier {::verification/vendor          :okta
                                                              ::verification/required-issuer "http://oidc-idp"}))))

  (testing "required claims"

    ;; default required claims
    (is (= #{"aud"                                          ;; <-- required due to required-audience
             "iss"                                          ;; <-- required due to required-issuer
             "exp"                                          ;; <-- here and below, default required claims set
             "iat"
             "jti"
             "sub"}
           (->> ^DefaultJWTClaimsVerifier (verification/claims-verifier
                                           {::verification/vendor            :okta
                                            ::verification/required-issuer   "http://oidc-idp"
                                            ::verification/required-audience "http://slipway-api"})
                (.getRequiredClaims)
                set)))

    ;; specific required claims (removing jti)
    (is (= #{"aud"                                          ;; <-- required due to required audience
             "iss"                                          ;; <-- required due to required issuer
             "exp"                                          ;; <-- here and below, specific required claims set
             "iat"
             "sub"}
           (->> ^DefaultJWTClaimsVerifier (verification/claims-verifier
                                           {::verification/vendor            :okta
                                            ::verification/required-issuer   "http://oidc-idp"
                                            ::verification/required-audience "http://slipway-api"
                                            ::verification/required-claims   #{JWTClaimNames/SUBJECT
                                                                               JWTClaimNames/ISSUED_AT
                                                                               JWTClaimNames/EXPIRATION_TIME}})
                (.getRequiredClaims)
                set)))))