(ns slipway
  (:require [clojure.tools.logging :as log]
            [slipway.connector.http]
            [slipway.connector.https]
            [slipway.handler]
            [slipway.security :as security]
            [slipway.server :as server]
            [slipway.user])
  (:import (org.eclipse.jetty.server Handler Server)))

(comment
  #:slipway.handler.gzip{:enabled?            "is gzip enabled? default true"
                         :included-mime-types "mime types to include (without charset or other parameters), leave nil for default types"
                         :excluded-mime-types "mime types to exclude (replacing any previous exclusion set)"
                         :min-gzip-size       "min response size to trigger dynamic compression (in bytes, default 1024)"}

  #:slipway.connector.https{:host                       "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces"
                            :port                       "port this connector listens on. If set to 0 a random port is assigned which may be obtained with getLocalPort(), default 443"
                            :idle-timeout               "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 200000 ms"
                            :http-forwarded?            "if true, add the ForwardRequestCustomizer. See Jetty Forward HTTP docs"
                            :proxy-protocol?            "if true, add the ProxyConnectionFactor. See Jetty Proxy Protocol docs"
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
                            :sni-required?              "true if SNI is required, else requests will be rejected with 400 response, default false"
                            :sni-host-check?            "true if the SNI Host name must match when there is an SNI certificate, default false"
                            :sts-max-age                "set the Strict-Transport-Security max age in seconds, default -1"
                            :sts-include-subdomains?    "true if a include subdomain property is sent with any Strict-Transport-Security header"}

  #:slipway.connector.http{:host            "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces."
                           :port            "port this connector listens on. If set to 0 a random port is assigned which may be obtained with getLocalPort(), default 80"
                           :idle-timeout    "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 200000 ms"
                           :http-forwarded? "if true, add the ForwardRequestCustomizer. See Jetty Forward HTTP docs"
                           :proxy-protocol? "if true, add the ProxyConnectionFactory. See Jetty Proxy Protocol docs"
                           :http-config     "a concrete HttpConfiguration object to replace the default config entirely"
                           :configurator    "a fn taking the final connector as argument, allowing further configuration"}

  #:slipway.security{:realm               "the Jetty authentication realm"
                     :hash-user-file      "the path to a Jetty Hash User File"
                     :login-service       "a Jetty LoginService identifier, 'jaas' and 'hash' supported by default"
                     :identity-service    "a concrete Jetty IdentityService"
                     :authenticator       "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
                     :constraint-mappings "a list of concrete Jetty ConstraintMapping"}

  #:slipway.session{:secure-request-only?  "set the secure flag on session cookies (default true)"
                    :http-only?            "set the http-only flag on session cookies (default true)"
                    :same-site             "set session cookie same-site policy to :none, :lax, or :strict (default :strict)"
                    :max-inactive-interval "max session idle time (in s, default -1)"
                    :cookie-name           "the name of the session cookie"
                    :session-id-manager    "the meta manager used for cross context session management"
                    :refresh-cookie-age    "max time before a session cookie is re-set (in s)"
                    :path-parameter-name   "name of path parameter used for URL session tracking"}

  ;; Jetty 10 / Jetty 11 Websockets
  #:slipway.websockets{:idle-timeout            "max websocket idle time (in ms), default 500000"
                       :input-buffer-size       "max websocket input buffer size (in bytes)"
                       :output-buffer-size      "max websocket output buffer size (in bytes)"
                       :max-text-message-size   "max websocket text message size (in bytes, default 65536)"
                       :max-binary-message-size "max websocket binary message size (in bytes)"
                       :max-frame-size          "max websocket frame size (in bytes)"
                       :auto-fragment           "websocket auto fragment (boolean)"}

  ;; Jetty 9 Websockets
  #:slipway.websockets{:idle-timeout            "max websocket idle time (in ms), default 500000"
                       :input-buffer-size       "max websocket input buffer size"
                       :max-text-message-size   "max websocket text message size"
                       :max-binary-message-size "max websocket binary message size"}

  #:slipway.handler{:context-path    "the root context path, default '/'"
                    :ws-path         "the path serving the websocket upgrade handler, default '/chsk'"
                    :null-path-info? "true if /path is not redirected to /path/, default true"}

  #:slipway.server{:handler       "the base Jetty handler implementation (:default defmethod impl found in slipway.handler)"
                   :connectors    "the connectors supported by this server"
                   :thread-pool   "the thread-pool used by this server (leave null for reasonable defaults)"
                   :error-handler "the error-handler used by this server for Jetty level errors"}

  #:slipway{:join? "join the Jetty threadpool, blocks the calling thread until jetty exits, default false"})

(defn start ^Server
  [ring-handler {::keys [join?] :as opts}]
  (log/info "starting slipway server")
  (let [server        (server/create-server opts)
        login-service (security/login-service opts)]
    (.setHandler server ^Handler (server/handler ring-handler login-service opts))
    (some->> login-service (.addBean server))
    (.start server)
    (when join?
      (log/info "joining jetty thread")
      (.join server))
    server))

(defn stop
  [^Server server]
  (log/info "stopping slipway server")
  (.stop server))