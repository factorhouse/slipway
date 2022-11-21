(ns slipway.connector.https
  (:require [clojure.tools.logging :as log]
            [slipway.server :as server])
  (:import (java.security KeyStore)
           (org.eclipse.jetty.http HttpVersion)
           (org.eclipse.jetty.server ConnectionFactory ForwardedRequestCustomizer HttpConfiguration
                                     HttpConnectionFactory ProxyConnectionFactory SecureRequestCustomizer Server ServerConnector SslConnectionFactory)
           (org.eclipse.jetty.util.ssl SslContextFactory$Server)))

(defn default-config ^HttpConfiguration
  [{::keys [port http-forwarded?]}]
  (let [config (doto (HttpConfiguration.)
                 (.setSecurePort port)
                 (.setSendServerVersion false)
                 (.setSendDateHeader false)
                 (.addCustomizer (doto (SecureRequestCustomizer.)
                                   (.setSniRequired false)
                                   (.setSniHostCheck true))))]
    (when http-forwarded? (.addCustomizer config (ForwardedRequestCustomizer.)))
    config))

(defn context-factory ^SslContextFactory$Server
  [{::keys [keystore keystore-type keystore-password client-auth key-manager-password truststore truststore-password
            truststore-type include-protocols security-provider exclude-ciphers replace-exclude-ciphers?
            exclude-protocols replace-exclude-protocols? ssl-context]}]
  (let [context-factory (SslContextFactory$Server.)]
    (.setProvider context-factory security-provider)
    (if (string? keystore)
      (.setKeyStorePath context-factory keystore)
      (.setKeyStore context-factory ^KeyStore keystore))
    (when (string? keystore-type)
      (.setKeyStoreType context-factory keystore-type))
    (.setKeyStorePassword context-factory keystore-password)
    (when key-manager-password
      (.setKeyManagerPassword context-factory key-manager-password))
    (cond
      (string? truststore)
      (.setTrustStorePath context-factory truststore)
      (instance? KeyStore truststore)
      (.setTrustStore context-factory ^KeyStore truststore))
    (when truststore-password
      (.setTrustStorePassword context-factory truststore-password))
    (when truststore-type
      (.setTrustStoreType context-factory truststore-type))
    (when ssl-context
      (.setSslContext context-factory ssl-context))
    (case client-auth
      :need (.setNeedClientAuth context-factory true)
      :want (.setWantClientAuth context-factory true)
      nil)
    (when exclude-ciphers
      (let [ciphers (into-array String exclude-ciphers)]
        (if replace-exclude-ciphers?
          (.setExcludeCipherSuites context-factory ciphers)
          (.addExcludeCipherSuites context-factory ciphers))))
    (when include-protocols
      (.setIncludeProtocols context-factory (into-array String include-protocols)))
    (when exclude-protocols
      (let [protocols (into-array String exclude-protocols)]
        (if replace-exclude-protocols?
          (.setExcludeProtocols context-factory protocols)
          (.addExcludeProtocols context-factory protocols))))
    context-factory))

(defn proxied-connector ^ServerConnector
  [^Server server ^HttpConnectionFactory http-factory {::keys [port http-forwarded?] :as opts}]
  (log/infof (str "starting proxied HTTPS connector on :%s" (when http-forwarded? " with http-forwarded support")) port)
  (let [ssl-factory (SslConnectionFactory. (context-factory opts) (.asString HttpVersion/HTTP_1_1))
        factories   (into-array ConnectionFactory [(ProxyConnectionFactory.) ssl-factory http-factory])]
    (ServerConnector. server ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" factories)))

(defn standard-connector ^ServerConnector
  [^Server server {::keys [port http-forwarded?] :as opts}]
  (log/infof (str "starting HTTPS connector on :%s" (when http-forwarded? " with http-forwarded support")) port)
  (let [factories (->> [(HttpConnectionFactory. (default-config opts))] (into-array ConnectionFactory))]
    (ServerConnector. server (context-factory opts) ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" factories)))

(comment
  #:slipway.connector.https{:host                       "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces"
                            :port                       "port this connector listens on. If set the 0 a random port is assigned which may be obtained with getLocalPort()"
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
                            :ssl-context                "a concrete pre-configured SslContext"})

(defmethod server/connector ::connector
  [^Server server {::keys [host port idle-timeout proxy-protocol? configurator http-config]
                   :or    {idle-timeout 200000}
                   :as    opts}]
  {:pre [port]}
  (let [http-factory (HttpConnectionFactory. (or http-config (default-config opts)))
        connector    (if proxy-protocol? (proxied-connector server http-factory opts) (standard-connector server opts))]
    (.setHost connector host)
    (.setPort connector port)
    (.setIdleTimeout connector idle-timeout)
    (when configurator (configurator connector))
    connector))
