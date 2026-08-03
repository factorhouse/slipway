(ns slipway.security.openid.user.principal-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.security.openid :as-alias openid])
  (:import (java.security Principal)
           (org.eclipse.jetty.security.openid OpenIdCredentials OpenIdUserPrincipal)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(deftest is-an-open-id-user-principal

  ;; Jetty handles session expiration on the assumption that users created by the OpenIdLoginService are
  ;; instances of OpenIdUserPrincipal, so it is important that our custom principal class subclasses correctly

  (let [creds (OpenIdCredentials. {"sub" "factor-dev"
                                   "a"   "b"})
        state {:name  "name-comes-from-state-not-credentials"
               :roles ["one" "two" "three"]}]

    (is (instance? Principal (OpenIdUserPrincipalWithState. creds state)))
    (is (instance? OpenIdUserPrincipal (OpenIdUserPrincipalWithState. creds state)))

    (is (= "name-comes-from-state-not-credentials"
           (.getName (OpenIdUserPrincipalWithState. creds state))))

    ;; this mimics the behaviour of OpenIdUserPrincipal where .getName and .toString both return the name
    (is (= "name-comes-from-state-not-credentials"
           (.toString (OpenIdUserPrincipalWithState. creds state))))

    (is (= creds
           (.getCredentials (OpenIdUserPrincipalWithState. creds state))))))

(deftest redeem-refresh-token

  (is (= false
         (.redeemRefreshToken (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                             {:roles ["one" "two" "three"]})))))
(deftest get-state

  (is (= {:roles                ["one" "two" "three"]
          ::openid/access-token {"exp" 1311281975}}
         (.getState (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                   {::openid/access-token {"exp" 1311281975}
                                                    :roles                ["one" "two" "three"]})))))

