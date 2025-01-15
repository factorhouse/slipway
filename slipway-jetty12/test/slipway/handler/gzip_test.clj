(ns slipway.handler.gzip-test
  (:require [clojure.test :refer :all]
            [slipway.handler.gzip :as gzip])
  (:import (org.eclipse.jetty.server.handler.gzip GzipHandler)))

(deftest construction

  (is (some? (gzip/handler nil)))
  (is (some? (gzip/handler {})))
  (is (some? (gzip/handler {::gzip/enabled? true})))
  (is (nil? (gzip/handler {::gzip/enabled? false}))))

(deftest gzipable?

  (testing "include/exclude defaults"

    (let [^GzipHandler handler (gzip/handler {})]

      ;; jetty defaults to broadly accepting anything not explicitly excluded where no includes are set
      ;; jetty also explicitly excludes known ungzipable types, it's generally safe to just use the default handler
      (is (.isMimeTypeDeflatable handler "text/css"))
      (is (.isMimeTypeDeflatable handler "text/html"))
      (is (.isMimeTypeDeflatable handler "text/csv"))
      (is (.isMimeTypeDeflatable handler "text/plain"))
      (is (.isMimeTypeDeflatable handler "text/javascript"))
      (is (.isMimeTypeDeflatable handler "application/javascript"))
      (is (.isMimeTypeDeflatable handler "image/svg+xml"))))

  (testing "explicit excludes"

    (let [handler (gzip/handler {::gzip/excluded-mime-types ["text/css" "text/html"]})]

      (is (not (.isMimeTypeDeflatable handler "text/css")))
      (is (not (.isMimeTypeDeflatable handler "text/html")))
      (is (.isMimeTypeDeflatable handler "text/csv"))
      (is (.isMimeTypeDeflatable handler "text/plain"))
      (is (.isMimeTypeDeflatable handler "text/javascript"))
      (is (.isMimeTypeDeflatable handler "application/javascript"))
      (is (.isMimeTypeDeflatable handler "image/svg+xml"))))

  (testing "explicit includes"

    (let [handler (gzip/handler {::gzip/included-mime-types ["text/css" "text/html"]})]

      ;; explicit includes blows away defaults, so only do it if you know what you're doing or want to be exhaustively explicit
      (is (.isMimeTypeDeflatable handler "text/css"))
      (is (.isMimeTypeDeflatable handler "text/html"))
      (is (not (.isMimeTypeDeflatable handler "text/csv")))
      (is (not (.isMimeTypeDeflatable handler "text/plain")))
      (is (not (.isMimeTypeDeflatable handler "text/javascript")))
      (is (not (.isMimeTypeDeflatable handler "application/javascript")))
      (is (not (.isMimeTypeDeflatable handler "image/svg+xml")))))

  (testing "include/exclude preference"

    (let [handler (gzip/handler {::gzip/included-mime-types ["text/css" "text/html"]
                                 ::gzip/excluded-mime-types ["text/css"]})]

      ;; exclude trumps include
      (is (not (.isMimeTypeDeflatable handler "text/css")))
      (is (.isMimeTypeDeflatable handler "text/html"))
      (is (not (.isMimeTypeDeflatable handler "text/csv")))
      (is (not (.isMimeTypeDeflatable handler "text/plain")))
      (is (not (.isMimeTypeDeflatable handler "text/javascript")))
      (is (not (.isMimeTypeDeflatable handler "application/javascript")))
      (is (not (.isMimeTypeDeflatable handler "image/svg+xml"))))))
