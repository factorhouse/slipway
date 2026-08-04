(ns slipway.security.openid-client-credentials-test
  (:require [clj-http.client :as client]
            [clojure.test :refer [deftest is testing]]
            [slipway.connector.http :as http]
            [slipway.context :as context]
            [slipway.example.app :as app]
            [slipway.security :as security]
            [slipway.security.openid :as openid]
            [slipway.security.openid.jwk :as openid.jwk]
            [slipway.security.openid.jwk.rsa :as openid.jwk.rsa]
            [slipway.security.openid.jwt.verification :as openid.jwt.verification]
            [slipway.sente]
            [slipway.server :as server]
            [slipway.session :as session]
            [slipway.test-server :as test-server])
  (:import (com.nimbusds.jose JOSEObjectType JWSAlgorithm JWSHeader$Builder)
           (com.nimbusds.jose.crypto RSASSASigner)
           (com.nimbusds.jose.jwk RSAKey)
           (com.nimbusds.jwt JWTClaimsSet$Builder SignedJWT)
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

(deftest client-credentials-flow

  (try

    (let [rsa-key (openid.jwk.rsa/jwk {})
          now     (.getEpochSecond (Instant/now))]

      (test-server/start!
       #::server{:connector     {::http/port 3000}
                 :handler       {::context/ring-handler              (app/handler)
                                 ::security/handler                  :openid
                                 ::session/enabled?                  false
                                 ::openid/authorization-flow         :client-credentials
                                 ::openid.jwk/source                 :rsa
                                 ::openid.jwk.rsa/key                rsa-key
                                 ::openid.jwt.verification/exact-iss "http://localhost:8080/realms/master"
                                 ::openid.jwt.verification/exact-aud "https://slipway.io/api" ;; <-- set in keycloak-realms-with-client.json
                                 ::openid/constraint-mappings        app/constraints}
                 :error-handler app/server-error-handler})

      (testing "no authorization header"

        (is (= 401
               (:status (client/request {:url              "http://localhost:3000/user"
                                         :method           "GET"
                                         :throw-exceptions false})))))

      (testing "malformed authorization header"

        (is (= 401
               (:status (client/request {:url              "http://localhost:3000/user"
                                         :method           "GET"
                                         :headers          {:authorization "Bearer gibberish"}
                                         :throw-exceptions false})))))

      (testing "valid token"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/master"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :aud "https://slipway.io/api"
                                     :sub "slipway-user-x"
                                     :iat now
                                     :exp (+ now 120)})
                        (.serialize))]
          (is (= 200 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))

      (testing "jwt signed by the a different key"

        (let [token (-> (signed-jwt (openid.jwk.rsa/jwk {})
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/master"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :aud "https://slipway.io/api"
                                     :sub "slipway-user-x"
                                     :iat now
                                     :exp (+ now 120)})
                        (.serialize))]
          (is (= 401 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))

      (testing "missing iss"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :aud "https://slipway.io/api"
                                     :sub "slipway-user-x"
                                     :iat now
                                     :exp (+ now 120)})
                        (.serialize))]
          (is (= 401 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))

      (testing "wrong iss"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/wrong-iss"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :aud "https://slipway.io/api"
                                     :sub "slipway-user-x"
                                     :iat now
                                     :exp (+ now 120)})
                        (.serialize))]
          (is (= 401 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))

      (testing "missing aud"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/master"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :sub "slipway-user-x"
                                     :iat now
                                     :exp (+ now 120)})
                        (.serialize))]
          (is (= 401 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))

      (testing "wrong aud"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/master"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :aud "https://other-service.io/api"
                                     :sub "slipway-user-x"
                                     :iat now
                                     :exp (+ now 120)})
                        (.serialize))]
          (is (= 401 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))

      (testing "acceptable token with multiple aud"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/master"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :aud ["account" "https://slipway.io/api"]
                                     :sub "slipway-user-x"
                                     :iat now
                                     :exp (+ now 120)})
                        (.serialize))]
          (is (= 200 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))

      (testing "failed token with multiple aud, none match"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/master"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :aud ["account" "https://other-service.io/api"]
                                     :sub "slipway-user-x"
                                     :iat now
                                     :exp (+ now 120)})
                        (.serialize))]
          (is (= 401 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))

      (testing "missing jti"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/master"
                                     :aud "https://slipway.io/api"
                                     :sub "slipway-user-x"
                                     :iat now
                                     :exp (+ now 120)})
                        (.serialize))]
          (is (= 401 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))

      (testing "missing sub"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/master"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :aud "https://slipway.io/api"
                                     :iat now
                                     :exp (+ now 120)})
                        (.serialize))]
          (is (= 401 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))
      (testing "missing iat"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/master"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :aud "https://slipway.io/api"
                                     :sub "slipway-user-x"
                                     :exp (+ now 120)})
                        (.serialize))]
          (is (= 401 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))

      (testing "missing exp"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/master"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :aud "https://slipway.io/api"
                                     :sub "slipway-user-x"
                                     :iat now})
                        (.serialize))]
          (is (= 401 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false}))))))

      (testing "expired jwt"

        (let [token (-> (signed-jwt rsa-key
                                    {:typ "at+jwt"
                                     :iss "http://localhost:8080/realms/master"
                                     :jti "trrtcc:f28c3021-a469-59d4-7c94-52e94357086d"
                                     :aud "https://slipway.io/api"
                                     :sub "slipway-user-x"
                                     :iat (- now 120)
                                     :exp (- now 100)})
                        (.serialize))]
          (is (= 401 (:status (client/request {:url              "http://localhost:3000/user"
                                               :method           "GET"
                                               :headers          {:authorization (str "Bearer " token)}
                                               :throw-exceptions false})))))))

    (finally
      (test-server/stop!))))