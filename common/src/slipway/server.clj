(ns slipway.server
  (:import (org.eclipse.jetty.server Connector Server)
           (org.eclipse.jetty.util.thread ScheduledExecutorScheduler ThreadPool)))

(defmulti handler (fn [_ring_handler _login_service opts] (::handler opts)))

(defmulti connector (fn [_server opts] (keyword (namespace (first (keys opts))) "connector")))

(comment
  #:slipway.server{:handler       ""
                   :connectors    ""
                   :thread-pool   ""
                   :error-handler ""})

(defn create-server ^Server
  [{::keys [connectors thread-pool error-handler]}]
  {:pre [connectors]}
  (let [server (Server. ^ThreadPool thread-pool)]
    (.setConnectors server (into-array Connector (map #(connector server %) connectors)))
    (.addBean server (ScheduledExecutorScheduler.))
    (when error-handler (.setErrorHandler server error-handler))
    server))