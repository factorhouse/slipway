(ns slipway.handler
  (:require [clojure.tools.logging :as log]
            [slipway.handler.compression :as compression]
            [slipway.security :as security]
            [slipway.server :as server]
            [slipway.session :as session])
  (:import (org.eclipse.jetty.server Handler)
           (org.eclipse.jetty.server.handler ContextHandler)
           (slipway.handler SyncHandler)))

(comment
  #:slipway.handler{:context-path    "the root context path, default '/'"
                    :ws-path         "the path serving the websocket upgrade handler, default '/chsk'"
                    :null-path-info? "true if /path is not redirected to /path/, default true"})

(defmethod server/handler :default
  [ring-handler login-service {::keys [context-path null-path-info?] :or {context-path "/"} :as opts}]
  (log/debugf "creating default server handler, context path %s, null-path-info? %s" context-path null-path-info?)
  (let [proxy-handler   (SyncHandler. ring-handler opts)
        handler         (if login-service
                          (let [security-handler (security/handler login-service opts)
                                session-handler  (session/handler opts)]
                            (.setHandler security-handler proxy-handler)
                            (.setHandler session-handler security-handler)
                            session-handler)
                          proxy-handler)
        context-handler (doto (ContextHandler.)
                          (.setContextPath context-path)
                          (.setAllowNullPathInContext (not (false? null-path-info?)))
                          (.setHandler ^Handler handler))]
    (some->> (compression/handler opts) (.insertHandler context-handler))
    context-handler))