(ns slipway.security.openid.authorization-code-flow-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.principal :as principal]
            [slipway.security.openid :as openid]
            [slipway.security.openid.authorization-code-flow :as authorization-code-flow]
            [slipway.security.openid.jwt :as openid.jwt]
            [slipway.user :as user]))

(defn munge-expiry
  [m]
  (update-in m [::user/expires-at] #(.getEpochSecond %1)))

(deftest state

  (is (= {::principal/type      ::openid/principal
          ::principal/name      "factor-dev"
          ::user/roles          #{"1" "2" "3"}
          ::user/expires-at     1311281970
          ::openid/id-token     {"other" {"id" "x-name"}
                                 "sub"   "factor-dev"}
          ::openid/access-token {"exp"   1311281970
                                 "other" {"roles" ["x-role"]}
                                 "roles" ["1" "2" "3"]}
          ::openid/response     {"access_token" "some-access-token"}}
         (munge-expiry
          (authorization-code-flow/user-state
           {"sub"   "factor-dev"
            "other" {"id" "x-name"}}
           {"roles" ["1" "2" "3"]
            "other" {"roles" ["x-role"]}
            "exp"   1311281970}
           {"access_token" "some-access-token"}
           {}))))

  ;; different configured path for name and roles (default tokens)
  (is (= {::principal/type      ::openid/principal
          ::principal/name      "x-name"
          ::user/roles          #{"x-role"}
          ::user/expires-at     1311281970
          ::openid/id-token     {"other" {"id" "x-name"}
                                 "sub"   "factor-dev"}
          ::openid/access-token {"other" {"roles" ["x-role"]}
                                 "roles" ["1" "2" "3"]
                                 "exp"   1311281970}
          ::openid/response     {"access_token" "some-access-token"}}
         (munge-expiry
          (authorization-code-flow/user-state
           {"sub"   "factor-dev"
            "other" {"id" "x-name"}}
           {"roles" ["1" "2" "3"]
            "other" {"roles" ["x-role"]}
            "exp"   1311281970}
           {"access_token" "some-access-token"}
           {::openid.jwt/user-roles-path ["other" "roles"]
            ::openid.jwt/user-id-path    ["other" "id"]}))))

  ;; different configured path for name and roles (roles taken from id-token)
  (is (= {::principal/type      ::openid/principal
          ::principal/name      "x-name"
          ::user/roles          #{"x-from-id-token"}
          ::user/expires-at     1311281970
          ::openid/id-token     {"other" {"id"    "x-name"
                                          "roles" ["x-from-id-token"]}
                                 "sub"   "factor-dev"}
          ::openid/access-token {"other" {"roles" ["x-role"]}
                                 "roles" ["1" "2" "3"]
                                 "exp"   1311281970}
          ::openid/response     {"access_token" "some-access-token"}}
         (munge-expiry
          (authorization-code-flow/user-state
           {"sub"   "factor-dev"
            "other" {"id"    "x-name"
                     "roles" ["x-from-id-token"]}}
           {"roles" ["1" "2" "3"]
            "other" {"roles" ["x-role"]}
            "exp"   1311281970}
           {"access_token" "some-access-token"}
           {::openid.jwt/user-roles-path   ["other" "roles"]
            ::openid.jwt/user-id-path      ["other" "id"]
            ::openid.jwt/user-roles-source :id-token}))))

  ;; different configured path for name and roles (id taken from access-token)
  (is (= {::principal/type      ::openid/principal
          ::principal/name      "y-name"
          ::user/roles          #{"x-role"}
          ::user/expires-at     1311281970
          ::openid/id-token     {"other" {"id" "x-name"}
                                 "sub"   "factor-dev"}
          ::openid/access-token {"other" {"id"    "y-name"
                                          "roles" ["x-role"]}
                                 "roles" ["1" "2" "3"]
                                 "exp"   1311281970}
          ::openid/response     {"access_token" "some-access-token"}}
         (munge-expiry
          (authorization-code-flow/user-state
           {"sub"   "factor-dev"
            "other" {"id" "x-name"}}
           {"roles" ["1" "2" "3"]
            "other" {"roles" ["x-role"]
                     "id"    "y-name"}
            "exp"   1311281970}
           {"access_token" "some-access-token"}
           {::openid.jwt/user-roles-path ["other" "roles"]
            ::openid.jwt/user-id-path    ["other" "id"]
            ::openid.jwt/user-id-source  :access-token}))))

  ;; roles is scalar in the jwt, is presented as a single-element-set in the output
  ;; we encounter this occasionally with IdP that reduce single-role claims to a string
  (is (= {::principal/type      ::openid/principal
          ::principal/name      "factor-dev"
          ::user/roles          #{"1"}
          ::user/expires-at     1311281970
          ::openid/id-token     {"other" {"id" "x-name"}
                                 "sub"   "factor-dev"}
          ::openid/access-token {"other" {"roles" ["x-role"]}
                                 "roles" "1"
                                 "exp"   1311281970}
          ::openid/response     {"access_token" "some-access-token"}}
         (munge-expiry
          (authorization-code-flow/user-state
           {"sub"   "factor-dev"
            "other" {"id" "x-name"}}
           {"roles" "1"
            "other" {"roles" ["x-role"]}
            "exp"   1311281970}
           {"access_token" "some-access-token"}
           {})))))

(deftest no-exp-field-in-access-token

  ;; technically this shoudn't really happen, but if it does it just results in nil exp in the user principal
  (is (= {::principal/type      ::openid/principal
          ::principal/name      "factor-dev"
          ::user/roles          #{"1"}
          ::user/expires-at     nil
          ::openid/id-token     {"other" {"id" "x-name"}
                                 "sub"   "factor-dev"}
          ::openid/access-token {"other" {"roles" ["x-role"]}
                                 "roles" "1"}
          ::openid/response     {"access_token" "some-access-token"}}
         (authorization-code-flow/user-state
          {"sub"   "factor-dev"
           "other" {"id" "x-name"}}
          {"roles" "1"
           "other" {"roles" ["x-role"]}}
          {"access_token" "some-access-token"}
          {}))))