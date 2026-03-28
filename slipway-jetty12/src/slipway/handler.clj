(ns slipway.handler
  (:require [clojure.tools.logging :as log]
            [slipway.handler.compression :as compression]
            [slipway.security :as security]
            [slipway.server :as server]
            [slipway.session :as session]
            [slipway.websockets :as websockets])
  (:import (org.eclipse.jetty.server Handler)
           (org.eclipse.jetty.server.handler ContextHandler)
           (slipway.handler SyncHandler)))

(comment
  #:slipway.handler{:context-path    "the root context path, default '/'"
                    :ws-path         "the path serving the websocket upgrade handler, default '/chsk'"
                    :null-path-info? "true if /path is not redirected to /path/, default true"})

(defmethod server/handler :default
  [server ring-handler login-service {::keys [context-path null-path-info?] :or {context-path "/"} :as opts}]
  (log/debugf "creating default server handler, context path %s, null-path-info? %s" context-path null-path-info?)
  (let [context-handler (doto (ContextHandler.)
                          (.setContextPath context-path)
                          (.setAllowNullPathInContext (not (false? null-path-info?))))
        app-handler     (if-let [ws-handler (websockets/handler server context-handler ring-handler opts)]
                          (doto ws-handler (.setHandler (SyncHandler. ring-handler (::websockets/path-spec opts))))
                          (SyncHandler. ring-handler nil))
        handler         (if login-service
                          (let [security-handler (security/handler login-service opts)
                                session-handler  (session/handler opts)]
                            (.addBean server login-service)
                            (.setHandler security-handler ^Handler app-handler)
                            (.setHandler session-handler security-handler)
                            session-handler)
                          app-handler)]
    (.setHandler context-handler ^Handler handler)
    (some->> (compression/handler opts) (.insertHandler context-handler))
    context-handler))