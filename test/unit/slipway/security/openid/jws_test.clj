(ns slipway.security.openid.jws-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.security.openid.jws :as jws])
  (:import (clojure.lang ExceptionInfo)
           (com.nimbusds.jose JWSAlgorithm)
           (com.nimbusds.jose.jwk JWKSet)
           (com.nimbusds.jose.jwk.source ImmutableJWKSet)))

(deftest algorithm

  (testing "successful parse"

    ;; default is RS256
    (is (= JWSAlgorithm/RS256 (jws/algorithm {})))

    ;; other algs are available
    (is (= JWSAlgorithm/HS256 (jws/algorithm {::jws/algorithm "HS256"})))

    ;; algs are strictly case-sensitive
    (is (thrown? ExceptionInfo (jws/algorithm {::jws/algorithm "hs256"}))))

  (testing "failed parse"

    (is (= ["unknown algorithm some-text"
            #::jws{:algorithm             "some-text"
                   :recognized-algorithms #{"ES256"
                                            "ES256K"
                                            "ES384"
                                            "ES512"
                                            "Ed25519"
                                            "Ed448"
                                            "EdDSA"
                                            "HS256"
                                            "HS384"
                                            "HS512"
                                            "PS256"
                                            "PS384"
                                            "PS512"
                                            "RS256"
                                            "RS384"
                                            "RS512"}}]
           (try (jws/algorithm {::jws/algorithm "some-text"})
                (catch ExceptionInfo ex
                  ((juxt ex-message ex-data) ex)))))))

(deftest jwk-source

  (let [dummy-source (ImmutableJWKSet. (JWKSet.))]

    (testing "successful key-selector creation"

      ;; default is RS256
      (is (.isAllowed (jws/key-selector dummy-source {}) JWSAlgorithm/RS256))
      (is (not (.isAllowed (jws/key-selector dummy-source {}) JWSAlgorithm/HS256)))

      ;; when configured, only the specifically configured algorithm is allowed (what was default is now excluded)
      (is (not (.isAllowed (jws/key-selector dummy-source {::jws/algorithm "HS256"}) JWSAlgorithm/RS256)))
      (is (.isAllowed (jws/key-selector dummy-source {::jws/algorithm "HS256"}) JWSAlgorithm/HS256))

      ;; set multiple algorithms in the single key-selector
      (is (.isAllowed (jws/key-selector dummy-source {::jws/algorithms ["HS256" "Ed448"]}) JWSAlgorithm/HS256))
      (is (.isAllowed (jws/key-selector dummy-source {::jws/algorithms ["HS256" "Ed448"]}) JWSAlgorithm/Ed448))
      (is (not (.isAllowed (jws/key-selector dummy-source {::jws/algorithms ["HS256" "Ed448"]}) JWSAlgorithm/RS256))))

    (testing "failed key-selector creation"

      (is (= ["unknown algorithm some-text"
              #::jws{:algorithm             "some-text"
                     :recognized-algorithms #{"ES256"
                                              "ES256K"
                                              "ES384"
                                              "ES512"
                                              "Ed25519"
                                              "Ed448"
                                              "EdDSA"
                                              "HS256"
                                              "HS384"
                                              "HS512"
                                              "PS256"
                                              "PS384"
                                              "PS512"
                                              "RS256"
                                              "RS384"
                                              "RS512"}}]
             (try (jws/key-selector dummy-source {::jws/algorithm "some-text"})
                  (catch ExceptionInfo ex
                    ((juxt ex-message ex-data) ex)))))

      (is (= ["unknown algorithm some-text"
              #::jws{:algorithm             "some-text"
                     :recognized-algorithms #{"ES256"
                                              "ES256K"
                                              "ES384"
                                              "ES512"
                                              "Ed25519"
                                              "Ed448"
                                              "EdDSA"
                                              "HS256"
                                              "HS384"
                                              "HS512"
                                              "PS256"
                                              "PS384"
                                              "PS512"
                                              "RS256"
                                              "RS384"
                                              "RS512"}}]
             (try (jws/key-selector dummy-source {::jws/algorithms ["some-text" "RS256"]})
                  (catch ExceptionInfo ex
                    ((juxt ex-message ex-data) ex))))))))