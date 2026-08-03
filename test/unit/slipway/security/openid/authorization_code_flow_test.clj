(ns slipway.security.openid.authorization-code-flow-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.security.openid.authorization-code-flow :as authorization-code-flow]
            [slipway.security.openid.jwt :as openid.jwt])
  (:import (org.eclipse.jetty.security.openid OpenIdCredentials)))

(defn munge-expiry
  [m]
  (update-in m [:expires-at] #(.getEpochSecond %1)))

(deftest state

  (is (= {:name                                  "factor-dev"
          :roles                                 #{"1" "2" "3"}
          :expires-at                            1311281970
          :slipway.security.openid/id-token      {"other" {"id" "x-name"}
                                                  "sub"   "factor-dev"}
          :slipway.security.openid/access-token  {"exp"   1311281970
                                                  "other" {"roles" ["x-role"]}
                                                  "roles" ["1" "2" "3"]}
          :slipway.security.openid/refresh-token nil
          :slipway.security.openid/response      nil}
         (munge-expiry
          (authorization-code-flow/state (OpenIdCredentials. {"sub"   "factor-dev"
                                                              "other" {"id" "x-name"}})
                                         {"roles" ["1" "2" "3"]
                                          "other" {"roles" ["x-role"]}
                                          "exp"   1311281970}
                                         {}))))

  ;; different configured path for name and roles (default tokens)
  (is (= {:name                                  "x-name"
          :roles                                 #{"x-role"}
          :expires-at                            1311281970
          :slipway.security.openid/id-token      {"other" {"id" "x-name"}
                                                  "sub"   "factor-dev"}
          :slipway.security.openid/access-token  {"other" {"roles" ["x-role"]}
                                                  "roles" ["1" "2" "3"]
                                                  "exp"   1311281970}
          :slipway.security.openid/refresh-token nil
          :slipway.security.openid/response      nil}
         (munge-expiry
          (authorization-code-flow/state (OpenIdCredentials. {"sub"   "factor-dev"
                                                              "other" {"id" "x-name"}})
                                         {"roles" ["1" "2" "3"]
                                          "other" {"roles" ["x-role"]}
                                          "exp"   1311281970}
                                         {::openid.jwt/user-roles-path ["other" "roles"]
                                          ::openid.jwt/user-id-path    ["other" "id"]}))))

  ;; different configured path for name and roles (roles taken from id-token)
  (is (= {:name                                  "x-name"
          :roles                                 #{"x-from-id-token"}
          :expires-at                            1311281970
          :slipway.security.openid/id-token      {"other" {"id"    "x-name"
                                                           "roles" ["x-from-id-token"]}
                                                  "sub"   "factor-dev"}
          :slipway.security.openid/access-token  {"other" {"roles" ["x-role"]}
                                                  "roles" ["1" "2" "3"]
                                                  "exp"   1311281970}
          :slipway.security.openid/refresh-token nil
          :slipway.security.openid/response      nil}
         (munge-expiry
          (authorization-code-flow/state (OpenIdCredentials. {"sub"   "factor-dev"
                                                              "other" {"id"    "x-name"
                                                                       "roles" ["x-from-id-token"]}})
                                         {"roles" ["1" "2" "3"]
                                          "other" {"roles" ["x-role"]}
                                          "exp"   1311281970}
                                         {::openid.jwt/user-roles-path   ["other" "roles"]
                                          ::openid.jwt/user-id-path      ["other" "id"]
                                          ::openid.jwt/user-roles-source :id-token}))))

  ;; different configured path for name and roles (id taken from access-token)
  (is (= {:name                                  "y-name"
          :roles                                 #{"x-role"}
          :expires-at                            1311281970
          :slipway.security.openid/id-token      {"other" {"id" "x-name"}
                                                  "sub"   "factor-dev"}
          :slipway.security.openid/access-token  {"other" {"id"    "y-name"
                                                           "roles" ["x-role"]}
                                                  "roles" ["1" "2" "3"]
                                                  "exp"   1311281970}
          :slipway.security.openid/refresh-token nil
          :slipway.security.openid/response      nil}
         (munge-expiry
          (authorization-code-flow/state (OpenIdCredentials. {"sub"   "factor-dev"
                                                              "other" {"id" "x-name"}})
                                         {"roles" ["1" "2" "3"]
                                          "other" {"roles" ["x-role"]
                                                   "id"    "y-name"}
                                          "exp"   1311281970}
                                         {::openid.jwt/user-roles-path ["other" "roles"]
                                          ::openid.jwt/user-id-path    ["other" "id"]
                                          ::openid.jwt/user-id-source  :access-token}))))

  ;; roles is scalar in the jwt, is presented as a single-element-set in the output
  ;; we encounter this occasionally with IdP that reduce single-role claims to a string
  (is (= {:name                                  "factor-dev"
          :roles                                 #{"1"}
          :expires-at                            1311281970
          :slipway.security.openid/id-token      {"other" {"id" "x-name"}
                                                  "sub"   "factor-dev"}
          :slipway.security.openid/access-token  {"other" {"roles" ["x-role"]}
                                                  "roles" "1"
                                                  "exp"   1311281970}
          :slipway.security.openid/refresh-token nil
          :slipway.security.openid/response      nil}
         (munge-expiry
          (authorization-code-flow/state (OpenIdCredentials. {"sub"   "factor-dev"
                                                              "other" {"id" "x-name"}})
                                         {"roles" "1"
                                          "other" {"roles" ["x-role"]}
                                          "exp"   1311281970}
                                         {})))))

(deftest no-exp-field-in-access-token

  ;; technically this shoudn't really happen, but if it does it just results in nil exp in the user principal
  (is (= {:name                                  "factor-dev"
          :roles                                 #{"1"}
          :expires-at                            nil
          :slipway.security.openid/id-token      {"other" {"id" "x-name"}
                                                  "sub"   "factor-dev"}
          :slipway.security.openid/access-token  {"other" {"roles" ["x-role"]}
                                                  "roles" "1"}
          :slipway.security.openid/refresh-token nil
          :slipway.security.openid/response      nil}
         (authorization-code-flow/state (OpenIdCredentials. {"sub"   "factor-dev"
                                                             "other" {"id" "x-name"}})
                                        {"roles" "1"
                                         "other" {"roles" ["x-role"]}}
                                        {}))))