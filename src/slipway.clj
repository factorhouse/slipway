(ns slipway
  (:require [clojure.tools.logging :as log]
            [slipway.connector.http]
            [slipway.connector.https]
            [slipway.context]
            [slipway.server :as server]
            [slipway.user])
  (:import (org.eclipse.jetty.server Server)))

(comment
  #:slipway.compression{:enabled?           "is a compression handler enabled? default true"
                        :path-spec          "the compression path-spec, default '/*'"
                        :format             "compression format, defaults to :gzip"
                        :compress-min-bytes "min response size to trigger compression (default 1024 bytes)"
                        :compression-config "a concrete Jetty CompressionConfig instance (nil for default configuration)"}

  #:slipway.connector.https{:name                       "the name of this connector (useful for VirtualHosts configuration)"
                            :host                       "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces"
                            :port                       "port this connector listens on. If set to 0 a random port is assigned which may be obtained with getLocalPort(). default 443"
                            :idle-timeout-ms            "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 30000 ms"
                            :http-forwarded?            "if true, add the ForwardRequestCustomizer. See Jetty Forward HTTP docs"
                            :proxy-protocol?            "if true, add the ProxyConnectionFactory. See Jetty Proxy Protocol docs"
                            :http-config                "a concrete HttpConfiguration object to replace the default config entirely"
                            :configurator               "a fn taking the final connector as argument, allowing further configuration"
                            :keystore                   "keystore to use, either path (String) or concrete KeyStore"
                            :keystore-type              "type of keystore, e.g. JKS"
                            :keystore-password          "password of the keystore"
                            :key-manager-password       "password for the specific key within the keystore"
                            :truststore                 "truststore to use, either path (String) or concrete KeyStore"
                            :truststore-password        "password of the truststore"
                            :truststore-type            "type of the truststore, eg. JKS"
                            :include-protocols          "a list of protocol name patterns to include in SSLEngine"
                            :exclude-protocols          "a list of protocol name patterns to exclude from SSLEngine"
                            :replace-exclude-protocols? "if true will replace existing exclude-protocols, otherwise will add them"
                            :exclude-ciphers            "a list of cipher suite names to exclude from SSLEngine"
                            :replace-exclude-ciphers?   "if true will replace existing exclude-ciphers, otherwise will add them"
                            :security-provider          "the security provider name"
                            :client-auth                "either :need or :want to set the corresponding need/wantClientAuth field"
                            :ssl-context                "a concrete pre-configured SslContext"
                            :sni-required?              "if true SNI is required, else requests will be rejected with 400 response, default false"
                            :sni-host-check?            "if true the SNI Host name must match when there is an SNI certificate, default false"
                            :sts-max-age-s              "set the Strict-Transport-Security max age in seconds, default -1"
                            :sts-include-subdomains?    "true if a include subdomain property is sent with any Strict-Transport-Security header"
                            :send-server-version?       "if true, send the Server header in responses"
                            :send-date-header?          "if true, send the Date header in responses"
                            :relative-redirect-allowed? "if true, allow relative redirects, default false"
                            :http-compliance            "set the HttpCompliance mode, defaults to HttpCompliance/RFC9110"}

  #:slipway.connector.http{:name                       "the name of this connector (useful for VirtualHosts configuration)"
                           :host                       "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces"
                           :port                       "port this connector listens on. If set to 0 a random port is assigned which may be obtained with getLocalPort(), default 80"
                           :idle-timeout-ms            "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 30000 ms"
                           :http-forwarded?            "if true, add the ForwardRequestCustomizer. See Jetty Forward HTTP docs"
                           :proxy-protocol?            "if true, add the ProxyConnectionFactory. See Jetty Proxy Protocol docs"
                           :http-config                "a concrete HttpConfiguration object to replace the default config entirely"
                           :configurator               "a fn taking the final connector as argument, allowing further configuration"
                           :send-server-version?       "if true, send the Server header in responses"
                           :send-date-header?          "if true, send the Date header in responses"
                           :relative-redirect-allowed? "if true, allow relative redirects, default false"
                           :http-compliance            "set the HttpCompliance mode, defaults to HttpCompliance/RFC9110"}

  #:slipway.context{:path            "the context path, default '/'"
                    :ring-handler    "the ring-handler descendant of this context-handler"
                    :null-path-info? "true if /path is not redirected to /path/, default true"
                    :virtual-hosts   "a list of ^String virtual hosts for the context"
                    :error-handler   "the error-handler used by this context-handler for context level errors"
                    :handlers        "a sequence of [:slipway.context], when used with ::server/handler of ::context/handler-collection"}

  #:slipway.security{:handler "identifies a SecurityHandler impl, :jaas', :hash, and :openid supported by default"}

  #:slipway.security.hash{:realm                 "optional Jetty authentication realm"
                          :user-file             "the path to a Jetty hash-user file"
                          :hot-reload-interval-s "the period in seconds to scan :user-file for changes"
                          :users                 "a sequence of [^String user-name, ^String credential, ^String[] [roles]]"
                          :authenticator         "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
                          :constraint-mappings   "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"
                          :identity-service      "an (optional) concrete Jetty IdentityService"}

  #:slipway.security.jaas{:realm               "the Jetty authentication realm"
                          :authenticator       "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
                          :constraint-mappings "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"
                          :identity-service    "an (optional) concrete Jetty IdentityService"}

  #:slipway.security.openid{:authorization-flow               ":authorization-code or :client-credentials (default :authorization-code)"
                            :issuer                           "the URL of the OpenID provider"
                            :client-id                        "OAuth 2.0 Client Identifier valid at the OpenID provider"
                            :client-secret                    "the client secret known only by the Client and the OpenID"
                            :jwks-endpoint                    "the URL of the OpenID provider's public cryptographic keys for verifying JWT token signatures"
                            :authorization-endpoint           "the URL of the OpenID provider's authorization endpoint if configured"
                            :token-endpoint                   "the URL of the OpenID provider's token endpoint if configured"
                            :end-session-endpoint             "the URL of the OpenID provider's end session endpoint if configured"
                            :authentication-method            "authentication method to use with the Token Endpoint"
                            :http-client                      "the (optional) HttpClient instance to use"
                            :scopes                           "a sequence of ^String scopes to request, included in addition to \"openid\" scope which is always requested, default is [\"profile\" \"email\"]"
                            :logout-when-id-token-is-expired? "whether to logout when the ID token is expired, default false"
                            :oidc-redirect-success            "the path where the OIDC provider redirects back to Jetty"
                            :oidc-redirect-error              "optional page where authentication errors are redirected"
                            :oidc-redirect-logout             "optional page where the user is redirected to this page after logout"
                            :identity-service                 "a concrete Jetty IdentityService"
                            :constraint-mappings              "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"}

  #:slipway.security.openid.jwk{::source "configurable JWT key source, leave empty for default (JWKS)"}

  #:slipway.security.openid.jwks{:endpoint                  "the jwks endpoint url"
                                 :cache?                    "enable caching of the jwks set"
                                 :cache-ttl                 "the time to live of the cached JWK set, in milliseconds"
                                 :cache-refresh-timeout     "the cache refresh timeout, in milliseconds."
                                 :cache-forever?            "enable caching of the jwks set without expiration"
                                 :refresh-ahead-cache?      "enable refresh-ahead caching of the JWK set"
                                 :refresh-ahead-time        "the refresh ahead time, in milliseconds"
                                 :scheduled?                "refresh in a scheduled manner, regardless of requests"
                                 :rate-limited?             "rate limit the JWK set retrieval"
                                 :rate-limited-min-interval "the minimum allowed time interval between two JWK set retrievals"
                                 :retrying?                 "enables single retrial to retrieve the JWK set to work around transient network issues"
                                 :outage-tolerant?          "enable outage tolerance by serving a cached JWK set in case of outage"
                                 :outage-tolerant-forever?  "enable outage tolerance without expiration"
                                 :outage-tolerant-ttl       "the time to live of the cached JWK set to cover outages, in milliseconds"}

  #:slipway.security.openid.jwk.rsa{:key "the RSA key to use as an immutable JWK source"}

  #:slipway.security.openid.jws{:algorithm  "JSON Web Signature (JWS) algorithm name, represents the alg header parameter in JWS objects. Default is RS256"
                                :algorithms "a sequence of :algorithm if accepting multiple JWS algorithms"}

  #:slipway.security.openid.jwt{:user-roles-source "the token containing user roles, either :access-token or :id-token (default is :access-token)"
                                :user-roles-path   "the path within the :roles token to find user roles, default is [\"roles\"]"
                                :user-id-source    "the token containing user id, either :access-token or :id-token (default is :id-token)"
                                :user-id-path      "the path within the :id-token token to find user name, default is [\"sub\"]"}

  #:slipway.security.openid.jwt.verification{::exact-typ       "a sequence of acceptable 'typ' fields, default is ['at+jwt' 'application/at+jwt']"
                                             ::exact-iss       "required: the URL of the OpenID provider"
                                             ::exact-aud       "required: the audience of this service to match the 'aud' field in the jwt"
                                             ::required-claims "set of required JWTClaimNames. Default #{JWTClaimNames/JWT_ID JWTClaimNames/SUBJECT JWTClaimNames/ISSUED_AT JWTClaimNames/EXPIRATION_TIME}"}

  #:slipway.session{:enabled?                "are sessions enabled for this server? Default true"
                    :secure-request-only?    "set the secure flag on session cookies"
                    :http-only?              "set the http-only flag on session cookies"
                    :same-site               "set session cookie same-site policy to :none, :lax, or :strict"
                    :max-inactive-interval-s "max session idle time (in s)"
                    :cookie-name             "the name of the session cookie"
                    :session-id-manager      "the meta manager used for cross context session management"
                    :refresh-cookie-age-s    "max time before a session cookie is re-set (in s)"
                    :using-cookies           "true if cookies are used to track sessions (default true)"
                    :using-uri-parameters    "true if uri parameters are used to track sessions (default false)"
                    :path-parameter-name     "name of path parameter used for URL session tracking"}

  #:slipway.sente{:options "A map of options passed directly to sente/make-channel-socket-server!"}

  #:slipway.websockets{:enabled?                 "are websockets enabled? default false"
                       :path-spec                "the websocket path-spec, default '/chsk'"
                       :idle-timeout-ms          "max websocket idle time, default 300000"
                       :input-buffer-bytes       "max websocket input buffer size"
                       :output-buffer-bytes      "max websocket output buffer size"
                       :max-text-message-bytes   "max websocket text message size that can be received"
                       :max-binary-message-bytes "max websocket binary message size that can be received"
                       :max-frame-bytes          "max websocket frame size"
                       :max-outgoing-frames      "max websocket frames waiting to be sent per session, default -1"
                       :auto-fragment            "websocket auto fragment (boolean), default true"}

  #:slipway.server{:connector     "the connector supported by this server"
                   :connectors    "the connectors supported by this server (when many connectors supported)"
                   :handler       "the handler for this server, dispatches on :slipway.server/handler-type, :default is slipway.context/handler"
                   :thread-pool   "the thread-pool used by this server (nil for default behaviour)"
                   :scheduler     "the scheduler used by this server (nil for default behaviour)"
                   :buffer-pool   "the buffer-pool used by this server (nil for default behaviour)"
                   :error-handler "the error-handler used by this server for Jetty level errors (nil for default behaviour)"}

  #:slipway{:join? "join the Jetty threadpool, blocks the calling thread until jetty exits, default false"})

(defn start ^Server
  [{::keys [join?] :as opts}]
  (log/debugf "starting jetty server join? %s" join?)
  (let [server (server/create-server opts)]
    (.start server)
    (when join?
      (log/debug "joining jetty thread")
      (.join server))
    server))

(defn stop
  [^Server server]
  (log/debug "stopping jetty server")
  (.stop server))