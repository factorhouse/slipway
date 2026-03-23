(ns slipway.handler.compression
  (:refer-clojure :exclude [format])
  (:require [clojure.tools.logging :as log])
  (:import (org.eclipse.jetty.compression.gzip GzipCompression)
           (org.eclipse.jetty.compression.server CompressionConfig CompressionHandler)))

(comment
  #:slipway.handler.compression{:enabled?           "is compression handler enabled? default true"
                                :path-spec          "the compression path-spec, default '/*'"
                                :format             "compression format, defaults to :gzip"
                                :compress-min-bytes "min response size to trigger compression (default 1024 bytes)"
                                :compression-config "a concrete Jetty CompressConfig instance (nil for default configuration)"})

(defmulti format ::format)

(defmethod format :default
  [_opts]
  (GzipCompression.))

(defn compression
  [{::keys [compress-min-bytes] :or {compress-min-bytes 1024} :as opts}]
  (log/debugf "enabling %s compression with compress-min-bytes %s" (or (:format opts) :gzip) compress-min-bytes)
  (doto (format opts)
    (.setMinCompressSize compress-min-bytes)))

(defn config ^CompressionConfig
  [{::keys [compression-config]}]
  (log/debugf "using %s compression configuration" (if compression-config "specific" "default"))
  (or compression-config
      (let [builder (CompressionConfig/builder)]
        (.build (.defaults builder)))))

(defn handler
  [{::keys [enabled? path-spec] :or {path-spec "/*"} :as opts}]
  (when (not (false? enabled?))
    (let [handler     (CompressionHandler.)
          compression (compression opts)]
      (.putCompression handler compression)
      (.putConfiguration handler ^String path-spec (config opts))
      handler)))