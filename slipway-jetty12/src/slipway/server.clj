(ns slipway.server
  (:require [clojure.tools.logging :as log])
  (:import (org.eclipse.jetty.io ByteBufferPool)
           (org.eclipse.jetty.server Connector Server)
           (org.eclipse.jetty.util.thread Scheduler ThreadPool)))

(defmulti handler (fn [_ring_handler _login_service opts] (::handler opts)))

(defmulti connector (fn [_server opts] (keyword (namespace (first (keys opts))) "connector")))

(comment
  #:slipway.server{:handler       "the base Jetty handler implementation (:default defmethod impl found in slipway.handler)"
                   :connectors    "the connectors supported by this server"
                   :thread-pool   "the thread-pool used by this server (nil for default behaviour)"
                   :scheduler     "the scheduler used by this server (nil for default behaviour)"
                   :buffer-pool   "the buffer-pool used by this server (nil for default behaviour)"
                   :error-handler "the error-handler used by this server for Jetty level errors"})

(defn create-server ^Server
  [{::keys [connectors thread-pool scheduler buffer-pool error-handler] :as opts}]
  {:pre [connectors]}
  (log/debugf "creating server %s" opts)
  (let [server (Server. ^ThreadPool thread-pool ^Scheduler scheduler ^ByteBufferPool buffer-pool)]
    (.setConnectors server (into-array Connector (map #(connector server %) connectors)))
    (when error-handler
      (.setErrorHandler server error-handler))
    server))