(ns slipway.security.openid.user.principal-test
  (:require [clojure.core.protocols :as p]
            [clojure.test :refer [deftest is testing]]
            [slipway.principal :as principal]
            [slipway.security.openid :as openid]
            [slipway.user :as user])
  (:import (java.security Principal)
           (java.time Instant)
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
          ::principal/name      "factor-dev"
          ::user/expires-at     (Instant/ofEpochMilli 1311281975)
          ::openid/id-token     {"sub" "factor-dev"
                                 "exp" 1311281970}
          ::openid/access-token {"exp" 1311281975}
          ::openid/response     {"access_token" "first_at"}}
         (.getState (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                   {::principal/type      ::openid/principal
                                                    ::principal/name      "factor-dev"
                                                    ::user/expires-at     (Instant/ofEpochMilli 1311281975)
                                                    ::openid/id-token     {"sub" "factor-dev"
                                                                           "exp" 1311281970}
                                                    ::openid/access-token {"exp" 1311281975}
                                                    ::openid/response     {"access_token" "first_at"}}
                                                   nil)))))

(deftest expiry

  (is (user/expired? (p/datafy
                      (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                     {::principal/type      ::openid/principal
                                                      ::principal/name      "factor-dev"
                                                      ::user/expires-at     (Instant/ofEpochMilli 1311281975)
                                                      ::openid/id-token     {"sub" "factor-dev"
                                                                             "exp" 1311281970}
                                                      ::openid/access-token {"exp" 1311281975}
                                                      ::openid/response     {"access_token" "first_at"}}
                                                     nil)))))

(deftest redeem-refresh-token

  (testing "no refresh-token-fn configured"

    (let [user-state {::principal/type      ::openid/principal
                      ::principal/name      "factor-dev"
                      ::user/expires-at     (Instant/ofEpochMilli 1311281975)
                      ::openid/id-token     {"sub" "factor-dev"
                                             "exp" 1311281970}
                      ::openid/access-token {"exp" 1311281975}
                      ::openid/response     {"access_token" "first_at"}}
          principal  (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                    user-state
                                                    nil)]
      (is (= nil @(.redeemRefreshToken principal)))
      (is (= user-state (.getState principal)))))

  (testing "redeem-refresh-token returns a future"

    (let [user-state {::principal/type      ::openid/principal
                      ::principal/name      "factor-dev"
                      ::user/expires-at     (Instant/ofEpochMilli Long/MAX_VALUE)
                      ::openid/id-token     {"sub" "factor-dev"
                                             "exp" Long/MAX_VALUE}
                      ::openid/access-token {"exp" Long/MAX_VALUE}
                      ::openid/response     {"access_token" "first_at"}}
          principal  (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" Long/MAX_VALUE})
                                                    user-state
                                                    {:refresh-token-fn (fn [_id-token _response]
                                                                         {::openid/id-token     {"sub" "factor-dev"
                                                                                                 "exp" 1311299999}
                                                                          ::openid/access-token {"exp" 1311289999}
                                                                          ::openid/response     {"access_token" "second_at"}})
                                                     :user-state-fn    (fn [id-token access-token response]
                                                                         {::principal/name      "factor-dev"
                                                                          ::principal/type      ::openid/principal
                                                                          ::openid/id-token     id-token
                                                                          ::openid/access-token access-token
                                                                          ::openid/response     response})})]
      (is (future? (.redeemRefreshToken principal)))))

  (testing "refresh-token-fn returns nil"

    (let [user-state {::principal/type      ::openid/principal
                      ::principal/name      "factor-dev"
                      ::user/expires-at     (Instant/ofEpochMilli 1311281975)
                      ::openid/id-token     {"sub" "factor-dev"
                                             "exp" 1311281970}
                      ::openid/access-token {"exp" 1311281975}
                      ::openid/response     {"access_token" "first_at"}}
          principal  (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                    user-state
                                                    {:refresh-token-fn (fn [_id-token _response] nil)
                                                     :user-state-fn    (fn [id-token access-token response]
                                                                         {::principal/name      "factor-dev"
                                                                          ::principal/type      ::openid/principal
                                                                          ::openid/id-token     id-token
                                                                          ::openid/access-token access-token
                                                                          ::openid/response     response})})]
      (is (= nil @(.redeemRefreshToken principal)))
      (is (= user-state (.getState principal)))))

  (testing "full refresh of user"

    (let [principal (OpenIdUserPrincipalWithState.
                     (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                     {::principal/type      ::openid/principal
                      ::principal/name      "factor-dev"
                      ::user/expires-at     (Instant/ofEpochMilli 1311281975)
                      ::openid/id-token     {"sub" "factor-dev"
                                             "exp" 1311281970}
                      ::openid/access-token {"exp" 1311281975}
                      ::openid/response     {"access_token" "first_at"}}
                     {:refresh-token-fn (fn [_id-token _response]
                                          {::openid/id-token     {"sub" "factor-dev"
                                                                  "exp" 1311299999}
                                           ::openid/access-token {"exp" 1311289999}
                                           ::openid/response     {"access_token" "second_at"}})
                      :user-state-fn    (fn [id-token access-token response]
                                          {::principal/name      "factor-dev"
                                           ::principal/type      ::openid/principal
                                           ::openid/id-token     id-token
                                           ::openid/access-token access-token
                                           ::openid/response     response})})]

      ;; initial state returned by .getState on principal
      (is (= {::principal/name      "factor-dev"
              ::principal/type      ::openid/principal
              ::openid/access-token {"exp" 1311281975}
              ::openid/id-token     {"exp" 1311281970
                                     "sub" "factor-dev"}
              ::openid/response     {"access_token" "first_at"}}
             (dissoc (.getState principal) ::user/expires-at)))

      ;; new user state returned by redeemRefreshToken call
      (is (= {::principal/name      "factor-dev"
              ::principal/type      ::openid/principal
              ::openid/id-token     {"exp" 1311299999
                                     "sub" "factor-dev"}
              ::openid/access-token {"exp" 1311289999}
              ::openid/response     {"access_token" "second_at"}}
             @(.redeemRefreshToken principal)))

      ;; after refresh, new state is returned by .getState on principal
      (is (= {::principal/name      "factor-dev"
              ::principal/type      ::openid/principal
              ::openid/id-token     {"exp" 1311299999
                                     "sub" "factor-dev"}
              ::openid/access-token {"exp" 1311289999}
              ::openid/response     {"access_token" "second_at"}}
             (.getState principal)))))

  (testing "full refresh of user where refresh doesn't return id-token"

    (let [principal (OpenIdUserPrincipalWithState.
                     (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                     {::principal/type      ::openid/principal
                      ::principal/name      "factor-dev"
                      ::user/expires-at     (Instant/ofEpochMilli 1311281975)
                      ::openid/id-token     {"sub" "factor-dev"
                                             "exp" 1311281970}
                      ::openid/access-token {"exp" 1311281975}
                      ::openid/response     {"access_token" "first_at"}}
                     {:refresh-token-fn (fn [_id-token _response]
                                          {::openid/access-token {"exp" 1311289999}
                                           ::openid/response     {"access_token" "second_at"}})
                      :user-state-fn    (fn [id-token access-token response]
                                          {::principal/name      "factor-dev"
                                           ::principal/type      ::openid/principal
                                           ::openid/id-token     id-token
                                           ::openid/access-token access-token
                                           ::openid/response     response})})]

      ;; initial state returned by .getState on principal
      (is (= {::principal/name      "factor-dev"
              ::principal/type      ::openid/principal
              ::openid/access-token {"exp" 1311281975}
              ::openid/id-token     {"exp" 1311281970
                                     "sub" "factor-dev"}
              ::openid/response     {"access_token" "first_at"}}
             (dissoc (.getState principal) ::user/expires-at)))

      ;; new user state returned by redeemRefreshToken call
      (is (= {::principal/name      "factor-dev"
              ::principal/type      ::openid/principal
              ::openid/id-token     {"exp" 1311281970
                                     "sub" "factor-dev"}
              ::openid/access-token {"exp" 1311289999}
              ::openid/response     {"access_token" "second_at"}}
             @(.redeemRefreshToken principal)))

      ;; after refresh, new state is returned by .getState on principal
      (is (= {::principal/name      "factor-dev"
              ::principal/type      ::openid/principal
              ::openid/id-token     {"exp" 1311281970
                                     "sub" "factor-dev"}
              ::openid/access-token {"exp" 1311289999}
              ::openid/response     {"access_token" "second_at"}}
             (.getState principal))))))

