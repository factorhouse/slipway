(ns slipway.server
  (:import (org.eclipse.jetty.server Connector Server)
           (org.eclipse.jetty.util.thread ScheduledExecutorScheduler ThreadPool)))

(defmulti handler (fn [_ring_handler _login_service opts] (::handler opts)))

(defmulti connector (fn [_server opts] (keyword (namespace (first (keys opts))) "connector")))

(comment
  #:slipway.server{:handler       "the base Jetty handler implementation (:default defmethod impl found in slipway.handler)"
                   :connectors    "the connectors supported by this server"
                   :thread-pool   "the thread-pool used by this server (leave null for reasonable defaults)"
                   :error-handler "the error-handler used by this server for Jetty level errors"})

(defn create-server ^Server
  [{::keys [connectors thread-pool error-handler]}]
  {:pre [connectors]}
  (let [server (Server. ^ThreadPool thread-pool)]
    (.setConnectors server (into-array Connector (map #(connector server %) connectors)))
    (.addBean server (ScheduledExecutorScheduler.))
    (when error-handler (.setErrorHandler server error-handler))
    server))