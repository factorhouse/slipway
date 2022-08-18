(ns slipway.impl.server
  "Fns to create an org.eclipse.jetty.server.Server instance that conforms to the options map specified in the slipway README.

  Derived from:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9.clj
    * https://github.com/ring-clojure/ring/blob/master/ring-jetty-adapter/src/ring/adapter/jetty.clj"
  (:require [clojure.tools.logging :as log])
  (:import (java.security KeyStore)
           (org.eclipse.jetty.server ConnectionFactory Connector ForwardedRequestCustomizer HttpConfiguration
                                     HttpConnectionFactory ProxyConnectionFactory SecureRequestCustomizer Server ServerConnector)
           (org.eclipse.jetty.server.handler.gzip GzipHandler)
           (org.eclipse.jetty.util.ssl SslContextFactory SslContextFactory$Server)
           (org.eclipse.jetty.util.thread QueuedThreadPool ScheduledExecutorScheduler ThreadPool)))

(def default-gzip-content-types
  ["text/css"
   "text/plain"
   "text/javascript"
   "application/javascript"
   "image/svg+xml"])

(defn http-config
  [{:keys [ssl-port secure-scheme output-buffer-size request-header-size
           response-header-size send-server-version? send-date-header?
           header-cache-size sni-required? sni-host-check?]
    :or   {ssl-port             443
           secure-scheme        "https"
           output-buffer-size   32768
           request-header-size  8192
           response-header-size 8192
           send-server-version? false
           send-date-header?    false
           header-cache-size    512
           sni-required?        false
           sni-host-check?      true}}]
  (let [secure-customizer (doto (SecureRequestCustomizer.)
                            (.setSniRequired sni-required?)
                            (.setSniHostCheck sni-host-check?))]
    (doto (HttpConfiguration.)
      (.setSecureScheme secure-scheme)
      (.setSecurePort ssl-port)
      (.setOutputBufferSize output-buffer-size)
      (.setRequestHeaderSize request-header-size)
      (.setResponseHeaderSize response-header-size)
      (.setSendServerVersion send-server-version?)
      (.setSendDateHeader send-date-header?)
      (.setHeaderCacheSize header-cache-size)
      (.addCustomizer secure-customizer))))

(defn ssl-context-factory ^SslContextFactory$Server
  [{:keys [keystore keystore-type key-password client-auth key-manager-password
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

(defn http-forwarded-configurator
  [^Server server]
  (some-> (.getConnectors server)
          ^Connector first
          ^HttpConnectionFactory (.getConnectionFactory "HTTP/1.1")
          (.getHttpConfiguration)
          (.addCustomizer (ForwardedRequestCustomizer.))))

(defn gzip-configurator
  [^Server server content-types min-gzip-size]
  (let [gzip-handler (doto (GzipHandler.)
                       (.setIncludedMimeTypes (into-array String content-types))
                       (.setMinGzipSize min-gzip-size)
                       (.setHandler (.getHandler server)))]
    (log/infof "enabling gzip compression on the following content types: %s" content-types)
    (.setHandler server gzip-handler)))

(defn create-server ^Server
  [{:as   options
    :keys [port max-threads min-threads threadpool-idle-timeout job-queue
           daemon? max-idle-time host ssl? ssl-port http? proxy?
           thread-pool]
    :or   {port                    3000
           max-threads             50
           min-threads             8
           threadpool-idle-timeout 60000
           job-queue               nil
           daemon?                 false
           max-idle-time           200000
           ssl?                    false
           http?                   true
           proxy?                  false}}]
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
    server))