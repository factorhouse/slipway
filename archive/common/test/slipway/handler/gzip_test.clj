(ns slipway.handler.gzip-test
  (:require [clojure.test :refer :all]
            [slipway.handler.gzip :as gzip]))

(deftest construction

  (is (some? (gzip/handler nil)))
  (is (some? (gzip/handler {})))
  (is (some? (gzip/handler {::gzip/enabled? true})))
  (is (nil? (gzip/handler {::gzip/enabled? false}))))

(deftest gzipable?

  (testing "include/exclude defaults"

    (let [handler (gzip/handler {})]

      ;; jetty defaults to broadly accepting anything not explicitly excluded where no includes are set
      ;; jetty also explicitly excludes known ungzipable types, it's generally safe to just use the default handler
      (is (.isMimeTypeGzipable handler "text/css"))
      (is (.isMimeTypeGzipable handler "text/html"))
      (is (.isMimeTypeGzipable handler "text/csv"))
      (is (.isMimeTypeGzipable handler "text/plain"))
      (is (.isMimeTypeGzipable handler "text/javascript"))
      (is (.isMimeTypeGzipable handler "application/javascript"))
      (is (.isMimeTypeGzipable handler "image/svg+xml"))))

  (testing "explicit excludes"

    (let [handler (gzip/handler {::gzip/excluded-mime-types ["text/css" "text/html"]})]

      (is (not (.isMimeTypeGzipable handler "text/css")))
      (is (not (.isMimeTypeGzipable handler "text/html")))
      (is (.isMimeTypeGzipable handler "text/csv"))
      (is (.isMimeTypeGzipable handler "text/plain"))
      (is (.isMimeTypeGzipable handler "text/javascript"))
      (is (.isMimeTypeGzipable handler "application/javascript"))
      (is (.isMimeTypeGzipable handler "image/svg+xml"))))

  (testing "explicit includes"

    (let [handler (gzip/handler {::gzip/included-mime-types ["text/css" "text/html"]})]

      ;; explicit includes blows away defaults, so only do it if you know what you're doing or want to be exhaustively explicit
      (is (.isMimeTypeGzipable handler "text/css"))
      (is (.isMimeTypeGzipable handler "text/html"))
      (is (not (.isMimeTypeGzipable handler "text/csv")))
      (is (not (.isMimeTypeGzipable handler "text/plain")))
      (is (not (.isMimeTypeGzipable handler "text/javascript")))
      (is (not (.isMimeTypeGzipable handler "application/javascript")))
      (is (not (.isMimeTypeGzipable handler "image/svg+xml")))))

  (testing "include/exclude preference"

    (let [handler (gzip/handler {::gzip/included-mime-types ["text/css" "text/html"]
                                 ::gzip/excluded-mime-types ["text/css"]})]

      ;; exclude trumps include
      (is (not (.isMimeTypeGzipable handler "text/css")))
      (is (.isMimeTypeGzipable handler "text/html"))
      (is (not (.isMimeTypeGzipable handler "text/csv")))
      (is (not (.isMimeTypeGzipable handler "text/plain")))
      (is (not (.isMimeTypeGzipable handler "text/javascript")))
      (is (not (.isMimeTypeGzipable handler "application/javascript")))
      (is (not (.isMimeTypeGzipable handler "image/svg+xml"))))))
