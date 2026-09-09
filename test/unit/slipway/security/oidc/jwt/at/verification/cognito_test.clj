(ns slipway.security.oidc.jwt.at.verification.cognito-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.security.oidc.jwt.at.verification :as verification]
            [slipway.security.oidc.jwt.at.verification.cognito])
  (:import (clojure.lang ExceptionInfo)
           (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier)
           (com.nimbusds.jwt JWTClaimNames)
           (com.nimbusds.jwt.proc DefaultJWTClaimsVerifier)))

(deftest type-verifier

  ;; default allowable 'typ' fields
  (is (= #{nil}                                             ;; not quite sure why the odd #{nil} here but it's the impl
         (->> ^DefaultJOSEObjectTypeVerifier (verification/type-verifier {::verification/vendor :amazon-cognito})
              (.getAllowedTypes))))

  ;; configured allowed-types are ignored for cognito
  (is (= #{nil}
         (->> ^DefaultJOSEObjectTypeVerifier (verification/type-verifier {::verification/vendor        :amazon-cognito
                                                                          ::verification/allowed-types ["abc" "efg"]})
              (.getAllowedTypes)))))

(deftest claims-verifier

  (testing "both required available"
    (is (not (nil? (verification/claims-verifier {::verification/vendor            :amazon-cognito
                                                  ::verification/required-issuer   "http://oidc-idp"
                                                  ::verification/required-audience "http://slipway-api"})))))

  (testing "required issuer throws"
    (is (thrown? ExceptionInfo (verification/claims-verifier {::verification/vendor            :amazon-cognito
                                                              ::verification/required-audience "http://slipway-api"}))))

  (testing "required audience throws (inbound-config check only, is applied as client_id in the actual verifier)"
    (is (thrown? ExceptionInfo (verification/claims-verifier {::verification/vendor          :amazon-cognito
                                                              ::verification/required-issuer "http://oidc-idp"}))))

  (testing "required claims"

    ;; default required claims
    (is (= #{"client_id"                                    ;; <-- required by amazon cognito
             "token_use"                                    ;; <-- required by amazon cognito
             "iss"                                          ;; <-- required due to required-issuer
             "exp"                                          ;; <-- here and below, default required claims set
             "iat"
             "sub"
             "jti"}
           (->> ^DefaultJWTClaimsVerifier (verification/claims-verifier
                                           {::verification/vendor            :amazon-cognito
                                            ::verification/required-issuer   "http://oidc-idp"
                                            ::verification/required-audience "http://slipway-api"})
                (.getRequiredClaims)
                set)))

    ;; specific required claims (removing jti)
    (is (= #{"client_id"                                    ;; <-- required by amazon cognito
             "token_use"                                    ;; <-- required by amazon cognito
             "iss"                                          ;; <-- required due to required-issuer
             "exp"                                          ;; <-- here and below, default required claims set
             "iat"
             "sub"}
           (->> ^DefaultJWTClaimsVerifier (verification/claims-verifier
                                           {::verification/vendor            :amazon-cognito
                                            ::verification/required-issuer   "http://oidc-idp"
                                            ::verification/required-audience "http://slipway-api"
                                            ::verification/required-claims   #{JWTClaimNames/SUBJECT
                                                                               JWTClaimNames/ISSUED_AT
                                                                               JWTClaimNames/EXPIRATION_TIME}})
                (.getRequiredClaims)
                set)))))
