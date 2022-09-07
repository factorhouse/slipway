(ns slipway.ssl
  (:import (java.security KeyStore)
           (org.eclipse.jetty.util.ssl SslContextFactory$Server)))

(comment
  #:slipway.ssl {:keystore                   "keystore to use, either path (String) or concrete KeyStore"
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
                 :ssl-provider               "the security provider name"
                 :client-auth                "either :need or :want to set the corresponding need/wantClientAuth field"
                 :ssl-context                "a concrete pre-configured SslContext"})

(defn context-factory ^SslContextFactory$Server
  [{::keys [keystore keystore-type keystore-password client-auth key-manager-password truststore truststore-password
            truststore-type include-protocols ssl-provider exclude-ciphers replace-exclude-ciphers? exclude-protocols
            replace-exclude-protocols? ssl-context]}]
  (let [context-server (SslContextFactory$Server.)]
    (.setProvider context-server ssl-provider)
    (if (string? keystore)
      (.setKeyStorePath context-server keystore)
      (.setKeyStore context-server ^KeyStore keystore))
    (when (string? keystore-type)
      (.setKeyStoreType context-server keystore-type))
    (.setKeyStorePassword context-server keystore-password)
    (when key-manager-password
      (.setKeyManagerPassword context-server key-manager-password))
    (cond
      (string? truststore)
      (.setTrustStorePath context-server truststore)
      (instance? KeyStore truststore)
      (.setTrustStore context-server ^KeyStore truststore))
    (when truststore-password
      (.setTrustStorePassword context-server truststore-password))
    (when truststore-type
      (.setTrustStoreType context-server truststore-type))
    (when ssl-context
      (.setSslContext context-server ssl-context))
    (case client-auth
      :need (.setNeedClientAuth context-server true)
      :want (.setWantClientAuth context-server true)
      nil)
    (when exclude-ciphers
      (let [ciphers (into-array String exclude-ciphers)]
        (if replace-exclude-ciphers?
          (.setExcludeCipherSuites context-server ciphers)
          (.addExcludeCipherSuites context-server ciphers))))
    (when include-protocols
      (.setIncludeProtocols context-server (into-array String include-protocols)))
    (when exclude-protocols
      (let [protocols (into-array String exclude-protocols)]
        (if replace-exclude-protocols?
          (.setExcludeProtocols context-server protocols)
          (.addExcludeProtocols context-server protocols))))
    context-server))