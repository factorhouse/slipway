(ns slipway
  (:require [clojure.tools.logging :as log]
            [slipway.connector.http]
            [slipway.connector.https]
            [slipway.handler]
            [slipway.security]
            [slipway.server :as server]
            [slipway.user]
            [slipway.websockets])
  (:import (org.eclipse.jetty.server Server)))

(comment
  #:slipway.handler.compression{:enabled?           "is compression handler enabled? default true"
                                :path-spec          "the compression path-spec, default '/*'"
                                :format             "compression format, defaults to :gzip"
                                :compress-min-bytes "min response size to trigger compression (default 1024 bytes)"
                                :compression-config "a concrete Jetty CompressConfig instance (nil for default configuration)"}

  #:slipway.connector.https{:name                       "the name of this connector (useful for VirtualHosts configuration)"
                            :host                       "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces"
                            :port                       "port this connector listens on. If set to 0 a random port is assigned which may be obtained with getLocalPort(). default 443"
                            :idle-timeout-ms            "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 200000 ms"
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
                           :idle-timeout-ms            "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 200000 ms"
                           :http-forwarded?            "if true, add the ForwardRequestCustomizer. See Jetty Forward HTTP docs"
                           :proxy-protocol?            "if true, add the ProxyConnectionFactory. See Jetty Proxy Protocol docs"
                           :http-config                "a concrete HttpConfiguration object to replace the default config entirely"
                           :configurator               "a fn taking the final connector as argument, allowing further configuration"
                           :send-server-version?       "if true, send the Server header in responses"
                           :send-date-header?          "if true, send the Date header in responses"
                           :relative-redirect-allowed? "if true, allow relative redirects, default false"
                           :http-compliance            "set the HttpCompliance mode, defaults to HttpCompliance/RFC9110"}

  #:slipway.security{:handler "identifies a SecurityHandler impl, 'jaas', 'hash', and 'openid' supported by default"}

  #:slipway.security.hash{:realm               "optional Jetty authentication realm"
                          :user-file           "the path to a Jetty hash-user file"
                          :users               "a sequence of [^String user-name, ^String credential, ^String[] [roles]]"
                          :authenticator       "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
                          :constraint-mappings "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"
                          :identity-service    "an (optional) concrete Jetty IdentityService"}

  #:slipway.security.jaas{:realm               "the Jetty authentication realm"
                          :authenticator       "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
                          :constraint-mappings "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"
                          :identity-service    "an (optional) concrete Jetty IdentityService"}

  #:slipway.session{:secure-request-only?    "set the secure flag on session cookies"
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

  #:slipway.websockets{:enabled?                 "are websockets enabled? default true"
                       :path-spec                "the websocket path-spec, default '/chsk'"
                       :idle-timeout-ms          "max websocket idle time, default 300000"
                       :input-buffer-bytes       "max websocket input buffer size"
                       :output-buffer-bytes      "max websocket output buffer size"
                       :max-text-message-bytes   "max websocket text message size that can be received"
                       :max-binary-message-bytes "max websocket binary message size that can be received"
                       :max-frame-bytes          "max websocket frame size"
                       :max-outgoing-frames      "max websocket frames waiting to be sent per session, default -1"
                       :auto-fragment            "websocket auto fragment (boolean), default true"}

  #:slipway.handler{:context-path    "the root context path, default '/'"
                    :null-path-info? "true if /path is not redirected to /path/, default true"}

  #:slipway.server{:handler       "the base Jetty handler implementation (:default defmethod impl found in slipway.handler)"
                   :connectors    "the connectors supported by this server"
                   :thread-pool   "the thread-pool used by this server (nil for default behaviour)"
                   :scheduler     "the scheduler used by this server (nil for default behaviour)"
                   :buffer-pool   "the buffer-pool used by this server (nil for default behaviour)"
                   :error-handler "the error-handler used by this server for Jetty level errors"}

  #:slipway{:join? "join the Jetty threadpool, blocks the calling thread until jetty exits, default false"})

(defn start ^Server
  [ring-handler {::keys [join?] :as opts}]
  (log/debugf "starting jetty server %s" opts)
  (let [server (server/create-server ring-handler opts)]
    (.start server)
    (when join?
      (log/debug "joining jetty thread")
      (.join server))
    server))

(defn stop
  [^Server server]
  (log/debug "stopping jetty server")
  (.stop server))