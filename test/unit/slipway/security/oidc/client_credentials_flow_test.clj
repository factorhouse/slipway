(ns slipway.security.oidc.client-credentials-flow-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.principal :as principal]
            [slipway.security.oidc :as oidc]
            [slipway.security.oidc.client-credentials-flow :as client-credentials-flow]
            [slipway.security.oidc.jwt :as oidc.jwt]
            [slipway.user :as user])
  (:import (com.nimbusds.jwt.util DateUtils)))

(defn munge-expiry
  [m]
  (update-in m [::user/expires-at] #(.getEpochSecond %1)))

(deftest state

  (is (= {::principal/type                    ::oidc/principal
          ::principal/name                    "factor-dev"
          ::user/roles                        #{"1" "2" "3"}
          ::user/expires-at                   1311281970
          :slipway.security.oidc/access-token {"sub"   "factor-dev"
                                               ;; JOSE Nimbus library decodes exp to java.util.Date
                                               ;; This is different to the authorization flow which where the JWT
                                               ;; decoding is handled by Jetty, and remains a long at this point
                                               "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                               "other" {"roles" ["x-role"]
                                                        "name"  "x-name"}
                                               "roles" ["1" "2" "3"]}}
         (munge-expiry
          (client-credentials-flow/user-state {"sub"   "factor-dev"
                                               "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                               "roles" ["1" "2" "3"]
                                               "other" {"name"  "x-name"
                                                        "roles" ["x-role"]}}
                                              {}))))

  (testing "identity-fn"

    ;; drop user for any reason (can lead to not-authenticated state)
    (is (= nil
           (client-credentials-flow/user-state {"sub"   "factor-dev"
                                                "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                                "roles" ["1" "2" "3"]
                                                "other" {"name"  "x-name"
                                                         "roles" ["x-role"]}}
                                               {::oidc/identity-fn (constantly nil)})))

    ;; amend roles to include default role, '*' in this case
    (is (= {::principal/type                    ::oidc/principal
            ::principal/name                    "factor-dev"
            ::user/roles                        #{"*" "1" "2" "3"}
            ::user/expires-at                   1311281970
            :slipway.security.oidc/access-token {"sub"   "factor-dev"
                                                 ;; JOSE Nimbus library decodes exp to java.util.Date
                                                 ;; This is different to the authorization flow which where the JWT
                                                 ;; decoding is handled by Jetty, and remains a long at this point
                                                 "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                                 "other" {"roles" ["x-role"]
                                                          "name"  "x-name"}
                                                 "roles" ["1" "2" "3"]}}
           (munge-expiry
            (client-credentials-flow/user-state {"sub"   "factor-dev"
                                                 "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                                 "roles" ["1" "2" "3"]
                                                 "other" {"name"  "x-name"
                                                          "roles" ["x-role"]}}
                                                {::oidc/identity-fn (fn [user]
                                                                      (update user ::user/roles conj "*"))})))))

  ;; different configured path for name and roles
  (is (= {::principal/type                    ::oidc/principal
          ::principal/name                    "x-name"
          ::user/roles                        #{"x-role"}
          ::user/expires-at                   1311281970
          :slipway.security.oidc/access-token {"sub"   "factor-dev"
                                               "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                               "other" {"roles" ["x-role"]
                                                        "name"  "x-name"}
                                               "roles" ["1" "2" "3"]}}
         (munge-expiry
          (client-credentials-flow/user-state {"sub"   "factor-dev"
                                               "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                               "roles" ["1" "2" "3"]
                                               "other" {"name"  "x-name"
                                                        "roles" ["x-role"]}}
                                              {::oidc.jwt/user-roles-path ["other" "roles"]
                                               ::oidc.jwt/user-id-path    ["other" "name"]}))))

  ;; roles is scalar in the jwt, is presented as a single-element-set in the output
  ;; we encounter this occasionally with IdP that reduce single-role claims to a string
  (is (= {::principal/type                    ::oidc/principal
          ::principal/name                    "factor-dev"
          ::user/roles                        #{"1"}
          ::user/expires-at                   1311281970
          :slipway.security.oidc/access-token {"sub"   "factor-dev"
                                               "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                               "other" {"roles" ["x-role"]
                                                        "name"  "x-name"}
                                               "roles" "1"}}
         (munge-expiry
          (client-credentials-flow/user-state {"sub"   "factor-dev"
                                               "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                               "roles" "1"
                                               "other" {"name"  "x-name"
                                                        "roles" ["x-role"]}}
                                              {}))))
  ;; when roles is nil in the jwt, it is resolved as an empty set
  (is (= {::principal/type                    ::oidc/principal
          ::principal/name                    "factor-dev"
          ::user/roles                        #{}
          ::user/expires-at                   1311281970
          :slipway.security.oidc/access-token {"sub"   "factor-dev"
                                               "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                               "other" {"roles" ["x-role"]
                                                        "name"  "x-name"}
                                               "roles" nil}}
         (munge-expiry
          (client-credentials-flow/user-state {"sub"   "factor-dev"
                                               "exp"   (DateUtils/fromSecondsSinceEpoch 1311281970)
                                               "roles" nil
                                               "other" {"name"  "x-name"
                                                        "roles" ["x-role"]}}
                                              {})))))