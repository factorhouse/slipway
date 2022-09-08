(ns slipway.connector.http
  (:require [clojure.tools.logging :as log]
            [slipway.server :as server])
  (:import (org.eclipse.jetty.server ConnectionFactory ForwardedRequestCustomizer HttpConfiguration
                                     HttpConnectionFactory ProxyConnectionFactory Server ServerConnector)))

(defn default-config ^HttpConfiguration
  [{::keys [http-forwarded?]}]
  (let [config (doto (HttpConfiguration.)
                 (.setSendServerVersion false)
                 (.setSendDateHeader false))]
    (when http-forwarded? (.addCustomizer config (ForwardedRequestCustomizer.)))
    config))

(comment
  #:slipway.connector.http {:host            "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces."
                            :port            "port this connector listens on. If set the 0 a random port is assigned which may be obtained with getLocalPort()"
                            :idle-timeout    "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 180000."
                            :http-forwarded? "if true, add the ForwardRequestCustomizer. See Jetty Forward HTTP docs"
                            :proxy-protocol? "if true, add the ProxyConnectionFactor. See Jetty Proxy Protocol docs"
                            :http-config     "a concrete HttpConfiguration object to replace the default config entirely"
                            :configurator    "a fn taking the final connector as argument, allowing further configuration"})

(defmethod server/connector ::connector
  [^Server server {::keys [host port idle-timeout proxy-protocol? http-forwarded? configurator http-config]
                   :or    {idle-timeout 180000}
                   :as    opts}]
  {:pre [port]}
  (log/infof (str "starting " (when proxy-protocol? "proxied ") "HTTP connector on port %s"
                  (when http-forwarded? " with http-forwarded support")) port)
  (let [http-factory (HttpConnectionFactory. (or http-config (default-config opts)))
        factories    (->> (if proxy-protocol? [(ProxyConnectionFactory.) http-factory] [http-factory])
                          (into-array ConnectionFactory))
        connector    (ServerConnector. ^Server server ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" factories)]
    (.setHost connector host)
    (.setPort connector port)
    (.setIdleTimeout connector idle-timeout)
    (when configurator (configurator connector))
    connector))