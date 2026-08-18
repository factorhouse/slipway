(ns slipway.security.oidc-test
  (:require [clojure.core.protocols :as p]
            [clojure.test :refer [deftest is testing]]
            [slipway.principal :as principal]
            [slipway.security.oidc :as oidc]
            [slipway.user :as user])
  (:import (org.eclipse.jetty.security.openid OpenIdCredentials)
           (slipway.security.oidc.user.principal OpenIdUserPrincipalWithState)))

(deftest datafy

  (testing "credentials"

    (is (= {:id-token {"sub" "factor-dev"
                       "a"   "b"}
            :response {}}
           (p/datafy (OpenIdCredentials. {"sub" "factor-dev" "a" "b"})))))

  (testing "principal"

    (let [openid-principal (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                          {::principal/type ::oidc/principal
                                                           ::principal/name "factor-dev"
                                                           ::user/roles     ["one" "two" "three"]}
                                                          nil)]
      (is (= {::principal/type ::oidc/principal
              ::principal/name "factor-dev"
              ::user/roles     ["one" "two" "three"]
              ::oidc/principal openid-principal}
             (p/datafy openid-principal)))

      ;; the original principal object is carried on the datafied output
      (is (= openid-principal (oidc/principal (p/datafy openid-principal)))))))

(deftest configuration

  (is (= ["https:an.issuer.com"
          "client-id"
          "client-secret"
          ["profile" "email"]]                              ;; <-- default scopes
         ((juxt #(.getIssuer %1) #(.getClientId %1) #(.getClientSecret %1) #(.getScopes %1))
          (oidc/configuration #::oidc{:issuer            "https:an.issuer.com"
                                      :client-id     "client-id"
                                      :client-secret "client-secret"}))))

  (is (= []
         (.getScopes (oidc/configuration #::oidc{:issuer            "https:an.issuer.com"
                                                 :client-id     "client-id"
                                                 :client-secret "client-secret"
                                                 :scopes        []}))))

  (is (= ["another" "one"]
         (.getScopes (oidc/configuration #::oidc{:issuer            "https:an.issuer.com"
                                                 :client-id     "client-id"
                                                 :client-secret "client-secret"
                                                 :scopes        ["another" "one"]})))))

(deftest field-access

  (testing "id-token"
    (let [user {::principal/type    ::oidc/principal
                ::principal/name    "y-name"
                ::user/roles        #{"x-role"}
                ::user/expires-at   1311281970
                ::oidc/id-token     {"other"   {"id" "x-name"}
                                     "sub"   "factor-dev"
                                     "email" "one@two.com"}
                ::oidc/access-token {"other"   {"id"    "y-name"
                                                "roles" ["x-role"]}
                                     "roles" ["1" "2" "3"]
                                     "exp"   1311281970
                                     "email" "three@four.com"}
                ::oidc/response     {"access_token" "some-access-token"}}]
      (is (= "one@two.com"
             (oidc/id-token-field user "email")))))

  (testing "access-token"
    (let [user {::principal/type    ::oidc/principal
                ::principal/name    "y-name"
                ::user/roles        #{"x-role"}
                ::user/expires-at   1311281970
                ::oidc/id-token     {"other"   {"id" "x-name"}
                                     "sub"   "factor-dev"
                                     "email" "one@two.com"}
                ::oidc/access-token {"other"   {"id"    "y-name"
                                                "roles" ["x-role"]}
                                     "roles" ["1" "2" "3"]
                                     "exp"   1311281970
                                     "email" "three@four.com"}
                ::oidc/response     {"access_token" "some-access-token"}}]
      (is (= "three@four.com"
             (oidc/access-token-field user "email"))))))