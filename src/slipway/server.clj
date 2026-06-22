(ns slipway.server
  (:require [clojure.tools.logging :as log])
  (:import (org.eclipse.jetty.io ByteBufferPool)
           (org.eclipse.jetty.server Connector Handler Server)
           (org.eclipse.jetty.util.thread Scheduler ThreadPool)))

(defmulti handler (fn [_server opts] (::handler-impl opts)))

(defmulti connector (fn [_server opts] (keyword (namespace (first (keys opts))) "connector")))

(comment
  #:slipway.server{:handler-impl  "the handler impl dispatch-val (:default defmethod found in slipway.context)"
                   :handler       "the handler for this server"
                   :connector     "the connector supported by this server"
                   :connectors    "the connectors supported by this server (when many connectors supported)"
                   :thread-pool   "the thread-pool used by this server (nil for default behaviour)"
                   :scheduler     "the scheduler used by this server (nil for default behaviour)"
                   :buffer-pool   "the buffer-pool used by this server (nil for default behaviour)"
                   :error-handler "the error-handler used by this server for Jetty level errors (nil for default behaviour)"})

(defn create-server ^Server
  [{::keys [connectors thread-pool scheduler buffer-pool error-handler] :as opts}]
  (log/debugf "creating server %s" opts)
  (let [server (Server. ^ThreadPool thread-pool ^Scheduler scheduler ^ByteBufferPool buffer-pool)
        {handler-config   ::handler
         connector-config ::connector} opts]
    (.setConnectors server (into-array Connector
                                       (if connector-config
                                         [(connector server connector-config)]
                                         (map #(connector server %) connectors))))
    (.setHandler server ^Handler (handler server handler-config))
    (some->> error-handler (.setErrorHandler server))
    server))