(ns slipway.security.openid.client-credentials-flow-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.security.openid.client-credentials-flow :as client-credentials-flow]
            [slipway.security.openid.jwt :as openid.jwt]))

(deftest state

  (is (= {:name                                 "factor-dev"
          :roles                                #{"1" "2" "3"}
          :slipway.security.openid/access-token {"sub"   "factor-dev"
                                                 "other" {"roles" ["x-role"]
                                                          "name"  "x-name"}
                                                 "roles" ["1" "2" "3"]}}
         (client-credentials-flow/state {"sub"   "factor-dev"
                                         "roles" ["1" "2" "3"]
                                         "other" {"name"  "x-name"
                                                  "roles" ["x-role"]}}
                                        {})))

  ;; different configured path for name and roles
  (is (= {:name                                 "x-name"
          :roles                                #{"x-role"}
          :slipway.security.openid/access-token {"sub"   "factor-dev"
                                                 "other" {"roles" ["x-role"]
                                                          "name"  "x-name"}
                                                 "roles" ["1" "2" "3"]}}
         (client-credentials-flow/state {"sub"   "factor-dev"
                                         "roles" ["1" "2" "3"]
                                         "other" {"name"  "x-name"
                                                  "roles" ["x-role"]}}
                                        {::openid.jwt/user-roles-path ["other" "roles"]
                                         ::openid.jwt/user-id-path    ["other" "name"]})))

  ;; roles is scalar in the jwt, is presented as a single-element-set in the output
  ;; we encounter this occasionally with IdP that reduce single-role claims to a string
  (is (= {:name                                 "factor-dev"
          :roles                                #{"1"}
          :slipway.security.openid/access-token {"sub"   "factor-dev"
                                                 "other" {"roles" ["x-role"]
                                                          "name"  "x-name"}
                                                 "roles" "1"}}
         (client-credentials-flow/state {"sub"   "factor-dev"
                                         "roles" "1"
                                         "other" {"name"  "x-name"
                                                  "roles" ["x-role"]}}
                                        {}))))