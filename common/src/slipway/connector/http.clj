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
  #:slipway.connector.http {:host            ""
                            :port            ""
                            :idle-timeout    ""
                            :http-forwarded? ""
                            :proxy-protocol? ""
                            :http-config     ""
                            :configurator    ""})

(defmethod server/connector ::connector
  [^Server server {::keys [host port idle-timeout proxy-protocol? http-forwarded? configurator http-config] :as opts}]
  {:pre [port]}
  (log/infof (str "starting " (when proxy-protocol? "proxied ") "HTTP connector on port %s"
                  (when http-forwarded? " with http-forwarded support")) port)
  (let [http-factory (HttpConnectionFactory. (or http-config (default-config opts)))
        factories    (->> (if proxy-protocol? [(ProxyConnectionFactory.) http-factory] [http-factory])
                          (into-array ConnectionFactory))
        connector    (ServerConnector. ^Server server ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" factories)]
    (.setHost connector host)
    (.setPort connector port)
    (some->> idle-timeout (.setIdleTimeout connector))
    (when configurator (configurator connector))
    connector))