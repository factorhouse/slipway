(ns slipway.security.openid.jwt.verification-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.security.openid.jwt.verification :as verification])
  (:import (com.nimbusds.jose.proc DefaultJOSEObjectTypeVerifier)))

(deftest type-verifier

  ;; default allowable 'typ' fields
  (is (= #{"at+jwt" "application/at+jwt"}
         (->> ^DefaultJOSEObjectTypeVerifier (verification/type-verifier {})
              (.getAllowedTypes)
              (map #(.getType %1))
              set)))

  ;; common override to support "JWT"
  (is (= #{"JWT"}
         (->> ^DefaultJOSEObjectTypeVerifier (verification/type-verifier {::verification/exact-typ ["JWT"]})
              (.getAllowedTypes)
              (map #(.getType %1))
              set))))
