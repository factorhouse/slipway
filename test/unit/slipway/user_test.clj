(ns slipway.user-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.user :as user])
  (:import (java.time Instant)))

(deftest expired?

  ;; expired? requires :expires-at to be set on the user identity, otherwise is not expired
  (is (not (user/expired? {})))

  (is (user/expired? {::user/identity {:expires-at (Instant/ofEpochSecond 1311281970)}}))

  ;; expiry happens when expires-at is earlier than 'now'
  (is (not (user/expired? {::user/identity {:expires-at (Instant/ofEpochSecond 1311281970)}}
                          (Instant/ofEpochSecond 1311281970))))

  ;; expiry happens when expires-at is earlier than 'now'
  (is (user/expired? {::user/identity {:expires-at (Instant/ofEpochSecond (- 1311281970 1))}}
                     (Instant/ofEpochSecond 1311281970))))