(ns slipway.server
  (:require [clojure.tools.logging :as log])
  (:import (org.eclipse.jetty.io ByteBufferPool)
           (org.eclipse.jetty.server Connector Handler Server)
           (org.eclipse.jetty.util.thread Scheduler ThreadPool)))

(defmulti ^Handler handler (fn [_server opts] (::handler-type opts)))

(defmulti ^Connector connector (fn [_server opts] (keyword (namespace (first (keys opts))) "connector")))

(comment
  #:slipway.server{:connector     "the connector supported by this server"
                   :connectors    "the connectors supported by this server (when many connectors supported)"
                   :handler       "the handler for this server, dispatches on :slipway.server/handler-type, :default is slipway.context/handler"
                   :thread-pool   "the thread-pool used by this server (nil for default behaviour)"
                   :scheduler     "the scheduler used by this server (nil for default behaviour)"
                   :buffer-pool   "the buffer-pool used by this server (nil for default behaviour)"
                   :error-handler "the error-handler used by this server for Jetty level errors (nil for default behaviour)"})

(defn create-server ^Server
  [{::keys [connectors thread-pool scheduler buffer-pool error-handler] :as opts}]
  (log/debugf "creating server with [%s] connectors" (count connectors))
  (let [server (Server. ^ThreadPool thread-pool ^Scheduler scheduler ^ByteBufferPool buffer-pool)
        {handler-config   ::handler
         connector-config ::connector} opts]
    (.setConnectors server (into-array Connector
                                       (if connector-config
                                         [(connector server connector-config)]
                                         (map #(connector server %) connectors))))
    (.setHandler server (handler server handler-config))
    (some->> error-handler (.setErrorHandler server))
    server))