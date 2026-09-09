(ns slipway.security.oidc.jwt.at.verification-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.security.oidc.jwt.at.verification :as verification])
  (:import (clojure.lang ExceptionInfo)
           (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier)
           (com.nimbusds.jwt JWTClaimNames)
           (com.nimbusds.jwt.proc DefaultJWTClaimsVerifier)))

(deftest type-verifier

  ;; default allowable 'typ' fields
  (is (= #{"JWT" "at+jwt" "application/at+jwt"}
         (->> ^DefaultJOSEObjectTypeVerifier (verification/type-verifier {})
              (.getAllowedTypes)
              (map #(.getType %1))
              set)))

  ;; can override to support anything
  (is (= #{"abc" "efg"}
         (->> ^DefaultJOSEObjectTypeVerifier (verification/type-verifier {::verification/allowed-types ["abc" "efg"]})
              (.getAllowedTypes)
              (map #(.getType %1))
              set))))

(deftest claims-verifier

  (testing "required issuer"
    (is (thrown? ExceptionInfo (verification/claims-verifier {::verification/required-audience "http://slipway-api"}))))

  (testing "required audience"
    (is (thrown? ExceptionInfo (verification/claims-verifier {::verification/required-issuer "http://oidc-idp"}))))

  (testing "required claims"

    ;; default required claims
    (is (= #{"aud"                                          ;; <-- required due to required-audience
             "iss"                                          ;; <-- required due to required-issuer
             "exp"                                          ;; <-- here and below, default required claims set
             "iat"
             "jti"
             "sub"}
           (->> ^DefaultJWTClaimsVerifier (verification/claims-verifier
                                           {::verification/required-issuer   "http://oidc-idp"
                                            ::verification/required-audience "http://slipway-api"})
                (.getRequiredClaims)
                set)))

    ;; specific required claims
    (is (= #{"aud"                                          ;; <-- required due to required audience
             "iss"                                          ;; <-- required due to required issuer
             "exp"                                          ;; <-- here and below, specific required claims set
             "iat"
             "sub"}
           (->> ^DefaultJWTClaimsVerifier (verification/claims-verifier
                                           {::verification/required-issuer   "http://oidc-idp"
                                            ::verification/required-audience "http://slipway-api"
                                            ::verification/required-claims   #{JWTClaimNames/SUBJECT
                                                                               JWTClaimNames/ISSUED_AT
                                                                               JWTClaimNames/EXPIRATION_TIME}})
                (.getRequiredClaims)
                set)))))
