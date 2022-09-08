(ns slipway.connector.http
  (:require [clojure.tools.logging :as log]
            [slipway.server :as server])
  (:import (org.eclipse.jetty.server ConnectionFactory ForwardedRequestCustomizer HttpConfiguration
                                     HttpConnectionFactory ProxyConnectionFactory Server ServerConnector)))

(defn config ^HttpConfiguration
  [{::keys [output-buffer-size request-header-size response-header-size send-server-version? send-date-header?
            header-cache-size http-forwarded?]
    :or    {output-buffer-size   32768
            request-header-size  8192
            response-header-size 8192
            send-server-version? false
            send-date-header?    false
            header-cache-size    512
            http-forwarded?      false}}]
  (let [config (doto (HttpConfiguration.)
                 (.setOutputBufferSize output-buffer-size)
                 (.setRequestHeaderSize request-header-size)
                 (.setResponseHeaderSize response-header-size)
                 (.setSendServerVersion send-server-version?)
                 (.setSendDateHeader send-date-header?)
                 (.setHeaderCacheSize header-cache-size))]
    (when http-forwarded? (.addCustomizer config (ForwardedRequestCustomizer.)))
    config))

(comment
  #:slipway.connector.http {:host                 ""
                            :port                 ""
                            :output-buffer-size   ""
                            :request-header-size  ""
                            :response-header-size ""
                            :send-server-version? ""
                            :send-date-header?    ""
                            :header-cache-size    ""
                            :http-forwarded?      ""
                            :proxy-protocol?      ""
                            :idle-timeout         ""})

(defmethod server/connector ::connector
  [^Server server {::keys [host port idle-timeout proxy-protocol? http-forwarded?]
                   :or    {idle-timeout 200000}
                   :as    opts}]
  {:pre [port]}
  (let [factories (->> (if proxy-protocol?
                         [(ProxyConnectionFactory.) (HttpConnectionFactory. (config opts))]
                         [(HttpConnectionFactory. (config opts))])
                       (into-array ConnectionFactory))]
    (log/infof (str "starting " (when proxy-protocol? "proxied ") "HTTP connector on port %s"
                    (when http-forwarded? " with http-forwarded support")) port)
    (doto (ServerConnector. ^Server server ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" factories)
      (.setPort port)
      (.setHost host)
      (.setIdleTimeout idle-timeout))))