(ns slipway.request-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.principal :as-alias principal]
            [slipway.request :as request]
            [slipway.security.oidc :as-alias oidc]
            [slipway.user :as user])
  (:import (java.time Instant)))

(deftest request-user-state

  (let [request-user {::user/identity {::principal/type  ::oidc/principal
                                       ::principal/name  "my-user-id"
                                       ::user/roles      ["my-role-1" "my-role-2"]
                                       ::user/expires-at (Instant/ofEpochSecond 1311281970)}}]

    (is (= ::oidc/principal (request/user-type request-user)))
    (is (= "my-user-id" (request/user-name request-user)))
    (is (= ["my-role-1" "my-role-2"] (request/user-roles request-user)))
    (is (request/user-expired? request-user))))

(deftest request-user-expires-in

  ;; expires-in requires :expires-at to be set on the user identity, otherwise is not expired
  (is (nil? (request/user-expires-in {})))

  ;; expiry happens when expires-at is earlier than or equal to 'now'
  (is (= 0 (request/user-expires-in {::user/identity {::user/expires-at (Instant/ofEpochSecond 1311281970)}}
                                    (Instant/ofEpochSecond 1311281970))))

  (is (= -1 (request/user-expires-in {::user/identity {::user/expires-at (Instant/ofEpochSecond (- 1311281970 1))}}
                                     (Instant/ofEpochSecond 1311281970))))

  (is (= -100 (request/user-expires-in {::user/identity {::user/expires-at (Instant/ofEpochSecond (- 1311281970 100))}}
                                       (Instant/ofEpochSecond 1311281970))))

  (is (= 1 (request/user-expires-in {::user/identity {::user/expires-at (Instant/ofEpochSecond (+ 1311281970 1))}}
                                    (Instant/ofEpochSecond 1311281970))))

  (is (= 100 (request/user-expires-in {::user/identity {::user/expires-at (Instant/ofEpochSecond (+ 1311281970 100))}}
                                      (Instant/ofEpochSecond 1311281970)))))

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