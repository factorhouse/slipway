(ns slipway.handler
  (:require [clojure.tools.logging :as log]
            [slipway.auth :as auth]
            [slipway.common.websockets :as common.ws]
            [slipway.servlet :as servlet]
            [slipway.session :as session]
            [slipway.websockets :as ws])
  (:import (javax.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.server Request)
           (org.eclipse.jetty.server.handler.gzip GzipHandler)
           (org.eclipse.jetty.servlet ServletContextHandler ServletHandler)
           (org.eclipse.jetty.websocket.server.config JettyWebSocketServletContainerInitializer)))

(defmulti root (fn [_ _ opts] (::root opts)))

(defn gzip-handler
  [{:keys [gzip? gzip-content-types gzip-min-size]}]
  (when (not (false? gzip?))
    (let [gzip-handler (GzipHandler.)]
      (log/info "enabling gzip compression")
      (when (seq gzip-content-types)
        (log/infof "setting gzip included mime types: %s" gzip-content-types)
        (.setIncludedMimeTypes gzip-handler (into-array String gzip-content-types)))
      (when gzip-min-size
        (log/infof "setting gzip min size: %s" gzip-min-size)
        (.setMinGzipSize gzip-min-size))
      gzip-handler)))

(defn request-map
  [^Request base-request ^HttpServletRequest request]
  (merge (servlet/build-request-map request)
         (auth/user base-request)
         {::base-request base-request}))

(defn proxy-handler
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

(defmethod root :default
  [ring-handler login-service {:keys [auth context-path null-path-info?] :or {context-path "/"} :as opts}]
  (log/info "slipway Jetty 11 > default handler")
  (let [context (doto (ServletContextHandler.)
                  (.setContextPath context-path)
                  (.setAllowNullPathInfo (not (false? null-path-info?)))
                  (.setServletHandler (proxy-handler ring-handler opts))
                  (JettyWebSocketServletContainerInitializer/configure nil))]
    (when login-service
      (.setSecurityHandler context (auth/handler login-service auth))
      (.setSessionHandler context (session/handler auth)))
    (some->> (gzip-handler opts) (.insertHandler context))
    context))