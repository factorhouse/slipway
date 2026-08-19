(ns slipway.security.oidc.authorization-code-flow-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.principal :as principal]
            [slipway.security.oidc :as oidc]
            [slipway.security.oidc.authorization-code-flow :as authorization-code-flow]
            [slipway.security.oidc.jwt :as oidc.jwt]
            [slipway.user :as user]))

(defn munge-expiry
  [m]
  (update-in m [::user/expires-at] #(.getEpochSecond %1)))

(deftest state

  (is (= {::principal/type    ::oidc/principal
          ::principal/name    "factor-dev"
          ::user/roles        #{"1" "2" "3"}
          ::user/expires-at   1311281970
          ::oidc/id-token     {"other" {"id" "x-name"}
                               "sub"   "factor-dev"}
          ::oidc/access-token {"exp"   1311281970
                               "other" {"roles" ["x-role"]}
                               "roles" ["1" "2" "3"]}
          ::oidc/response     {"access_token" "some-access-token"}}
         (munge-expiry
          (authorization-code-flow/user-state
           {"sub"   "factor-dev"
            "other" {"id" "x-name"}}
           {"roles" ["1" "2" "3"]
            "other" {"roles" ["x-role"]}
            "exp"   1311281970}
           {"access_token" "some-access-token"}
           {}))))

  (testing "identity-fn"

    ;; drop user for any reason (can lead to not-authenticated state)
    (is (= nil
           (authorization-code-flow/user-state
            {"sub"   "factor-dev"
             "other" {"id" "x-name"}}
            {"roles" ["1" "2" "3"]
             "other" {"roles" ["x-role"]}
             "exp"   1311281970}
            {"access_token" "some-access-token"}
            {::oidc/identity-fn (constantly nil)})))

    ;; amend roles to include default role, '*' in this case
    (is (= {::principal/type    ::oidc/principal
            ::principal/name    "factor-dev"
            ::user/roles        #{"*" "1" "2" "3"}
            ::user/expires-at   1311281970
            ::oidc/id-token     {"other" {"id" "x-name"}
                                 "sub"   "factor-dev"}
            ::oidc/access-token {"exp"   1311281970
                                 "other" {"roles" ["x-role"]}
                                 "roles" ["1" "2" "3"]}
            ::oidc/response     {"access_token" "some-access-token"}}
           (munge-expiry
            (authorization-code-flow/user-state
             {"sub"   "factor-dev"
              "other" {"id" "x-name"}}
             {"roles" ["1" "2" "3"]
              "other" {"roles" ["x-role"]}
              "exp"   1311281970}
             {"access_token" "some-access-token"}
             {::oidc/identity-fn (fn [user]
                                   (update user ::user/roles conj "*"))})))))

  ;; different configured path for name and roles (default tokens)
  (is (= {::principal/type    ::oidc/principal
          ::principal/name    "x-name"
          ::user/roles        #{"x-role"}
          ::user/expires-at   1311281970
          ::oidc/id-token     {"other" {"id" "x-name"}
                               "sub"   "factor-dev"}
          ::oidc/access-token {"other" {"roles" ["x-role"]}
                               "roles" ["1" "2" "3"]
                               "exp"   1311281970}
          ::oidc/response     {"access_token" "some-access-token"}}
         (munge-expiry
          (authorization-code-flow/user-state
           {"sub"   "factor-dev"
            "other" {"id" "x-name"}}
           {"roles" ["1" "2" "3"]
            "other" {"roles" ["x-role"]}
            "exp"   1311281970}
           {"access_token" "some-access-token"}
           {::oidc.jwt/user-roles-path ["other" "roles"]
            ::oidc.jwt/user-id-path    ["other" "id"]}))))

  ;; different configured path for name and roles (roles taken from id-token)
  (is (= {::principal/type    ::oidc/principal
          ::principal/name    "x-name"
          ::user/roles        #{"x-from-id-token"}
          ::user/expires-at   1311281970
          ::oidc/id-token     {"other" {"id"    "x-name"
                                        "roles" ["x-from-id-token"]}
                               "sub"   "factor-dev"}
          ::oidc/access-token {"other" {"roles" ["x-role"]}
                               "roles" ["1" "2" "3"]
                               "exp"   1311281970}
          ::oidc/response     {"access_token" "some-access-token"}}
         (munge-expiry
          (authorization-code-flow/user-state
           {"sub"   "factor-dev"
            "other" {"id"    "x-name"
                     "roles" ["x-from-id-token"]}}
           {"roles" ["1" "2" "3"]
            "other" {"roles" ["x-role"]}
            "exp"   1311281970}
           {"access_token" "some-access-token"}
           {::oidc.jwt/user-roles-path   ["other" "roles"]
            ::oidc.jwt/user-id-path      ["other" "id"]
            ::oidc.jwt/user-roles-source "id_token"}))))

  ;; different configured path for name and roles (id taken from access-token)
  (is (= {::principal/type    ::oidc/principal
          ::principal/name    "y-name"
          ::user/roles        #{"x-role"}
          ::user/expires-at   1311281970
          ::oidc/id-token     {"other" {"id" "x-name"}
                               "sub"   "factor-dev"}
          ::oidc/access-token {"other" {"id"    "y-name"
                                        "roles" ["x-role"]}
                               "roles" ["1" "2" "3"]
                               "exp"   1311281970}
          ::oidc/response     {"access_token" "some-access-token"}}
         (munge-expiry
          (authorization-code-flow/user-state
           {"sub"   "factor-dev"
            "other" {"id" "x-name"}}
           {"roles" ["1" "2" "3"]
            "other" {"roles" ["x-role"]
                     "id"    "y-name"}
            "exp"   1311281970}
           {"access_token" "some-access-token"}
           {::oidc.jwt/user-roles-path ["other" "roles"]
            ::oidc.jwt/user-id-path    ["other" "id"]
            ::oidc.jwt/user-id-source  "access_token"}))))

  ;; roles is scalar in the jwt, is presented as a single-element-set in the output
  ;; we encounter this occasionally with IdP that reduce single-role claims to a string
  (is (= {::principal/type    ::oidc/principal
          ::principal/name    "factor-dev"
          ::user/roles        #{"1"}
          ::user/expires-at   1311281970
          ::oidc/id-token     {"other" {"id" "x-name"}
                               "sub"   "factor-dev"}
          ::oidc/access-token {"other" {"roles" ["x-role"]}
                               "roles" "1"
                               "exp"   1311281970}
          ::oidc/response     {"access_token" "some-access-token"}}
         (munge-expiry
          (authorization-code-flow/user-state
           {"sub"   "factor-dev"
            "other" {"id" "x-name"}}
           {"roles" "1"
            "other" {"roles" ["x-role"]}
            "exp"   1311281970}
           {"access_token" "some-access-token"}
           {}))))

  ;; when roles is nil in the jwt, it is resolved as an empty set
  (is (= {::principal/type    ::oidc/principal
          ::principal/name    "factor-dev"
          ::user/roles        #{}
          ::user/expires-at   1311281970
          ::oidc/id-token     {"other" {"id" "x-name"}
                               "sub"   "factor-dev"}
          ::oidc/access-token {"other" {"roles" ["x-role"]}
                               "roles" nil
                               "exp"   1311281970}
          ::oidc/response     {"access_token" "some-access-token"}}
         (munge-expiry
          (authorization-code-flow/user-state
           {"sub"   "factor-dev"
            "other" {"id" "x-name"}}
           {"roles" nil
            "other" {"roles" ["x-role"]}
            "exp"   1311281970}
           {"access_token" "some-access-token"}
           {})))))

(deftest no-exp-field-in-access-token

  ;; technically this shoudn't really happen, but if it does it just results in nil exp in the user principal
  (is (= {::principal/type    ::oidc/principal
          ::principal/name    "factor-dev"
          ::user/roles        #{"1"}
          ::user/expires-at   nil
          ::oidc/id-token     {"other" {"id" "x-name"}
                               "sub"   "factor-dev"}
          ::oidc/access-token {"other" {"roles" ["x-role"]}
                               "roles" "1"}
          ::oidc/response     {"access_token" "some-access-token"}}
         (authorization-code-flow/user-state
          {"sub"   "factor-dev"
           "other" {"id" "x-name"}}
          {"roles" "1"
           "other" {"roles" ["x-role"]}}
          {"access_token" "some-access-token"}
          {}))))