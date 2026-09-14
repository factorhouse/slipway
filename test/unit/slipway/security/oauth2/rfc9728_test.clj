(ns slipway.security.oauth2.rfc9728-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.security.oauth2.rfc9728 :as rfc9728]))

(deftest metadata-url

  (is (= "http://localhost:3000/.well-known/oauth-protected-resource"
         (rfc9728/metadata-url "http://localhost:3000"))))

(deftest metadata-url-header-value

  (is (= "Bearer resource_metadata=\"http://localhost:3000/.well-known/oauth-protected-resource\""
         (rfc9728/metadata-url-header-value "http://localhost:3000"))))

(deftest metadata

  (is (= {} (rfc9728/metadata nil)))

  (is (= {"resource"                                   "http://localhost:3000"
          "authorization_servers"                      ["https://idp1:8000"
                                                        "https://idp2:8000"]
          "jwks_uri"                                   "http://localhost:8080/realms/master/protocol/openid-connect/certs"
          "scopes_supported"                           ["openid"
                                                        "profile"
                                                        "email"]
          "bearer_methods_supported"                   ["header"]
          "resource_signing_alg_values_supported"      ["RS256"]
          "resource_name"                              "slipway-api"
          "resource_documentation"                     "http://localhost:3000/docs"
          "resource_policy_uri"                        "http://localhost:3000/policy"
          "resource_tos_uri"                           "http://localhost:3000/tos"
          "tls_client_certificate_bound_access_tokens" true
          "authorization_details_types_supported"      [{"actions"   ["read"
                                                                      "write"]
                                                         "datatypes" ["contacts"
                                                                      "photos"]
                                                         "locations" ["https://example.com/customers"]
                                                         "type"      "customer_information"}]
          "dpop_signing_alg_values_supported"          ["RS256"]
          "dpop_bound_access_tokens_required"          true}
         (rfc9728/metadata
          #::rfc9728{:resource                                   "http://localhost:3000"
                     :authorization-servers                      ["https://idp1:8000" "https://idp2:8000"]
                     :jwks-uri                                   "http://localhost:8080/realms/master/protocol/openid-connect/certs"
                     :scopes-supported                           ["openid" "profile" "email"]
                     :bearer-methods-supported                   ["header"]
                     :resource-signing-alg-values-supported      ["RS256"]
                     :resource-name                              "slipway-api"
                     :resource-documentation                     "http://localhost:3000/docs"
                     :resource-policy-uri                        "http://localhost:3000/policy"
                     :resource-tos-uri                           "http://localhost:3000/tos"
                     :tls-client-certificate-bound-access-tokens true
                     :authorization-details-types-supported      [{"type"      "customer_information"
                                                                   "locations" ["https://example.com/customers"]
                                                                   "actions"   ["read" "write"]
                                                                   "datatypes" ["contacts" "photos"]}]
                     :dpop-signing-alg-values-supported          ["RS256"]
                     :dpop-bound-access-tokens-required          true}))))
