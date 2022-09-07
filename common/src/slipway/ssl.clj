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
                 :security-provider          "the security provider name"
                 :client-auth                "either :need or :want to set the corresponding need/wantClientAuth field"
                 :ssl-context                "a concrete pre-configured SslContext"})

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