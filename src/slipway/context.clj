(ns slipway.context
  (:require [clojure.tools.logging :as log]
            [slipway.handler.compression :as compression]
            [slipway.security :as security]
            [slipway.server :as server]
            [slipway.session :as session]
            [slipway.websockets :as websockets])
  (:import (org.eclipse.jetty.security SecurityHandler)
           (org.eclipse.jetty.server Handler Server)
           (org.eclipse.jetty.server.handler ContextHandler ContextHandlerCollection)
           (slipway.handler SyncHandler)))

(defn handler
  [^Server server {::keys [ring-handler context-path null-path-info? virtual-hosts]
                   :or    {context-path "/"}
                   :as    opts}]
  (log/debugf "creating context-handler, context-path %s, null-path-info? %s" context-path null-path-info?)
  (let [context-handler (let [ctx (ContextHandler.)]
                          (.setContextPath ctx context-path)
                          (.setAllowNullPathInContext ctx (not (false? null-path-info?)))
                          (some->> virtual-hosts (.setVirtualHosts ctx))
                          ctx)
        app-handler     (if-let [ws-handler (websockets/handler server context-handler ring-handler opts)]
                          (doto ws-handler (.setHandler (SyncHandler. ring-handler (::websockets/path-spec opts))))
                          (SyncHandler. ring-handler nil))
        auth-handler    (when-let [^SecurityHandler security-handler (security/handler opts)]
                          (let [session-handler (session/handler opts)]
                            (.setHandler security-handler ^Handler app-handler)
                            (.setHandler session-handler security-handler)
                            session-handler))
        handler         (if-let [compression-handler (compression/handler opts)]
                          (doto compression-handler (.setHandler (or auth-handler app-handler)))
                          (or auth-handler app-handler))]
    (.setHandler context-handler ^Handler handler)
    context-handler))

(comment
  #:slipway.context{:ring-handler    "the ring-handler descendant of this context-handler"
                    :context-path    "the root context path, default '/'"
                    :null-path-info? "true if /path is not redirected to /path/, default true"
                    :virtual-hosts   "a list of ^String virtual hosts for the context"
                    :handlers        "an (optional) sequence of [#:slipway.context] for a ContextHandlerCollection"})

(defmethod server/handler :default
  [^Server server {::keys [handlers]
                   :as    opts}]
  (if handlers
    (ContextHandlerCollection. (map handler handlers))
    (handler server opts)))