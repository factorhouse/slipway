(ns slipway.security.openid-test
  (:require [clojure.core.protocols :as p]
            [clojure.test :refer [deftest is]]
            [slipway.security.openid])
  (:import (org.eclipse.jetty.security.openid OpenIdCredentials)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(deftest credentials

  (is (= {:id-token {"sub" "factor-dev"
                     "a"   "b"}
          :response nil}
         (p/datafy (OpenIdCredentials. {"sub" "factor-dev" "a" "b"})))))

(deftest principal

  (is (= {:type  :slipway.security.openid/principal
          :roles ["one" "two" "three"]}
         (p/datafy (OpenIdUserPrincipalWithState. (OpenIdCredentials. {"sub" "factor-dev" "exp" 1311281970})
                                                  {:roles ["one" "two" "three"]})))))