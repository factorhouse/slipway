(ns slipway.connector.http-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.connector.http :as http])
  (:import (org.eclipse.jetty.http HttpCompliance)
           (org.eclipse.jetty.server HttpConfiguration)))

(deftest http-compliance-mode

  (testing "default mode as documented"

    (is (= HttpCompliance/RFC9110 (.getHttpCompliance (HttpConfiguration.)))))

  (testing "no config, or misconfigured"

    (is (= nil (http/http-compliance-mode nil)))
    (is (= nil (http/http-compliance-mode {})))
    (is (= nil (http/http-compliance-mode "")))
    (is (= nil (http/http-compliance-mode "INCORRECT_INPUT"))))

  (testing "correctly configured, case insensitive"

    (is (= HttpCompliance/STRICT (http/http-compliance-mode "STRICT")))
    (is (= HttpCompliance/STRICT (http/http-compliance-mode "strict")))

    (is (= HttpCompliance/RFC9110 (http/http-compliance-mode "RFC9110")))
    (is (= HttpCompliance/RFC9110 (http/http-compliance-mode "rfc9110")))

    (is (= HttpCompliance/RFC7230 (http/http-compliance-mode "RFC7230")))
    (is (= HttpCompliance/RFC7230 (http/http-compliance-mode "rfc7230")))

    (is (= HttpCompliance/RFC7230_LEGACY (http/http-compliance-mode "RFC7230_LEGACY")))
    (is (= HttpCompliance/RFC7230_LEGACY (http/http-compliance-mode "rfc7230_LEGACY")))

    (is (= HttpCompliance/RFC2616 (http/http-compliance-mode "RFC2616")))
    (is (= HttpCompliance/RFC2616 (http/http-compliance-mode "rfc2616")))

    (is (= HttpCompliance/RFC2616_LEGACY (http/http-compliance-mode "RFC2616_LEGACY")))
    (is (= HttpCompliance/RFC2616_LEGACY (http/http-compliance-mode "rfc2616_LEGACY")))

    (is (= HttpCompliance/LEGACY (http/http-compliance-mode "LEGACY")))
    (is (= HttpCompliance/LEGACY (http/http-compliance-mode "legacy")))))


