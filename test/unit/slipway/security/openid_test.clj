(ns slipway.security.openid-test
  (:require [clojure.core.protocols :as p]
            [clojure.test :refer [deftest is]]
            [slipway.principal :as principal]
            [slipway.security.openid :as openid]
            [slipway.user :as user])
  (:import (org.eclipse.jetty.security.openid OpenIdCredentials)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(deftest credentials

  (is (= {:id-token {"sub" "factor-dev"
                     "a"   "b"}
          :response nil}
         (p/datafy (OpenIdCredentials. {"sub" "factor-dev" "a" "b"})))))

(deftest principal

  (let [openid-principal (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                        {::principal/type ::openid/principal
                                                         ::principal/name "factor-dev"
                                                         ::user/roles     ["one" "two" "three"]})]
    (is (= {::principal/type   ::openid/principal
            ::principal/name   "factor-dev"
            ::user/roles       ["one" "two" "three"]
            ::openid/principal openid-principal}
           (p/datafy openid-principal)))))