(ns slipway.security.oidc.jwt.at.verification-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.security.oidc.jwt.at.verification :as verification])
  (:import (clojure.lang ExceptionInfo)
           (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier)
           (com.nimbusds.jwt JWTClaimNames)
           (com.nimbusds.jwt.proc DefaultJWTClaimsVerifier)))

(deftest type-verifier

  ;; default allowable 'typ' fields
  (is (= #{"at+jwt" "application/at+jwt"}
         (->> ^DefaultJOSEObjectTypeVerifier (verification/type-verifier {})
              (.getAllowedTypes)
              (map #(.getType %1))
              set)))

  ;; common override to support "JWT"
  (is (= #{"JWT"}
         (->> ^DefaultJOSEObjectTypeVerifier (verification/type-verifier {::verification/exact-typ ["JWT"]})
              (.getAllowedTypes)
              (map #(.getType %1))
              set))))

(deftest claims-verifier

  (testing "required exact-iss"
    (is (thrown? ExceptionInfo (verification/claims-verifier {::verification/exact-aud "http://slipway-api"}))))

  (testing "required exact-aud"
    (is (thrown? ExceptionInfo (verification/claims-verifier {::verification/exact-iss "http://oidc-idp"}))))

  (testing "required claims"

    ;; default required claims
    (is (= #{"aud"                                          ;; <-- required due to exact-iss
             "iss"                                          ;; <-- required due to exact-aud
             "exp"                                          ;; <-- here and below, default required claims set
             "iat"
             "jti"
             "sub"}
           (->> ^DefaultJWTClaimsVerifier (verification/claims-verifier
                                           {::verification/exact-iss "http://oidc-idp"
                                            ::verification/exact-aud "http://slipway-api"})
                (.getRequiredClaims)
                set)))

    ;; specific required claims
    (is (= #{"aud"                                          ;; <-- required due to exact-iss
             "iss"                                          ;; <-- required due to exact-aud
             "exp"                                          ;; <-- here and below, specific required claims set
             "iat"
             "sub"}
           (->> ^DefaultJWTClaimsVerifier (verification/claims-verifier
                                           {::verification/exact-iss       "http://oidc-idp"
                                            ::verification/exact-aud       "http://slipway-api"
                                            ::verification/required-claims #{JWTClaimNames/SUBJECT
                                                                             JWTClaimNames/ISSUED_AT
                                                                             JWTClaimNames/EXPIRATION_TIME}})
                (.getRequiredClaims)
                set)))))
