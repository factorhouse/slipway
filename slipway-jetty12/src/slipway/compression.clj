(ns slipway.compression
  (:refer-clojure :exclude [format])
  (:require [clojure.tools.logging :as log])
  (:import (org.eclipse.jetty.compression.server CompressionConfig)))

(defmulti format ::format)

(defmethod format :default [_] nil)

(defn config ^CompressionConfig
  [{:slipway.compression.config/keys [] :as opts}]
  (let [builder (CompressionConfig/builder)]
    (if (seq opts)
      (do
        (log/info "building config" opts)
        (.build builder))
      (do
        (log/info "default config")
        (.build (.defaults builder))))))