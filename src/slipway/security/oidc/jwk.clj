(ns slipway.security.oidc.jwk
  (:import (com.nimbusds.jose.jwk.source JWKSource)))

(comment
  #:slipway.security.oidc.jwk{::source "configurable JWT key source, leave empty for default (JWKS)"})

(defmulti ^JWKSource source ::source)