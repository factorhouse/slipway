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

(deftest in-role?

  ;; should't be possible, but in the case a user has no roles, this is safe
  ;; (a user should always at least have an empty set of roles)
  (is (not (user/in-role? nil "role-1")))


  (is (not (user/in-role? {::user/roles #{"role-2"}} "role-1")))

  (is (user/in-role? {::user/roles #{"role-2"}} "role-2"))

  (is (user/in-role? {::user/roles #{"role-1" "role-2"}} "role-2")))