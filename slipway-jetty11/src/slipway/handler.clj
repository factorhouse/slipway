(ns slipway.handler
  (:require [clojure.tools.logging :as log]
            [slipway.authz :as authz]
            [slipway.common.websockets :as common.ws]
            [slipway.server :as server]
            [slipway.servlet :as servlet]
            [slipway.session :as session]
            [slipway.websockets :as ws])
  (:import (jakarta.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.server Request)
           (org.eclipse.jetty.servlet ServletContextHandler ServletHandler)
           (org.eclipse.jetty.websocket.server.config JettyWebSocketServletContainerInitializer)))

(defn request-map
  [^Request base-request ^HttpServletRequest request]
  (merge (servlet/build-request-map request)
         (authz/user base-request)
         {::base-request base-request}))

(defn proxy-handler
  [handler opts]
  (proxy [ServletHandler] []
    (doHandle [_ ^Request base-request ^HttpServletRequest request ^HttpServletResponse response]
      (try
        (let [request-map  (request-map base-request request)
              response-map (handler request-map)]
          (if (and (common.ws/upgrade-request? request-map) (common.ws/upgrade-response? response-map))
            (when-not (ws/upgrade-websocket request response (:ws response-map) opts)
              (servlet/update-servlet-response response {:status 400 :body "Bad Request"}))
            (servlet/update-servlet-response response response-map)))
        (catch Throwable ex
          (log/error ex "Unhandled exception processing HTTP request")
          (.sendError response 500 (.getMessage ex)))
        (finally
          (.setHandled base-request true))))))

(comment
  #:slipway.handler {:context-path    "the root context path, default '/'"
                     :ws-path         "the path serving the websocket upgrade handler, default '/chsk'"
                     :null-path-info? "true if /path is not redirected to /path/, default true"})

(defmethod server/handler :default
  [ring-handler login-service {::keys [context-path null-path-info?] :or {context-path "/"} :as opts}]
  (log/info "using Jetty 11, default server handler")
  (let [context (doto (ServletContextHandler.)
                  (.setContextPath context-path)
                  (.setAllowNullPathInfo (not (false? null-path-info?)))
                  (.setServletHandler (proxy-handler ring-handler opts))
                  (JettyWebSocketServletContainerInitializer/configure nil))]
    (when login-service
      (.setSecurityHandler context (authz/handler login-service opts))
      (.setSessionHandler context (session/handler opts)))
    (some->> (server/gzip-handler opts) (.insertHandler context))
    context))