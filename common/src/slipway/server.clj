(ns slipway.server
  (:require [clojure.tools.logging :as log]
            [slipway.ssl :as ssl])
  (:import (org.eclipse.jetty.server ConnectionFactory Connector ForwardedRequestCustomizer HttpConfiguration
                                     HttpConnectionFactory ProxyConnectionFactory SecureRequestCustomizer Server ServerConnector)
           (org.eclipse.jetty.util.ssl SslContextFactory)
           (org.eclipse.jetty.util.thread QueuedThreadPool ScheduledExecutorScheduler ThreadPool)))

(defmulti handler (fn [_ _ opts] (::handler opts)))

(defn http-config
  [{::keys [ssl-port secure-scheme output-buffer-size request-header-size response-header-size send-server-version?
            send-date-header? header-cache-size sni-required? sni-host-check?]
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

(defn https-connector
  [server http-configuration ssl-context-factory port host max-idle-time]
  (let [conn-factory [(HttpConnectionFactory. http-configuration)]]
    (log/infof "starting HTTPS connector on port %s" port)
    (doto (ServerConnector.
           ^Server server
           ^SslContextFactory ssl-context-factory
           ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" (into-array ConnectionFactory conn-factory))
      (.setPort port)
      (.setHost host)
      (.setIdleTimeout max-idle-time))))

(defn http-connector
  [server http-configuration port host max-idle-time proxy?]
  (let [conn-factory (cond-> [(HttpConnectionFactory. http-configuration)] proxy? (concat [(ProxyConnectionFactory.)]))]
    (log/infof "starting HTTP connector on port %s" port)
    (doto (ServerConnector.
           ^Server server
           ^"[Lorg.eclipse.jetty.server.ConnectionFactory;" (into-array ConnectionFactory conn-factory))
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

(comment
  #:slipway.server{:handler                 ""

                   :port                    ""
                   :max-threads             ""
                   :min-threads             ""
                   :threadpool-idle-timeout ""
                   :job-queue               ""
                   :daemon?                 ""
                   :max-idle-time           ""
                   :host                    ""
                   :ssl?                    ""
                   :ssl-port                ""
                   :http?                   ""
                   :proxy?                  ""
                   :thread-pool             ""
                   :http-forwarded?         ""
                   :error-handler           ""

                   :secure-scheme           ""
                   :output-buffer-size      ""
                   :request-header-size     ""
                   :response-header-size    ""
                   :send-server-version?    ""
                   :send-date-header?       ""
                   :header-cache-size       ""
                   :sni-required?           ""
                   :sni-host-check?         ""

                   :gzip?                   ""
                   :gzip-content-types      ""
                   :gzip-min-size           ""})

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
    :as    opts}]
  {:pre [(or http? ssl? ssl-port)]}
  (let [pool               (or thread-pool
                               (doto (QueuedThreadPool. (int max-threads)
                                                        (int min-threads)
                                                        (int threadpool-idle-timeout)
                                                        job-queue)
                                 (.setDaemon daemon?)))
        server             (doto (Server. ^ThreadPool pool)
                             (.addBean (ScheduledExecutorScheduler.)))
        http-configuration (http-config opts)
        ssl?               (or ssl? ssl-port)
        connectors         (cond-> []
                             ssl? (conj (https-connector server http-configuration (ssl/context-factory opts)
                                                         ssl-port host max-idle-time))
                             http? (conj (http-connector server http-configuration port host max-idle-time proxy?)))]
    (.setConnectors server (into-array connectors))

    ;; TODO: push this back up into creation of connectors rather than after the fact
    (when http-forwarded? (add-forward-request-customizer server))
    (when error-handler (.setErrorHandler server error-handler))

    server))