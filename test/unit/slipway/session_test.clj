(ns slipway.session-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.session :as session])
  (:import (org.eclipse.jetty.http HttpCookie$SameSite)))

(deftest cookie-same-site

  (is (= HttpCookie$SameSite/STRICT (session/cookie-same-site nil)))
  (is (= HttpCookie$SameSite/NONE (session/cookie-same-site :none)))
  (is (= HttpCookie$SameSite/LAX (session/cookie-same-site :lax)))
  (is (= HttpCookie$SameSite/STRICT (session/cookie-same-site :strict)))
  (is (= HttpCookie$SameSite/STRICT (session/cookie-same-site :bad-input))))