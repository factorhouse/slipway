(ns slipway.security.oidc.bearer-token-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.security.oidc.bearer-token :as bearer-token]))

(deftest is-bearer-auth-type?

  ;; As described in Section 2.3 of [RFC5234], the string Bearer is case-insensitive
  ;; See: https://auth0.com/blog/the-bearer-token-case/ for more
  (is (bearer-token/is-bearer-auth-type? "bearer "))
  (is (bearer-token/is-bearer-auth-type? "Bearer "))
  (is (bearer-token/is-bearer-auth-type? "BEARER "))
  (is (bearer-token/is-bearer-auth-type? "BEARER some-token"))

  (is (not (bearer-token/is-bearer-auth-type? nil)))
  (is (not (bearer-token/is-bearer-auth-type? "")))

  ;; must be followed by at least one space character (then presumably a token)
  (is (not (bearer-token/is-bearer-auth-type? "bearer")))
  (is (not (bearer-token/is-bearer-auth-type? "Bearer")))
  (is (not (bearer-token/is-bearer-auth-type? "BEARER")))
  (is (not (bearer-token/is-bearer-auth-type? "earer")))

  ;; any other prefix
  (is (not (bearer-token/is-bearer-auth-type? "earer")))
  (is (not (bearer-token/is-bearer-auth-type? "X-Bearer-Type")))
  (is (not (bearer-token/is-bearer-auth-type? "BASIC"))))
