(ns slipway.security.openid-test
  (:require [clojure.core.protocols :as p]
            [clojure.test :refer [deftest is testing]]
            [slipway.principal :as principal]
            [slipway.security.openid :as openid]
            [slipway.user :as user])
  (:import (org.eclipse.jetty.security.openid OpenIdCredentials)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(deftest datafy

  (testing "credentials"

    (is (= {:id-token {"sub" "factor-dev"
                       "a"   "b"}
            :response {}}
           (p/datafy (OpenIdCredentials. {"sub" "factor-dev" "a" "b"})))))

  (testing "principal"

    (let [openid-principal (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                          {::principal/type ::openid/principal
                                                           ::principal/name "factor-dev"
                                                           ::user/roles     ["one" "two" "three"]}
                                                          nil)]
      (is (= {::principal/type   ::openid/principal
              ::principal/name   "factor-dev"
              ::user/roles       ["one" "two" "three"]
              ::openid/principal openid-principal}
             (p/datafy openid-principal)))

      ;; the original principal object is carried on the datafied output
      (is (= openid-principal (openid/principal (p/datafy openid-principal)))))))

(deftest configuration

  (is (= ["https:an.issuer.com"
          "client-id"
          "client-secret"
          ["profile" "email"]]                              ;; <-- default scopes
         ((juxt #(.getIssuer %1) #(.getClientId %1) #(.getClientSecret %1) #(.getScopes %1))
          (openid/configuration #::openid{:issuer        "https:an.issuer.com"
                                          :client-id     "client-id"
                                          :client-secret "client-secret"}))))

  (is (= []
         (.getScopes (openid/configuration #::openid{:issuer        "https:an.issuer.com"
                                                     :client-id     "client-id"
                                                     :client-secret "client-secret"
                                                     :scopes        []}))))

  (is (= ["another" "one"]
         (.getScopes (openid/configuration #::openid{:issuer        "https:an.issuer.com"
                                                     :client-id     "client-id"
                                                     :client-secret "client-secret"
                                                     :scopes        ["another" "one"]})))))

(deftest field-access

  (testing "id-token"
    (let [user {::principal/type      ::openid/principal
                ::principal/name      "y-name"
                ::user/roles          #{"x-role"}
                ::user/expires-at     1311281970
                ::openid/id-token     {"other" {"id" "x-name"}
                                       "sub"   "factor-dev"
                                       "email" "one@two.com"}
                ::openid/access-token {"other" {"id"    "y-name"
                                                "roles" ["x-role"]}
                                       "roles" ["1" "2" "3"]
                                       "exp"   1311281970
                                       "email" "three@four.com"}
                ::openid/response     {"access_token" "some-access-token"}}]
      (is (= "one@two.com"
             (openid/id-token-field user "email")))))

  (testing "access-token"
    (let [user {::principal/type      ::openid/principal
                ::principal/name      "y-name"
                ::user/roles          #{"x-role"}
                ::user/expires-at     1311281970
                ::openid/id-token     {"other" {"id" "x-name"}
                                       "sub"   "factor-dev"
                                       "email" "one@two.com"}
                ::openid/access-token {"other" {"id"    "y-name"
                                                "roles" ["x-role"]}
                                       "roles" ["1" "2" "3"]
                                       "exp"   1311281970
                                       "email" "three@four.com"}
                ::openid/response     {"access_token" "some-access-token"}}]
      (is (= "three@four.com"
             (openid/access-token-field user "email"))))))