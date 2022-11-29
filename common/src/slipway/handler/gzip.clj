(ns slipway.handler.gzip
  (:require [clojure.tools.logging :as log])
  (:import (org.eclipse.jetty.server.handler.gzip GzipHandler)))

(comment
  #:slipway.handler.gzip {:enabled?            "is gzip enabled? default true"
                          :included-mime-types "mime types to include (without charset or other parameters), leave nil for default types"
                          :excluded-mime-types "mime types to exclude (replacing any previous exclusion set)"
                          :min-gzip-size       "min response size to trigger dynamic compression (in bytes, default 1024)"})

(defn handler
  [{::keys [enabled? included-mime-types excluded-mime-types min-gzip-size]
    :or    {min-gzip-size       1024}}]
  (when (not (false? enabled?))
    (let [gzip-handler (GzipHandler.)]
      (log/info "enabling compression")
      (when (seq included-mime-types)
        (log/infof "setting included mime types: %s" included-mime-types)
        (.setIncludedMimeTypes gzip-handler (into-array String included-mime-types)))
      (when (seq excluded-mime-types)
        (log/infof "setting excluded mime types: %s" excluded-mime-types)
        (.setExcludedMimeTypes gzip-handler (into-array String excluded-mime-types)))
      (when min-gzip-size
        (log/infof "setting min gzip size: %s" min-gzip-size)
        (.setMinGzipSize gzip-handler min-gzip-size))
      gzip-handler)))
