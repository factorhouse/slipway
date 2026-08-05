(ns slipway.security.openid.jwk.source
  (:require [clojure.tools.logging :as log])
  (:import (com.nimbusds.jose.jwk.source JWKSource)
           (java.io Closeable)))

(defn stop
  [^JWKSource source]
  ;; Some source are not closeable
  (when (instance? Closeable source)
    (log/debug "stopping source")
    (.close ^Closeable source)))