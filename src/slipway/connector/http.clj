(ns slipway.connector.http
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [slipway.server :as server])
  (:import (org.eclipse.jetty.http HttpCompliance)
           (org.eclipse.jetty.server ConnectionFactory ForwardedRequestCustomizer HttpConfiguration
                                     HttpConnectionFactory ProxyConnectionFactory Server ServerConnector)))

(defn http-compliance-mode ^HttpCompliance
  [http-compliance]
  (when-let [mode (some-> http-compliance str/upper-case (HttpCompliance/valueOf))]
    (log/debugf "enabling HTTP compliance mode of %s" mode)
    mode))

(defn default-config ^HttpConfiguration
  [{::keys [http-forwarded? send-server-version? send-date-header? relative-redirect-allowed? http-compliance]
    :or    {send-server-version?       false
            send-date-header?          false
            relative-redirect-allowed? false}}]
  (let [config    (doto (HttpConfiguration.)
                    (.setSendServerVersion send-server-version?)
                    (.setSendDateHeader send-date-header?)
                    (.setRelativeRedirectAllowed relative-redirect-allowed?))
        http-mode (http-compliance-mode http-compliance)]
    (when http-forwarded? (.addCustomizer config (ForwardedRequestCustomizer.)))
    (when http-mode (.setHttpCompliance config http-mode))
    config))

(comment
  #:slipway.connector.http{:host                       "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces"
                           :port                       "port this connector listens on. If set to 0 a random port is assigned which may be obtained with getLocalPort(), default 80"
                           :idle-timeout-ms            "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 200000 ms"
                           :http-forwarded?            "if true, add the ForwardRequestCustomizer. See Jetty Forward HTTP docs"
                           :proxy-protocol?            "if true, add the ProxyConnectionFactory. See Jetty Proxy Protocol docs"
                           :http-config                "a concrete HttpConfiguration object to replace the default config entirely"
                           :configurator               "a fn taking the final connector as argument, allowing further configuration"
                           :send-server-version?       "if true, send the Server header in responses"
                           :send-date-header?          "if true, send the Date header in responses"
                           :relative-redirect-allowed? "if true, allow relative redirects, default false"
                           :http-compliance            "set the HttpCompliance mode, defaults to HttpCompliance/RFC9110"})

(defmethod server/connector ::connector
  [^Server server {::keys [host port idle-timeout-ms proxy-protocol? http-forwarded? configurator http-config]
                   :or    {idle-timeout-ms 200000
                           port            80}
                   :as    opts}]
  (log/debugf (str "starting " (when proxy-protocol? "proxied ") "HTTP connector on %s:%s" (when http-forwarded? " with http-forwarded support")) (or host "all-interfaces") port)
  (let [http-factory (HttpConnectionFactory. (or http-config (default-config opts)))
        factories    (->> (if proxy-protocol? [(ProxyConnectionFactory.) http-factory] [http-factory])
                          (into-array ConnectionFactory))
        connector    (ServerConnector. ^Server server ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" factories)]
    (.setHost connector host)
    (.setPort connector port)
    (.setIdleTimeout connector idle-timeout-ms)
    (when configurator (configurator connector))
    connector))