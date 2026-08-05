(ns slipway.request-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.principal :as-alias principal]
            [slipway.request :as request]
            [slipway.security.openid :as-alias openid]
            [slipway.user :as user])
  (:import (java.time Instant)))

(deftest request-user-state

  (let [request-user {::user/identity {::principal/type  ::openid/principal
                                       ::principal/name  "my-user-id"
                                       ::user/roles      ["my-role-1" "my-role-2"]
                                       ::user/expires-at (Instant/ofEpochSecond 1311281970)}}]

    (is (= ::openid/principal (request/user-type request-user)))
    (is (= "my-user-id" (request/user-name request-user)))
    (is (= ["my-role-1" "my-role-2"] (request/user-roles request-user)))
    (is (request/user-expired? request-user))))

(deftest request-user-expired?

  ;; expired? requires :expires-at to be set on the user identity, otherwise is not expired
  (is (not (request/user-expired? {})))

  (is (request/user-expired? {::user/identity {::user/expires-at (Instant/ofEpochSecond 1311281970)}}))

  ;; expiry happens when expires-at is earlier than 'now'
  (is (not (request/user-expired? {::user/identity {:expires-at (Instant/ofEpochSecond 1311281970)}}
                                  (Instant/ofEpochSecond 1311281970))))

  ;; expiry happens when expires-at is earlier than 'now'
  (is (request/user-expired? {::user/identity {::user/expires-at (Instant/ofEpochSecond (- 1311281970 1))}}
                             (Instant/ofEpochSecond 1311281970))))