(ns slipway.server
  "Fns to create an org.eclipse.jetty.server.Server instance that conforms to the options map specified in the slipway README.

  Derived from:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9.clj
    * https://github.com/ring-clojure/ring/blob/master/ring-jetty-adapter/src/ring/adapter/jetty.clj"
  (:require [clojure.tools.logging :as log]
            [slipway.authz :as authz]
            [slipway.handler :as handler])
  (:import (java.security KeyStore)
           (org.eclipse.jetty.server ConnectionFactory Connector ForwardedRequestCustomizer Handler HttpConfiguration
                                     HttpConnectionFactory ProxyConnectionFactory SecureRequestCustomizer Server ServerConnector)
           (org.eclipse.jetty.util.ssl SslContextFactory SslContextFactory$Server)
           (org.eclipse.jetty.util.thread QueuedThreadPool ScheduledExecutorScheduler ThreadPool)))

(defn http-config
  [{::keys [ssl-port secure-scheme output-buffer-size request-header-size
            response-header-size send-server-version? send-date-header?
            header-cache-size sni-required? sni-host-check?]
    :or    {ssl-port             443
            secure-scheme        "https"
            output-buffer-size   32768
            request-header-size  8192
            response-header-size 8192
            send-server-version? false
            send-date-header?    false
            header-cache-size    512
            sni-required?        false
            sni-host-check?      true}}]
  (doto (HttpConfiguration.)
    (.setSecureScheme secure-scheme)
    (.setSecurePort ssl-port)
    (.setOutputBufferSize output-buffer-size)
    (.setRequestHeaderSize request-header-size)
    (.setResponseHeaderSize response-header-size)
    (.setSendServerVersion send-server-version?)
    (.setSendDateHeader send-date-header?)
    (.setHeaderCacheSize header-cache-size)
    (.addCustomizer (doto (SecureRequestCustomizer.)
                      (.setSniRequired sni-required?)
                      (.setSniHostCheck sni-host-check?)))))

(defn ssl-context-factory ^SslContextFactory$Server
  [{::keys [keystore keystore-type key-password client-auth key-manager-password
            truststore trust-password truststore-type ssl-protocols ssl-provider
            exclude-ciphers replace-exclude-ciphers? exclude-protocols replace-exclude-protocols?
            ssl-context]}]
  (let [context-server (SslContextFactory$Server.)]
    ;;(.setCipherComparator context-server HTTP2Cipher/COMPARATOR)
    (.setProvider context-server ssl-provider)
    (if (string? keystore)
      (.setKeyStorePath context-server keystore)
      (.setKeyStore context-server ^KeyStore keystore))
    (when (string? keystore-type)
      (.setKeyStoreType context-server keystore-type))
    (.setKeyStorePassword context-server key-password)
    (when key-manager-password
      (.setKeyManagerPassword context-server key-manager-password))
    (cond
      (string? truststore)
      (.setTrustStorePath context-server truststore)
      (instance? KeyStore truststore)
      (.setTrustStore context-server ^KeyStore truststore))
    (when trust-password
      (.setTrustStorePassword context-server trust-password))
    (when truststore-type
      (.setTrustStoreType context-server truststore-type))
    (when ssl-context
      (.setSslContext context-server ssl-context))
    (case client-auth
      :need (.setNeedClientAuth context-server true)
      :want (.setWantClientAuth context-server true)
      nil)
    (when-let [exclude-ciphers exclude-ciphers]
      (let [ciphers (into-array String exclude-ciphers)]
        (if replace-exclude-ciphers?
          (.setExcludeCipherSuites context-server ciphers)
          (.addExcludeCipherSuites context-server ciphers))))
    (when ssl-protocols
      (.setIncludeProtocols context-server (into-array String ssl-protocols)))
    (when exclude-protocols
      (let [protocols (into-array String exclude-protocols)]
        (if replace-exclude-protocols?
          (.setExcludeProtocols context-server protocols)
          (.addExcludeProtocols context-server protocols))))
    context-server))

(defn https-connector
  [server http-configuration ssl-context-factory port host max-idle-time]
  (let [secure-connection-factory [(HttpConnectionFactory. http-configuration)]]
    (log/infof "starting HTTPS connector on port %s" port)
    (doto (ServerConnector.
           ^Server server
           ^SslContextFactory ssl-context-factory
           ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" (into-array ConnectionFactory secure-connection-factory))
      (.setPort port)
      (.setHost host)
      (.setIdleTimeout max-idle-time))))

(defn http-connector
  [server http-configuration port host max-idle-time proxy?]
  (let [plain-connection-factories (cond-> [(HttpConnectionFactory. http-configuration)]
                                     proxy? (concat [(ProxyConnectionFactory.)]))]
    (log/infof "starting HTTP connector on port %s" port)
    (doto (ServerConnector.
           ^Server server
           ^"[Lorg.eclipse.jetty.server.ConnectionFactory;"
           (into-array ConnectionFactory plain-connection-factories))
      (.setPort port)
      (.setHost host)
      (.setIdleTimeout max-idle-time))))

;; TODO validate this for dual http/https mode
(defn add-forward-request-customizer
  [^Server server]
  (some-> (.getConnectors server)
          ^Connector first
          ^HttpConnectionFactory (.getConnectionFactory "HTTP/1.1")
          (.getHttpConfiguration)
          (.addCustomizer (ForwardedRequestCustomizer.))))

(defn create-server ^Server
  [{::keys [port max-threads min-threads threadpool-idle-timeout job-queue daemon? max-idle-time host ssl? ssl-port http?
            proxy? thread-pool http-forwarded? error-handler]
    :or    {port                    3000
            max-threads             50
            min-threads             8
            threadpool-idle-timeout 60000
            job-queue               nil
            daemon?                 false
            max-idle-time           200000
            ssl?                    false
            http?                   true
            proxy?                  false}
    :as    options}]
  {:pre [(or http? ssl? ssl-port)]}
  (let [pool               (or thread-pool
                               (doto (QueuedThreadPool. (int max-threads)
                                                        (int min-threads)
                                                        (int threadpool-idle-timeout)
                                                        job-queue)
                                 (.setDaemon daemon?)))
        server             (doto (Server. ^ThreadPool pool)
                             (.addBean (ScheduledExecutorScheduler.)))
        http-configuration (http-config options)
        ssl?               (or ssl? ssl-port)
        connectors         (cond-> []
                             ssl? (conj (https-connector server http-configuration (ssl-context-factory options)
                                                         ssl-port host max-idle-time))
                             http? (conj (http-connector server http-configuration port host max-idle-time proxy?)))]
    (.setConnectors server (into-array connectors))

    ;; TODO: push this back up into creation of connectors rather than after the fact
    (when http-forwarded? (add-forward-request-customizer server))
    (when error-handler (.setErrorHandler server error-handler))

    server))

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

;:join? - blocks the thread until server ends (defaults to false)

;;; ssl

;:ssl-port - the SSL port to listen on (defaults to 443, implies :ssl?)
;:ssl-context - an optional SSLContext to use for SSL connections
;:ssl-protocols - the ssl protocols to use, default to ["TLSv1.3" "TLSv1.2"]
;:ssl-provider - the ssl provider
;:exclude-ciphers      - when :ssl? is true, additionally exclude these cipher suites
;:exclude-protocols    - when :ssl? is true, additionally exclude these protocols
;:keystore - the keystore to use for SSL connections
;:keystore-type - the format of keystore
;:key-password - the password to the keystore
;:key-manager-password - the password for key manager
;:truststore - a truststore to use for SSL connections
;:truststore-type - the format of trust store
;:trust-password - the password to the truststore
;:replace-exclude-ciphers?   - when true, :exclude-ciphers will replace rather than add to the cipher exclusion list (defaults to false)
;:replace-exclude-protocols? - when true, :exclude-protocols will replace rather than add to the protocols exclusion list (defaults to false)

;;; websockets
;:ws-max-idle-time  - the maximum idle time in milliseconds for a websocket connection (default 500000)
;:ws-max-text-message-size  - the maximum text message size in bytes for a websocket connection (default 65536)

;;; auth / session
;:auth - Map of auth opts. Configures Jetty JAAS auth, see JAAS Integration section of README
;;session

;;; handler
; context-path null-path-info?

(defn start ^Server
  [ring-handler {::keys [join?] :as opts}]
  (let [server        (create-server opts)
        login-service (authz/login-service opts)]
    (.setHandler server ^Handler (handler/root ring-handler login-service opts))
    (some->> login-service (.addBean server))
    (.start server)
    (when join? (.join server))
    server))

(defn stop
  [^Server server]
  (.stop server))