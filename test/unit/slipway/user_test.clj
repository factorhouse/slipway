(ns slipway.user-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.principal :as-alias principal]
            [slipway.security.openid :as-alias openid]
            [slipway.user :as user])
  (:import (java.time Instant)))

(deftest user-state

  (let [user {::principal/type      ::openid/principal
              ::principal/name      "my-user-id"
              ::user/roles          ["my-role-1" "my-role-2"]
              ::user/expires-at     (Instant/ofEpochSecond 1311281970)}]

    (is (= ::openid/principal (user/type user)))
    (is (= "my-user-id" (user/name user)))
    (is (= ["my-role-1" "my-role-2"] (user/roles user)))
    (is (user/expired? user))))

(deftest expired?

  ;; expired? requires :expires-at to be set on the user identity, otherwise is not expired
  (is (not (user/expired? {})))

  (is (user/expired? {::user/expires-at (Instant/ofEpochSecond 1311281970)}))

  ;; expiry happens when expires-at is earlier than 'now'
  (is (not (user/expired? {:expires-at (Instant/ofEpochSecond 1311281970)}
                          (Instant/ofEpochSecond 1311281970))))

  ;; expiry happens when expires-at is earlier than 'now'
  (is (user/expired? {::user/expires-at (Instant/ofEpochSecond (- 1311281970 1))}
                     (Instant/ofEpochSecond 1311281970))))