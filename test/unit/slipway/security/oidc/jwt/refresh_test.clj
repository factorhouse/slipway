(ns slipway.security.oidc.jwt.refresh-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.security.oidc.jwt.refresh :as refresh])
  (:import (clojure.lang ExceptionInfo)
           (org.eclipse.jetty.security.openid OpenIdCredentials$AuthenticationException)))

(deftest check-response

  ;; required access_token and token_type
  (is (= {"access_token" "a-token"
          "token_type"   "Bearer"}
         (refresh/check-response {"access_token" "a-token"
                                  "token_type"   "Bearer"})))

  ;; no access token
  (is (thrown? OpenIdCredentials$AuthenticationException
               (refresh/check-response {"token_type" "Bearer"})))

  ;; wrong token_type
  (is (thrown? OpenIdCredentials$AuthenticationException
               (refresh/check-response {"access_token" "a-token"
                                        "token_type"   "xyz"})))

  ;; response has "error" field
  (is (thrown? ExceptionInfo
               (refresh/check-response {"error" "some-error"}))))

(deftest validate-id-token-claims

  ;; all required fields match
  (is (= nil (refresh/validate-id-token-claims
              {"iss" "1"
               "sub" "2"
               "aud" "3"
               "azp" "4"}
              {"iss" "1"
               "sub" "2"
               "aud" "3"
               "azp" "4"})))

  ;; iss not matching
  (is (thrown? OpenIdCredentials$AuthenticationException
               (refresh/validate-id-token-claims
                {"iss" "1"
                 "sub" "2"
                 "aud" "3"
                 "azp" "4"}
                {"iss" "AA"
                 "sub" "2"
                 "aud" "3"
                 "azp" "4"})))

  ;; sub not matching
  (is (thrown? OpenIdCredentials$AuthenticationException
               (refresh/validate-id-token-claims
                {"iss" "1"
                 "sub" "2"
                 "aud" "3"
                 "azp" "4"}
                {"iss" "1"
                 "sub" "AA"
                 "aud" "3"
                 "azp" "4"})))

  ;; aud not matching
  (is (thrown? OpenIdCredentials$AuthenticationException
               (refresh/validate-id-token-claims
                {"iss" "1"
                 "sub" "2"
                 "aud" "3"
                 "azp" "4"}
                {"iss" "1"
                 "sub" "2"
                 "aud" "AA"
                 "azp" "4"})))

  ;; azp not matching
  (is (thrown? OpenIdCredentials$AuthenticationException
               (refresh/validate-id-token-claims
                {"iss" "1"
                 "sub" "2"
                 "aud" "3"
                 "azp" "4"}
                {"iss" "AA"
                 "sub" "2"
                 "aud" "3"
                 "azp" "AA"}))))