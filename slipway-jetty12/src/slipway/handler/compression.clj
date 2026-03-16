(ns slipway.handler.compression
  (:require [slipway.compression :as compression])
  (:import (org.eclipse.jetty.compression.server CompressionConfig CompressionHandler)))

(comment
  #:slipway.handler.compression{:type "the compression format, e.g. :gzip, :brotli, :zstd"})

(defn handler
  [{::keys [] :as opts}]
  (when-let [format (compression/format opts)]
    (let [config (compression/config opts)]
      )))
