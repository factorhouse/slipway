(ns slipway
  (:require [clojure.tools.logging :as log]
            [slipway.authz :as authz]
            [slipway.connector.http]
            [slipway.connector.https]
            [slipway.server :as server])
  (:import (org.eclipse.jetty.server Handler Server)))

;;; server
;:port - the port to listen on (defaults to 3000)
;:host - the hostname to listen on
;:http? - allow connections over HTTP
;: ssl? - allow connections over HTTPS
;:http-forwarded? - support for X-Forwarded-For header (defaults to false)
;:gzip? - enable Gzip compression on the server (defaults to true)
;:gzip-content-types - contents types to apply Gzip compression to (defaults to ["text/css" "text/plain" "text/javascript" "application/javascript" "image/svg+xml"])
;:gzip-min-size - the minimum size (in bytes) to apply Gzip compression to the response body. Default 1024
;:error-handler - sets an error handlers on the server for catch-all Jetty errors (something that extends the `org.eclipse.jetty.server.handler.ErrorHandler` class)
;:daemon? - use daemon threads (defaults to false)
;:send-server-version? - whether to send the Server header in responses (default false)
;:send-date-header? - whether to send Date header in responses (default false)
;:output-buffer-size - set the size of the buffer into which response content is aggregated before being sent to the client. A larger buffer can improve performance by allowing a content producer to run without blocking, however larger buffers consume more memory and may induce some latency before a client starts processing the content. (default 32768)
;:request-header-size - sets the maximum allowed size in bytes for the HTTP request line and HTTP request headers (default 8192)
;:response-header-size -  the maximum size in bytes of the response header (default 8192)
;:header-cache-size - the size of the header field cache, in terms of unique characters (default 512)
;:thread-pool - the thread pool for Jetty workload
;:max-threads - the maximum number of threads to use (default 50), ignored if `:thread-pool` provided
;:min-threads - the minimum number of threads to use (default 8), ignored if `:thread-pool` provided
;:threadpool-idle-timeout - the maximum idle time in milliseconds for a thread (default 60000), ignored if `:thread-pool` provided
;:job-queue - the job queue to be used by the Jetty threadpool (default is unbounded), ignored if `:thread-pool` provided
;:max-idle-time  - the maximum idle time in milliseconds for a connection (default 200000)
;:client-auth - SSL client certificate authenticate, may be set to :need, :want or :none (defaults to :none)
;:proxy? - enable the proxy protocol on plain socket port (see http://www.eclipse.org/jetty/documentation/9.4.x/configuring-connectors.html#_proxy_protocol)
;:sni-required? - require sni for secure connection, default to false
;:sni-host-check? - enable host check for secure connection, default to true

(comment
  #:slipway.handler.gzip
          {:enabled?            "is gzip enabled? default true"
           :included-mime-types "mime types to include (without charset or other parameters), leave nil for default types"
           :excluded-mime-types "mime types to exclude (replacing any previous exclusion set)"
           :min-gzip-size       "min response size to trigger dynamic compression"}

  #:slipway.connector.https
          {:host                       "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces."
           :port                       "port this connector listens on. If set the 0 a random port is assigned which may be obtained with getLocalPort()"
           :idle-timeout               "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 180000."
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
           :ssl-context                "a concrete pre-configured SslContext"}

  #:slipway.connector.http
          {:host            "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces."
           :port            "port this connector listens on. If set the 0 a random port is assigned which may be obtained with getLocalPort()"
           :idle-timeout    "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 180000."
           :http-forwarded? "if true, add the ForwardRequestCustomizer. See Jetty Forward HTTP docs"
           :proxy-protocol? "if true, add the ProxyConnectionFactor. See Jetty Proxy Protocol docs"
           :http-config     "a concrete HttpConfiguration object to replace the default config entirely"
           :configurator    "a fn taking the final connector as argument, allowing further configuration"}

  #:slipway.authz
          {:login-service       "pluggable Jetty LoginService identifier, 'jaas' and 'hash' supported by default"
           :authenticator       "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
           :constraint-mappings "a list of concrete Jetty ConstraintMapping"
           :realm               "the JAAS realm to use with jaas or hash authentication"
           :hash-user-file      "the path to a Jetty Hash User File"}

  #:slipway.session
          {:secure-request-only?  "set the secure flag on session cookies"
           :http-only?            "set the http-only flag on session cookies"
           :same-site             "set session cookie same-site policy to :none, :lax, or :strict"
           :max-inactive-interval "max session idle time (in s)"
           :tracking-modes        "a set (colloection) of #{:cookie, :ssl, or :url}"
           :cookie-name           "the name of the session cookie"
           :session-id-manager    "the meta manager used for cross context session management"
           :refresh-cookie-age    "max time before a session cookie is re-set (in s)"
           :path-parameter-name   "name of path parameter used for URL session tracking"}

  ;; Jetty 10 / Jetty 11 Websockets
  #:slipway.websockets
          {:idle-timeout            "max websocket idle time (in ms)"
           :input-buffer-size       "max websocket input buffer size"
           :output-buffer-size      "max websocket output buffer size"
           :max-text-message-size   "max websocket text message size"
           :max-binary-message-size "max websocket binary message size"
           :max-frame-size          "max websoccket frame size"
           :auto-fragment           "websocket auto fragment"}

  ;; Jetty 9 Websockets
  #:slipway.websockets
          {:idle-timeout            "max websocket idle time (in ms)"
           :input-buffer-size       "max websocket input buffer size"
           :max-text-message-size   "max websocket text message size"
           :max-binary-message-size "max websocket binary message size"}

  #:slipway.handler
          {:context-path    "the root context path, default '/'"
           :ws-path         "the path serving the websocket upgrade handler, default '/chsk'"
           :null-path-info? "true if /path is not redirected to /path/, default true"}

  #:slipway
          {:join? "join the Jetty threadpool, blocks the calling thread until jetty exits, default false"})

(defn start ^Server
  [ring-handler {::keys [join?] :as opts}]
  (let [server        (server/create-server opts)
        login-service (authz/login-service opts)]
    (.setHandler server ^Handler (server/handler ring-handler login-service opts))
    (some->> login-service (.addBean server))
    (.start server)
    (when join?
      (log/info "joining jetty thread")
      (.join server))
    server))

(defn stop
  [^Server server]
  (.stop server))

