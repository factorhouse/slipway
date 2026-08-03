(ns slipway.security.openid.client-credentials-flow-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.security.openid.client-credentials-flow :as client-credentials-flow]
            [slipway.security.openid.jwt :as openid.jwt])
  (:import (com.nimbusds.jwt.util DateUtils)))

(defn munge-expiry
  [m]
  (update-in m [:expires-at] #(.getEpochSecond %1)))

(deftest state

  (is (= {:name                                 "factor-dev"
          :roles                                #{"1" "2" "3"}
          :expires-at                           1311281970
          :slipway.security.openid/access-token {"sub"   "factor-dev"
                                                 ;; JOSE Nimbus library decodes exp to java.util.Date
                                                 ;; This is different to the authorization flow which where the JWT
                                                 ;; decoding is handled by Jetty, and remains a long at this point
                                                 "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                                 "other" {"roles" ["x-role"]
                                                          "name"  "x-name"}
                                                 "roles" ["1" "2" "3"]}}
         (munge-expiry
          (client-credentials-flow/state {"sub"   "factor-dev"
                                          "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                          "roles" ["1" "2" "3"]
                                          "other" {"name"  "x-name"
                                                   "roles" ["x-role"]}}
                                         {}))))

  ;; different configured path for name and roles
  (is (= {:name                                 "x-name"
          :roles                                #{"x-role"}
          :expires-at                           1311281970
          :slipway.security.openid/access-token {"sub"   "factor-dev"
                                                 "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                                 "other" {"roles" ["x-role"]
                                                          "name"  "x-name"}
                                                 "roles" ["1" "2" "3"]}}
         (munge-expiry
          (client-credentials-flow/state {"sub"   "factor-dev"
                                          "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                          "roles" ["1" "2" "3"]
                                          "other" {"name"  "x-name"
                                                   "roles" ["x-role"]}}
                                         {::openid.jwt/user-roles-path ["other" "roles"]
                                          ::openid.jwt/user-id-path    ["other" "name"]}))))

  ;; roles is scalar in the jwt, is presented as a single-element-set in the output
  ;; we encounter this occasionally with IdP that reduce single-role claims to a string
  (is (= {:name                                 "factor-dev"
          :roles                                #{"1"}
          :expires-at                           1311281970
          :slipway.security.openid/access-token {"sub"   "factor-dev"
                                                 "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                                 "other" {"roles" ["x-role"]
                                                          "name"  "x-name"}
                                                 "roles" "1"}}
         (munge-expiry
          (client-credentials-flow/state {"sub"   "factor-dev"
                                          "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                          "roles" "1"
                                          "other" {"name"  "x-name"
                                                   "roles" ["x-role"]}}
                                         {})))))