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
            :response nil}
           (p/datafy (OpenIdCredentials. {"sub" "factor-dev" "a" "b"})))))

  (testing "principal"

    (let [openid-principal (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                          {::principal/type ::openid/principal
                                                           ::principal/name "factor-dev"
                                                           ::user/roles     ["one" "two" "three"]}
                                                          (constantly nil))]
      (is (= {::principal/type   ::openid/principal
              ::principal/name   "factor-dev"
              ::user/roles       ["one" "two" "three"]
              ::openid/principal openid-principal}
             (p/datafy openid-principal))))))

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