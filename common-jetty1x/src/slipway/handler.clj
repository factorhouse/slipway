(ns slipway.handler
  (:require [clojure.tools.logging :as log]
            [slipway.auth :as auth]
            [slipway.common.websockets :as common.ws]
            [slipway.handler.gzip :as gzip]
            [slipway.server :as server]
            [slipway.servlet :as servlet]
            [slipway.session :as session]
            [slipway.websockets :as ws])
  (:import (org.eclipse.jetty.server Request)
           (org.eclipse.jetty.servlet ServletContextHandler ServletHandler)
           (org.eclipse.jetty.websocket.server.config JettyWebSocketServletContainerInitializer)))

(defn request-map
  [base-request request]
  (merge (servlet/build-request-map request)
         (auth/user base-request)
         {::base-request base-request}))

(defn proxy-handler
  [handler {::ws/keys [idle-timeout input-buffer-size output-buffer-size max-text-message-size max-binary-message-size max-frame-size auto-fragment] :as opts}]
  (log/infof "websocket handler with: idle-timeout %s, input-buffer-size %s, output-buffer-size %s, max-text-message-size %s, max-binary-message-size %s, max-frame-size %s, auto-fragment %s"
             (or idle-timeout "default") (or input-buffer-size "default") (or output-buffer-size "default") (or max-text-message-size "default") (or max-binary-message-size "default")
             (or max-frame-size "default") (or auto-fragment "default"))
  (proxy [ServletHandler] []
    (doHandle [_ ^Request base-request request response]
      (try
        (let [request-map  (request-map base-request request)
              response-map (handler request-map)]
          (if (and (common.ws/upgrade-request? request-map) (common.ws/upgrade-response? response-map))
            (when-not (ws/upgrade-websocket request response (:ws response-map) opts)
              (servlet/update-servlet-response response {:status 400 :body "Bad Request"}))
            (servlet/update-servlet-response response response-map)))
        (catch Throwable ex
          (log/error ex "Unhandled exception processing HTTP request")
          (servlet/send-error response 500 (.getMessage ex)))
        (finally
          (.setHandled base-request true))))))

(comment
  #:slipway.handler {:context-path    "the root context path, default '/'"
                     :ws-path         "the path serving the websocket upgrade handler, default '/chsk'"
                     :null-path-info? "true if /path is not redirected to /path/, default true"})

(defmethod server/handler :default
  [ring-handler login-service {::keys [context-path null-path-info?] :or {context-path "/"} :as opts}]
  (log/infof "default server handler, context path %s, null-path-info? %s" context-path null-path-info?)
  (let [context (doto (ServletContextHandler.)
                  (.setContextPath context-path)
                  (.setAllowNullPathInfo (not (false? null-path-info?)))
                  (.setServletHandler (proxy-handler ring-handler opts))
                  (JettyWebSocketServletContainerInitializer/configure nil))]
    (when login-service
      (.setSecurityHandler context (auth/handler login-service opts))
      (.setSessionHandler context (session/handler opts)))
    (some->> (gzip/handler opts) (.insertHandler context))
    context))