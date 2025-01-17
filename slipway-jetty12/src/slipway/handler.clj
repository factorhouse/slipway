(ns slipway.handler
  (:require [clojure.tools.logging :as log]
            [slipway.handler.gzip :as gzip]
            [slipway.request :as request]
            [slipway.response :as response]
            [slipway.security :as security]
            [slipway.server :as server]
            [slipway.session :as session])
  (:import (org.eclipse.jetty.http HttpStatus)
           (org.eclipse.jetty.server Handler Handler$Wrapper Request Response)
           (org.eclipse.jetty.server.handler ContextHandler)
           (org.eclipse.jetty.util Callback)))

(defn context
  [request response]
  (merge (request/request-map request)
         (security/user request)
         {::request request}
         {::response response}))

(defn proxy-handler ^Handler
  [handler {:keys [idle-timeout input-buffer-size output-buffer-size max-text-message-size max-binary-message-size max-frame-size auto-fragment] :as opts}]
  (log/infof "websocket handler with: idle-timeout %s, input-buffer-size %s, output-buffer-size %s, max-text-message-size %s, max-binary-message-size %s, max-frame-size %s, auto-fragment %s"
             (or idle-timeout "default") (or input-buffer-size "default") (or output-buffer-size "default") (or max-text-message-size "default") (or max-binary-message-size "default")
             (or max-frame-size "default") (or auto-fragment "default"))
  (proxy [Handler$Wrapper] []
    (handle [^Request request ^Response response ^Callback cb]
      (try
        (->> (context request response)
             (handler)
             (response/update-response request response))
        (.succeeded cb)
        (catch Throwable ex
          (log/error ex "Unhandled exception processing HTTP request")
          (Response/writeError request response cb HttpStatus/INTERNAL_SERVER_ERROR_500 "slipway proxy error" ex)
          (.failed cb ex)))
      true)))

(comment
  #:slipway.handler{:context-path    "the root context path, default '/'"
                    :ws-path         "the path serving the websocket upgrade handler, default '/chsk'"
                    :null-path-info? "true if /path is not redirected to /path/, default true"})

(defmethod server/handler :default
  [ring-handler login-service {::keys [context-path null-path-info?] :or {context-path "/"} :as opts}]
  (log/infof "default server handler, context path %s, null-path-info? %s" context-path null-path-info?)
  (let [handler         (if login-service
                          (let [security-handler (security/handler login-service opts)
                                session-handler  (session/handler opts)]
                            (.setHandler security-handler (proxy-handler ring-handler opts))
                            (.setHandler session-handler security-handler)
                            session-handler)
                          (proxy-handler ring-handler opts))
        context-handler (doto (ContextHandler.)
                          (.setContextPath context-path)
                          (.setAllowNullPathInContext (not (false? null-path-info?)))
                          (.setHandler ^Handler handler)
                          ;(JettyWebSocketServletContainerInitializer/configure nil)
                          )]
    (some->> (gzip/handler opts) (.insertHandler context-handler))
    context-handler))