(ns slipway.context
  (:require [clojure.tools.logging :as log]
            [slipway.compression :as compression]
            [slipway.security :as security]
            [slipway.security.hash]
            [slipway.security.jaas]
            [slipway.security.openid]
            [slipway.security.openid.authorization-code-flow]
            [slipway.security.openid.client-credentials-flow]
            [slipway.server :as server]
            [slipway.session :as session]
            [slipway.websockets :as websockets])
  (:import (org.eclipse.jetty.server Handler Server)
           (org.eclipse.jetty.server.handler ContextHandler ContextHandlerCollection)
           (slipway.handler SyncHandler)))

(defn app-handler
  [ring-handler opts]
  (SyncHandler. ring-handler (websockets/path-spec opts)))

(defn wrap-websockets
  [handler context-handler server ring-handler opts]
  (if-let [ws-handler (websockets/handler server context-handler ring-handler opts)]
    (doto ws-handler (.setHandler handler))
    handler))

(defn wrap-session
  "In some circumstances a server may be configured without a SessionHandler
   For example a stateless API with OpenID client-credentials authorization flow"
  [^Handler security-handler opts]
  (if-let [session-handler (session/handler opts)]
    (doto session-handler (.setHandler security-handler))
    security-handler))

(defn wrap-security-and-session
  "We only wrap the session handler if you definitely have a security handler
   Anonymous sessions are not supported by this context handler"
  [handler opts]
  (if-let [security-handler (security/handler opts)]
    (do (.setHandler security-handler ^Handler handler)
        (wrap-session security-handler opts))
    handler))

(defn wrap-compression
  [handler opts]
  (if-let [compression-handler (compression/handler opts)]
    (doto compression-handler (.setHandler handler))
    handler))

(defn base-handler
  [{::keys [path null-path-info? virtual-hosts error-handler]
    :or    {path "/"}}]
  (log/debugf "creating context-handler, path %s, null-path-info? %s" path null-path-info?)
  (let [context-handler (ContextHandler.)]
    (.setContextPath context-handler path)
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
                                (wrap-security-and-session opts)
                                (wrap-compression opts))]
    (.setHandler context-handler ^Handler application-handler)
    context-handler))

(comment
  #:slipway.context{:path             "the context path, default '/'"
                    :ring-handler     "the ring-handler descendant of this context-handler"
                    :null-path-info?  "true if /path is not redirected to /path/, default true"
                    :virtual-hosts    "a list of ^String virtual hosts for the context"
                    :error-handler    "the error-handler used by this context-handler for context level errors"
                    :handlers         "a sequence of [:slipway.context], when used with ::server/handler of ::context/handler-collection"})

(defmethod server/handler :default
  [^Server server opts]
  (handler server opts))

(defmethod server/handler ::handler-collection
  [^Server server opts]
  (log/debugf "creating context-handler collection with [%s] handlers" (count opts))
  (ContextHandlerCollection. (into-array ContextHandler (map (partial handler server) (::handlers opts)))))