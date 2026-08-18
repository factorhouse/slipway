(ns slipway.security.oidc.jwt.at-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.security.oidc.jwk :as jwk]
            [slipway.security.oidc.jwk.rsa :as jwk.rsa]
            [slipway.security.oidc.jwt.at :as jwt.at]
            [slipway.security.oidc.jwt.at.verification :as jwt.at.verification])
  (:import (clojure.lang ExceptionInfo)
           (com.nimbusds.jose JOSEObjectType JWSAlgorithm JWSHeader$Builder)
           (com.nimbusds.jose.crypto RSASSASigner)
           (com.nimbusds.jose.jwk RSAKey)
           (com.nimbusds.jose.proc BadJOSEException)
           (com.nimbusds.jwt JWTClaimsSet$Builder SignedJWT)
           (com.nimbusds.jwt.proc BadJWTException ExpiredJWTException)
           (java.time Instant)
           (java.util Date List)))

(defn signed-jwt ^SignedJWT
  [^RSAKey rsa-key {:keys [typ jti iss aud sub iat exp]}]
  (let [header  (-> (JWSHeader$Builder. JWSAlgorithm/RS256)
                    (.type (JOSEObjectType. typ))
                    (.keyID (.getKeyID rsa-key))
                    (.build))
        builder (JWTClaimsSet$Builder.)]
    (when iss (.issuer builder iss))
    (when jti (.jwtID builder jti))
    (when aud
      (if (instance? String aud)
        (.audience builder ^String aud)
        (.audience builder ^List aud)))
    (when sub (.subject builder sub))
    (when iat (.issueTime builder (Date/from (Instant/ofEpochSecond iat))))
    (when exp (.expirationTime builder (Date/from (Instant/ofEpochSecond exp))))
    (doto (SignedJWT. header (.build builder))
      (.sign (RSASSASigner. (.toRSAPrivateKey rsa-key))))))

(deftest processor-creation

  (testing "missing required exact-iss"
    (is (thrown? ExceptionInfo
                 (jwt.at/processor
                  (jwk/source {::jwk/source  :rsa
                               ::jwk.rsa/key (jwk.rsa/jwk {})})
                  {::jwt.at.verification/exact-aud "https://slipway.io/api"}))))

  (testing "missing required exact-aud"
    (is (thrown? ExceptionInfo
                 (jwt.at/processor
                  (jwk/source {::jwk/source  :rsa
                               ::jwk.rsa/key (jwk.rsa/jwk {})})
                  {::jwt.at.verification/exact-iss "http://localhost:8080/realms/master"})))))

(deftest processor-defaults

  (let [rsa-key   (jwk.rsa/jwk {})
        now       (.getEpochSecond (Instant/now))
        processor (jwt.at/processor
                   (jwk/source {::jwk/source  :rsa
                                ::jwk.rsa/key rsa-key})
                   {::jwt.at.verification/exact-iss "http://localhost:8080/realms/master"
                    ::jwt.at.verification/exact-aud "https://slipway.io/api"})]

    (testing "all valid defaults met"

      ;; JWT is:
      ;; - signed by the correct certificate
      ;; - has the correct typ
      ;; - has the correct iss
      ;; - has the correct aud
      (is (= {"iss" "http://localhost:8080/realms/master"
              "jti" "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
              "aud" "https://slipway.io/api"
              "sub" "slipway-user-x"
              "iat" now
              "exp" (+ now 120)}
             (-> (.process processor
                           (signed-jwt rsa-key
                                       {:typ "at+jwt"
                                        :iss "http://localhost:8080/realms/master"
                                        :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                        :aud "https://slipway.io/api"
                                        :sub "slipway-user-x"
                                        :iat now
                                        :exp (+ now 120)})
                           (jwk.rsa/security-context rsa-key))
                 (.toJSONObject)))))

    (testing "jwt signed by the a different key"

      (is (thrown? BadJOSEException
                   (-> (.process (jwt.at/processor
                                  (jwk/source {::jwk/source  :rsa
                                               ::jwk.rsa/key (jwk.rsa/jwk {})})
                                  {::jwt.at.verification/exact-iss "http://localhost:8080/realms/master"
                                   ::jwt.at.verification/exact-aud "https://slipway.io/api"})
                                 (signed-jwt rsa-key
                                             {:typ "at+jwt"
                                              :iss "http://localhost:8080/realms/master"
                                              :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                              :aud "https://slipway.io/api"
                                              :sub "slipway-user-x"
                                              :iat now
                                              :exp (+ now 120)})
                                 (jwk.rsa/security-context rsa-key))
                       (.toJSONObject)))))

    (testing "missing iss"

      (is (= [BadJWTException "JWT missing required claims: [iss]"]
             (try (-> (.process processor
                                (signed-jwt rsa-key
                                            {:typ "at+jwt"
                                             :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                             :aud "https://slipway.io/api"
                                             :sub "slipway-user-x"
                                             :iat now
                                             :exp (+ now 120)})
                                (jwk.rsa/security-context rsa-key))
                      (.toJSONObject))
                  (catch Exception ex
                    [(type ex) (.getMessage ex)])))))

    (testing "wrong iss"

      (is (= [BadJWTException "JWT iss claim value rejected"]
             (try (-> (.process processor
                                (signed-jwt rsa-key
                                            {:typ "at+jwt"
                                             :iss "http://localhost:8080/realms/some-other-iss"
                                             :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                             :aud "https://slipway.io/api"
                                             :sub "slipway-user-x"
                                             :iat now
                                             :exp (+ now 120)})
                                (jwk.rsa/security-context rsa-key))
                      (.toJSONObject))
                  (catch Exception ex
                    [(type ex) (.getMessage ex)])))))

    (testing "missing aud"

      (is (= [BadJWTException "JWT missing required aud claim"]
             (try (-> (.process processor
                                (signed-jwt rsa-key
                                            {:typ "at+jwt"
                                             :iss "http://localhost:8080/realms/master"
                                             :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                             :sub "slipway-user-x"
                                             :iat now
                                             :exp (+ now 120)})
                                (jwk.rsa/security-context rsa-key))
                      (.toJSONObject))
                  (catch Exception ex
                    [(type ex) (.getMessage ex)])))))

    (testing "wrong aud"

      (is (= [BadJWTException "JWT aud claim rejected"]
             (try (-> (.process processor
                                (signed-jwt rsa-key
                                            {:typ "at+jwt"
                                             :iss "http://localhost:8080/realms/master"
                                             :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                             :aud "https://other-service.io/api"
                                             :sub "slipway-user-x"
                                             :iat now
                                             :exp (+ now 120)})
                                (jwk.rsa/security-context rsa-key))
                      (.toJSONObject))
                  (catch Exception ex
                    [(type ex) (.getMessage ex)])))))

    (testing "jwt has multi aud"

      (is (= [BadJWTException "JWT aud claim rejected"]
             (try (-> (.process processor
                                (signed-jwt rsa-key
                                            {:typ "at+jwt"
                                             :iss "http://localhost:8080/realms/master"
                                             :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                             :aud ["account" "https://other-service.io/api"]
                                             :sub "slipway-user-x"
                                             :iat now
                                             :exp (+ now 120)})
                                (jwk.rsa/security-context rsa-key))
                      (.toJSONObject))
                  (catch Exception ex
                    [(type ex) (.getMessage ex)])))))

    (testing "expired jwt"

      (is (thrown? ExpiredJWTException
                   (-> (.process processor
                                 (signed-jwt rsa-key
                                             {:typ "at+jwt"
                                              :iss "http://localhost:8080/realms/master"
                                              :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                              :aud "https://slipway.io/api"
                                              :sub "slipway-user-x"
                                              :iat (- now 120)
                                              :exp (- now 100)})
                                 (jwk.rsa/security-context rsa-key))
                       (.toJSONObject)))))

    (testing "missing jti"

      (is (= [BadJWTException "JWT missing required claims: [jti]"]
             (try (-> (.process processor
                                (signed-jwt rsa-key
                                            {:typ "at+jwt"
                                             :iss "http://localhost:8080/realms/master"
                                             :aud "https://slipway.io/api"
                                             :sub "slipway-user-x"
                                             :iat now
                                             :exp (+ now 120)})
                                (jwk.rsa/security-context rsa-key))
                      (.toJSONObject))
                  (catch Exception ex
                    [(type ex) (.getMessage ex)])))))

    (testing "missing sub"

      (is (= [BadJWTException "JWT missing required claims: [sub]"]
             (try (-> (.process processor
                                (signed-jwt rsa-key
                                            {:typ "at+jwt"
                                             :iss "http://localhost:8080/realms/master"
                                             :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                             :aud "https://slipway.io/api"
                                             :iat now
                                             :exp (+ now 120)})
                                (jwk.rsa/security-context rsa-key))
                      (.toJSONObject))
                  (catch Exception ex
                    [(type ex) (.getMessage ex)])))))

    (testing "missing iat"

      (is (= [BadJWTException "JWT missing required claims: [iat]"]
             (try (-> (.process processor
                                (signed-jwt rsa-key
                                            {:typ "at+jwt"
                                             :iss "http://localhost:8080/realms/master"
                                             :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                             :aud "https://slipway.io/api"
                                             :sub "slipway-user-x"
                                             :exp (+ now 120)})
                                (jwk.rsa/security-context rsa-key))
                      (.toJSONObject))
                  (catch Exception ex
                    [(type ex) (.getMessage ex)])))))

    (testing "missing exp"

      (is (= [BadJWTException "JWT missing required claims: [exp]"]
             (try (-> (.process processor
                                (signed-jwt rsa-key
                                            {:typ "at+jwt"
                                             :iss "http://localhost:8080/realms/master"
                                             :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                             :aud "https://slipway.io/api"
                                             :sub "slipway-user-x"
                                             :iat now})
                                (jwk.rsa/security-context rsa-key))
                      (.toJSONObject))
                  (catch Exception ex
                    [(type ex) (.getMessage ex)])))))))
