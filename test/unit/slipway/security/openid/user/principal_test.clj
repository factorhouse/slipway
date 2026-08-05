(ns slipway.security.openid.user.principal-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.principal :as principal]
            [slipway.security.openid :as openid]
            [slipway.user :as user])
  (:import (java.security Principal)
           (org.eclipse.jetty.security.openid OpenIdCredentials OpenIdUserPrincipal)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(deftest is-an-open-id-user-principal

  ;; Jetty handles session expiration on the assumption that users created by the OpenIdLoginService are
  ;; instances of OpenIdUserPrincipal, so it is important that our custom principal class subclasses correctly

  (let [creds (OpenIdCredentials. {"sub" "factor-dev"
                                   "a"   "b"})
        state {::principal/type ::openid/principal
               ::principal/name "name-comes-from-state-not-credentials"
               ::user/roles     ["one" "two" "three"]}]

    (is (instance? Principal (OpenIdUserPrincipalWithState. creds state nil)))
    (is (instance? OpenIdUserPrincipal (OpenIdUserPrincipalWithState. creds state nil)))

    (is (= "name-comes-from-state-not-credentials"
           (.getName (OpenIdUserPrincipalWithState. creds state nil))))

    ;; this mimics the behaviour of OpenIdUserPrincipal where .getName and .toString both return the name
    (is (= "name-comes-from-state-not-credentials"
           (.toString (OpenIdUserPrincipalWithState. creds state nil))))

    (is (= creds
           (.getCredentials (OpenIdUserPrincipalWithState. creds state nil))))))

(deftest get-state

  (is (= {::principal/type      ::openid/principal
          ::principal/name      "name-comes-from-state-not-credentials"
          ::user/roles          ["one" "two" "three"]
          ::openid/access-token {"exp" 1311281975}}
         (.getState (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                   {::principal/type      ::openid/principal
                                                    ::principal/name      "name-comes-from-state-not-credentials"
                                                    ::user/roles          ["one" "two" "three"]
                                                    ::openid/access-token {"exp" 1311281975}}
                                                   nil)))))
(deftest redeem-refresh-token

  (testing "no refresh-token-fn configured"

    (is (= nil
           (.redeemRefreshToken
            (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                           {:roles ["one" "two" "three"]}
                                           nil))))))

