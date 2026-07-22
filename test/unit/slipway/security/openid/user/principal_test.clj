(ns slipway.security.openid.user.principal-test
  (:require [clojure.test :refer [deftest is]])
  (:import (java.security Principal)
           (org.eclipse.jetty.security.openid OpenIdCredentials OpenIdUserPrincipal)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(deftest is-an-open-id-user-principal

  ;; Jetty handles session expiration on the assumption that users created by the OpenIdLoginService are
  ;; instances of OpenIdUserPrincipal, so it is important that our custom principal class subclasses correctly

  (let [creds (OpenIdCredentials. {"sub" "factor-dev"
                                   "a"   "b"})
        state {:roles ["one" "two" "three"]}]

    (is (instance? Principal (OpenIdUserPrincipalWithState. creds state)))
    (is (instance? OpenIdUserPrincipal (OpenIdUserPrincipalWithState. creds state)))

    (is (= "factor-dev"
           (.getName (OpenIdUserPrincipalWithState. creds state))))

    (is (= "factor-dev"
           (.toString (OpenIdUserPrincipalWithState. creds state))))

    (is (= creds
           (.getCredentials (OpenIdUserPrincipalWithState. creds state))))))

(deftest redeem-refresh-token

  (is (= false
         (.redeemRefreshToken (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                             {:roles ["one" "two" "three"]})))))
(deftest get-state

  (is (= {:roles ["one" "two" "three"]}
         (.getState (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                   {:roles ["one" "two" "three"]})))))

