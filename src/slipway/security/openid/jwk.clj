(ns slipway.security.openid.jwk
  (:import (com.nimbusds.jose.jwk.source JWKSource)))

(comment
  #:slipway.security.openid.jwk{::source "configurable JWT key source, leave empty for default/JWKS"})

(defmulti ^JWKSource source ::source)