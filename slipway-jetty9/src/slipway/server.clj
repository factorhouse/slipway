(ns slipway.server
  "A Jetty9 server that conforms to the slipway API, inspired by:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9.clj
    * https://github.com/ring-clojure/ring/blob/master/ring-jetty-adapter/src/ring/adapter/jetty.clj"
  (:require [clojure.tools.logging :as log]
            [slipway.auth]
            [slipway.common.auth :as common.auth]
            [slipway.common.server :as common.server]
            [slipway.common.servlet :as common.servlet]
            [slipway.common.websockets :as common.ws]
            [slipway.websockets :as ws])
  (:import (javax.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.server Handler Request Server)
           (org.eclipse.jetty.server.handler AbstractHandler ContextHandler HandlerList)))

(defn handle*
  [handler request-map base-request response {:keys [auth]}]
  (try
    (let [request-map  (cond-> request-map
                         auth (assoc ::common.auth/user (common.auth/user base-request)))
          response-map (handler request-map)]
      (common.auth/maybe-logout auth base-request request-map)
      (when response-map
        (if (common.ws/upgrade-response? response-map)
          (common.servlet/update-servlet-response response {:status 406})
          (common.servlet/update-servlet-response response response-map))))
    (catch Throwable e
      (log/error e "unhandled exception processing HTTP request")
      (.sendError response 500 (.getMessage e)))
    (finally
      (.setHandled base-request true))))

(defn proxy-handler
  [handler options]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request ^HttpServletRequest request ^HttpServletResponse response]
      (try
        (let [request-map (common.servlet/build-request-map request)]
          (if (common.ws/upgrade-request? request-map)
            (.setHandled base-request false)                ;; Let the WS handler take care of ws-upgrade-requests
            (handle* handler request-map base-request response options)))
        (catch Throwable e
          (log/error e "unhandled exception processing HTTP request")
          (.sendError response 500 (.getMessage e))
          (.setHandled base-request true))))))

(defn start-jetty
  "Starts a Jetty server.
   See https://github.com/factorhouse/slipway#usage for list of options"
  ^Server [handler {:as   options
                    :keys [configurator join? auth gzip? gzip-content-types gzip-min-size http-forwarded? error-handler]
                    :or   {gzip?              true
                           gzip-content-types common.server/default-gzip-content-types
                           gzip-min-size      1024}}]
  (log/info "configuring Jetty9")
  (let [server           (common.server/create-server options)
        ring-app-handler (doto (ContextHandler.)
                           (.setContextPath "/")
                           (.setAllowNullPathInfo true)
                           (.setHandler (proxy-handler handler options)))
        ws-handler       (doto (ContextHandler.)
                           (.setContextPath "/")
                           (.setAllowNullPathInfo true)
                           (.setHandler (ws/proxy-ws-handler handler options)))
        contexts         (doto (HandlerList.)
                           (.setHandlers (into-array Handler [ring-app-handler ws-handler])))]
    (.setHandler server contexts)
    (when configurator (configurator server))
    (when http-forwarded? (common.server/add-forward-request-customizer server))
    (when gzip? (common.server/enable-gzip-compression server gzip-content-types gzip-min-size))
    (when error-handler (.setErrorHandler server error-handler))
    (when auth (common.auth/configure server auth))
    (.start server)
    (when join? (.join server))
    server))

(defn stop-jetty
  [^Server server]
  (.stop server))