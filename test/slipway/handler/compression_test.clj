(ns slipway.handler.compression-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.handler.compression :as compression])
  (:import (org.eclipse.jetty.compression.gzip GzipCompression)
           (org.eclipse.jetty.compression.server CompressionConfig CompressionHandler)))

(deftest construction

  (is (some? (compression/handler nil)))
  (is (some? (compression/handler {})))
  (is (some? (compression/handler {::compression/enabled? true})))
  (is (nil? (compression/handler {::compression/enabled? false}))))

(deftest compression

  (testing "defaults"

    (is (= GzipCompression (type (compression/compression {}))))
    (is (= 1024 (.getMinCompressSize (compression/compression {}))))
    (is (= 2048 (.getMinCompressSize (compression/compression {::compression/compress-min-bytes 2048}))))))

(deftest config

  (testing "default mime-types compression/decompression"

    ;; jetty defaults to broadly accepting anything not explicitly excluded where no includes are set
    ;; jetty also explicitly excludes known uncompressable types, it's generally safe to just use the default handler

    (let [^CompressionConfig config (compression/config {})]

      ;; compression
      (is (.isCompressMimeTypeSupported config "text/css"))
      (is (.isCompressMimeTypeSupported config "text/html"))
      (is (.isCompressMimeTypeSupported config "text/csv"))
      (is (.isCompressMimeTypeSupported config "text/plain"))
      (is (.isCompressMimeTypeSupported config "text/javascript"))
      (is (.isCompressMimeTypeSupported config "application/javascript"))
      (is (not (.isCompressMimeTypeSupported config "image/svg+xml")))
      (is (not (.isCompressMimeTypeSupported config "application/gzip")))

      ;; decompression
      (is (.isDecompressMimeTypeSupported config "text/css"))
      (is (.isDecompressMimeTypeSupported config "text/html"))
      (is (.isDecompressMimeTypeSupported config "text/csv"))
      (is (.isDecompressMimeTypeSupported config "text/plain"))
      (is (.isDecompressMimeTypeSupported config "text/javascript"))
      (is (.isDecompressMimeTypeSupported config "application/javascript"))
      (is (not (.isDecompressMimeTypeSupported config "image/svg+xml")))
      (is (not (.isDecompressMimeTypeSupported config "application/gzip"))))))

(deftest handler

  (testing "include/exclude defaults at the default path-spec"

    (let [^CompressionHandler handler (compression/handler {})
          ^CompressionConfig config   (.getConfiguration handler "/*")]

      ;; jetty defaults to broadly accepting anything not explicitly excluded where no includes are set
      ;; jetty also explicitly excludes known ungzipable types, it's generally safe to just use the default handler
      (is (.isDecompressMimeTypeSupported config "text/css"))
      (is (.isDecompressMimeTypeSupported config "text/html"))
      (is (.isDecompressMimeTypeSupported config "text/csv"))
      (is (.isDecompressMimeTypeSupported config "text/plain"))
      (is (.isDecompressMimeTypeSupported config "text/javascript"))
      (is (.isDecompressMimeTypeSupported config "application/javascript"))
      (is (not (.isDecompressMimeTypeSupported config "image/svg+xml")))
      (is (not (.isDecompressMimeTypeSupported config "application/gzip"))))))