(ns slipway.server
  (:require [clojure.tools.logging :as log]
            [slipway.auth :as auth]
            [slipway.common.server :as common.server]
            [slipway.common.websockets :as common.ws]
            [slipway.servlet :as servlet]
            [slipway.session :as session]
            [slipway.websockets :as ws])
  (:import (jakarta.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.server Request Server)
           (org.eclipse.jetty.servlet ServletContextHandler ServletHandler)
           (org.eclipse.jetty.websocket.server.config JettyWebSocketServletContainerInitializer)))

(defn request-map
  [^Request base-request ^HttpServletRequest request]
  (merge (servlet/build-request-map request)
         (auth/user base-request)
         {::base-request base-request}))

(defn handler
  [handler opts]
  (proxy [ServletHandler] []
    (doHandle [_ ^Request base-request ^HttpServletRequest request ^HttpServletResponse response]
      (try
        (let [request-map  (request-map base-request request)
              response-map (handler request-map)]
          (when response-map
            (if (and (common.ws/upgrade-request? request-map) (common.ws/upgrade-response? response-map))
              (ws/upgrade-websocket request response (:ws response-map) opts)
              (servlet/update-servlet-response response response-map))))
        (catch Throwable ex
          (log/error ex "Unhandled exception processing HTTP request")
          (.sendError response 500 (.getMessage ex)))
        (finally
          (.setHandled base-request true))))))

(defn start ^Server
  [ring-handler {:keys [join? auth context-path null-path-info?] :or {context-path "/"} :as opts}]
  (log/info "start slipway > Jetty 11")
  (let [server  (common.server/create-server opts)
        context (doto (ServletContextHandler.)
                  (.setContextPath context-path)
                  (.setAllowNullPathInfo (not (false? null-path-info?)))
                  (.setServletHandler (handler ring-handler opts))
                  (JettyWebSocketServletContainerInitializer/configure nil))]
    (when-let [login-service (some-> auth auth/login-service)]
      (.setSecurityHandler context (auth/handler login-service auth))
      (.setSessionHandler context (session/handler auth))
      (.addBean server login-service))
    (some->> (common.server/gzip-handler opts) (.insertHandler context))
    (.setHandler server context)
    (.start server)
    (when join? (.join server))
    server))

(defn stop
  [^Server server]
  (.stop server))