(ns slipway.connector.https
  (:require [clojure.tools.logging :as log]
            [slipway.server :as server])
  (:import (java.security KeyStore)
           (org.eclipse.jetty.http HttpVersion)
           (org.eclipse.jetty.server ConnectionFactory ForwardedRequestCustomizer HttpConfiguration
                                     HttpConnectionFactory ProxyConnectionFactory SecureRequestCustomizer Server ServerConnector SslConnectionFactory)
           (org.eclipse.jetty.util.ssl SslContextFactory$Server)))

(defn default-config ^HttpConfiguration
  [{::keys [port http-forwarded? sni-required? sni-host-check? sts-max-age sts-include-subdomains?]
    :or    {sni-required?           false
            sni-host-check?         false
            sts-max-age             -1
            sts-include-subdomains? false}}]
  (log/infof "sni required? %s, sni host check? %s, sts-max-age %s, sts-include-subdomains? %s"
             sni-required? sni-host-check? sts-max-age sts-include-subdomains?)
  (let [config (doto (HttpConfiguration.)
                 (.setSecurePort port)
                 (.setSendServerVersion false)
                 (.setSendDateHeader false)
                 (.addCustomizer (doto (SecureRequestCustomizer.)
                                   (.setSniRequired sni-required?)
                                   (.setSniHostCheck sni-host-check?)
                                   (.setStsMaxAge sts-max-age)
                                   (.setStsIncludeSubDomains sts-include-subdomains?))))]
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
  [^Server server ^HttpConnectionFactory http-factory {::keys [host port http-forwarded?] :as opts}]
  (log/infof (str "starting proxied HTTPS connector on %s:%s" (when http-forwarded? " with http-forwarded support")) (or host "all-interfaces") port)
  (let [ssl-factory (SslConnectionFactory. (context-factory opts) (.asString HttpVersion/HTTP_1_1))
        factories   (into-array ConnectionFactory [(ProxyConnectionFactory.) ssl-factory http-factory])]
    (ServerConnector. server ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" factories)))

(defn standard-connector ^ServerConnector
  [^Server server ^HttpConnectionFactory http-factory {::keys [host port http-forwarded?] :as opts}]
  (log/infof (str "starting HTTPS connector on %s:%s" (when http-forwarded? " with http-forwarded support")) (or host "all-interfaces") port)
  (ServerConnector. server (context-factory opts) ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" (into-array ConnectionFactory [http-factory])))

(comment
  #:slipway.connector.https{:host                       "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces"
                            :port                       "port this connector listens on. If set to 0 a random port is assigned which may be obtained with getLocalPort(). default 443"
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
                            :sts-include-subdomains?    "true if a include subdomain property is sent with any Strict-Transport-Security header"})

(defmethod server/connector ::connector
  [^Server server {::keys [host port idle-timeout proxy-protocol? http-config configurator]
                   :or    {idle-timeout 200000
                           port         443}
                   :as    opts}]
  (let [http-factory (HttpConnectionFactory. (or http-config (default-config opts)))
        connector    (if proxy-protocol? (proxied-connector server http-factory opts) (standard-connector server http-factory opts))]
    (.setHost connector host)
    (.setPort connector port)
    (.setIdleTimeout connector idle-timeout)
    (when configurator (configurator connector))
    connector))
