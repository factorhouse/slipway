(ns slipway.security.oidc.jwt
  (:import (org.eclipse.jetty.security.openid JwtDecoder)))

(defn decode
  [token-str]
  (JwtDecoder/decode token-str))

(comment
  #:slipway.security.oidc.jwt{:user-roles-source      "the token containing user roles, either 'access_token' or 'id_token' (default is 'access_token')"
                              :user-roles-path        "the path within the source token to find user roles, default is ['roles']"
                              :user-id-source         "the token containing user id, either 'access_token' or 'id_token' (default is 'id_token')"
                              :user-id-path           "the path within the source token to find user name, default is ['sub']"
                              :user-expiration-source "the token used for session expiration, either 'access_token' or 'id_token' (default is 'access_token')"})