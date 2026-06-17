(ns slipway.context
  (:require [clojure.tools.logging :as log]
            [slipway.compression :as compression]
            [slipway.security :as security]
            [slipway.server :as server]
            [slipway.session :as session]
            [slipway.websockets :as websockets])
  (:import (org.eclipse.jetty.security SecurityHandler)
           (org.eclipse.jetty.server Handler Server)
           (org.eclipse.jetty.server.handler ContextHandler ContextHandlerCollection)
           (slipway SyncHandler)))

(defn app-handler
  [ring-handler opts]
  (SyncHandler. ring-handler (websockets/path-spec opts)))

(defn wrap-websockets
  [handler context-handler server ring-handler opts]
  (if-let [ws-handler (websockets/handler server context-handler ring-handler opts)]
    (doto ws-handler (.setHandler handler))
    handler))

(defn wrap-auth
  [handler opts]
  (if-let [^SecurityHandler security-handler (security/handler opts)]
    (let [session-handler (session/handler opts)]
      (.setHandler security-handler ^Handler app-handler)
      (.setHandler session-handler security-handler)
      session-handler)
    handler))

(defn wrap-compression
  [handler opts]
  (if-let [compression-handler (compression/handler opts)]
    (doto compression-handler (.setHandler handler))
    handler))

(defn base-handler
  [{::keys [context-path null-path-info? virtual-hosts error-handler]
    :or    {context-path "/"}}]
  (log/debugf "creating context-handler, context-path %s, null-path-info? %s" context-path null-path-info?)
  (let [context-handler (ContextHandler.)]
    (.setContextPath context-handler context-path)
    (.setAllowNullPathInContext context-handler (not (false? null-path-info?)))
    (some->> virtual-hosts (.setVirtualHosts context-handler))
    (some->> error-handler (.setErrorHandler context-handler))
    context-handler))

(defn handler
  "Request routing is handled in the following order:
     -> Request
     -> ContextHandler
     -> CompressionHandler (optional)
     -> SessionHandler (optional)
     -> SecurityHandler (optional)
     -> WebsocketHandler (optional)
     -> SyncHandler
     -> ring-handler"
  [^Server server {::keys [ring-handler] :as opts}]
  (let [context-handler     (base-handler opts)
        application-handler (-> (app-handler ring-handler opts)
                                (wrap-websockets context-handler server ring-handler opts)
                                (wrap-auth opts)
                                (wrap-compression opts))]
    (.setHandler context-handler ^Handler application-handler)
    context-handler))

(comment
  #:slipway.context{:ring-handler    "the ring-handler descendant of this context-handler"
                    :context-path    "the root context path, default '/'"
                    :null-path-info? "true if /path is not redirected to /path/, default true"
                    :virtual-hosts   "a list of ^String virtual hosts for the context"
                    :error-handler   "the error-handler used by this context-handler for context level errors"
                    :handlers        "an (optional) sequence of [#:slipway.context] for a ContextHandlerCollection"})

(defmethod server/handler :default
  [^Server server {::keys [handlers] :as opts}]
  (if handlers
    (ContextHandlerCollection. (map (partial handler server) handlers))
    (handler server opts)))