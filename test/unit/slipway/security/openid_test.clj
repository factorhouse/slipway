(ns slipway.security.openid-test
  (:require [clojure.core.protocols :as p]
            [clojure.test :refer [deftest is]]
            [slipway.security.openid :as openid])
  (:import (org.eclipse.jetty.security.openid OpenIdCredentials)))

(deftest credentials

  (is (= {:id-token {"sub" "factor-dev"
                     "a"   "b"}
          :response nil}
         (p/datafy (OpenIdCredentials. {"sub" "factor-dev" "a" "b"})))))

(deftest state

  (is (= {:name                                  "factor-dev"
          :roles                                 #{"1" "2" "3"}
          :slipway.security.openid/id-token      {"other" {"id" "x-name"}
                                                  "sub"   "factor-dev"}
          :slipway.security.openid/access-token  {"other" {"roles" ["x-role"]}
                                                  "roles" ["1" "2" "3"]}
          :slipway.security.openid/refresh-token nil
          :slipway.security.openid/response      nil}
         (openid/state (OpenIdCredentials. {"sub"   "factor-dev"
                                            "other" {"id" "x-name"}})
                       {"roles" ["1" "2" "3"]
                        "other" {"roles" ["x-role"]}}
                       {})))

  ;; different configured path for name and roles (default tokens)
  (is (= {:name                                  "x-name"
          :roles                                 #{"x-role"}
          :slipway.security.openid/id-token      {"other" {"id" "x-name"}
                                                  "sub"   "factor-dev"}
          :slipway.security.openid/access-token  {"other" {"roles" ["x-role"]}
                                                  "roles" ["1" "2" "3"]}
          :slipway.security.openid/refresh-token nil
          :slipway.security.openid/response      nil}
         (openid/state (OpenIdCredentials. {"sub"   "factor-dev"
                                            "other" {"id" "x-name"}})
                       {"roles" ["1" "2" "3"]
                        "other" {"roles" ["x-role"]}}
                       {::openid/user-roles-path ["other" "roles"]
                        ::openid/user-id-path    ["other" "id"]})))

  ;; different configured path for name and roles (roles taken from id-token)
  (is (= {:name                                  "x-name"
          :roles                                 #{"x-from-id-token"}
          :slipway.security.openid/id-token      {"other" {"id"    "x-name"
                                                           "roles" ["x-from-id-token"]}
                                                  "sub"   "factor-dev"}
          :slipway.security.openid/access-token  {"other" {"roles" ["x-role"]}
                                                  "roles" ["1" "2" "3"]}
          :slipway.security.openid/refresh-token nil
          :slipway.security.openid/response      nil}
         (openid/state (OpenIdCredentials. {"sub"   "factor-dev"
                                            "other" {"id"    "x-name"
                                                     "roles" ["x-from-id-token"]}})
                       {"roles" ["1" "2" "3"]
                        "other" {"roles" ["x-role"]}}
                       {::openid/user-roles-path ["other" "roles"]
                        ::openid/user-id-path    ["other" "id"]
                        ::openid/user-roles-token :id-token}))))