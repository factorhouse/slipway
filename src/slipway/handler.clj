(ns slipway.handler
  (:require [clojure.tools.logging :as log]
            [slipway.handler.compression :as compression]
            [slipway.security :as security]
            [slipway.server :as server]
            [slipway.session :as session]
            [slipway.websockets :as websockets])
  (:import (org.eclipse.jetty.security SecurityHandler)
           (org.eclipse.jetty.server Handler Server)
           (org.eclipse.jetty.server.handler ContextHandler)
           (slipway.handler SyncHandler)))

(comment
  #:slipway.handler{:context-path    "the root context path, default '/'"
                    :null-path-info? "true if /path is not redirected to /path/, default true"})

(defmethod server/handler :default
  [^Server server ring-handler {::keys [context-path null-path-info?]
                                :or    {context-path "/"}
                                :as    opts}]
  (log/debugf "creating default server context-handler, context path %s, null-path-info? %s" context-path null-path-info?)
  (let [context-handler (doto (ContextHandler.)
                          (.setContextPath context-path)
                          (.setAllowNullPathInContext (not (false? null-path-info?))))
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