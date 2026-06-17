(ns slipway.security.hash-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.security.hash :as hash])
  (:import (clojure.lang ExceptionInfo)
           (org.eclipse.jetty.security HashLoginService)))

(deftest login-service

  (is (thrown? ExceptionInfo (hash/login-service nil)))

  (is (thrown? ExceptionInfo (hash/login-service {})))

  (is (= HashLoginService
         (type (hash/login-service {::hash/realm "test-realm"
                                    ::hash/users [["user-1" "password-1" ["role1" "role2"]]]})))))