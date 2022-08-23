(ns slipway.server
  "A Jetty11 server that conforms to the slipway API, inspired by:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9.clj
    * https://github.com/ring-clojure/ring/blob/master/ring-jetty-adapter/src/ring/adapter/jetty.clj"
  (:require [clojure.tools.logging :as log]
            [slipway.auth]
            [slipway.common.auth :as common.auth]
            [slipway.common.server :as common.server]
            [slipway.common.servlet :as common.servlet]
            [slipway.common.websockets :as common.ws]
            [slipway.websockets :as ws])
  (:import (jakarta.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.server Request Server)
           (org.eclipse.jetty.servlet ServletContextHandler ServletHandler)
           (org.eclipse.jetty.websocket.server.config JettyWebSocketServletContainerInitializer)))

(defn wrap-proxy-handler
  [jetty-handler]
  (doto (ServletContextHandler.)
    (.setContextPath "/")
    (.setAllowNullPathInfo true)
    (JettyWebSocketServletContainerInitializer/configure nil)
    (.setServletHandler jetty-handler)))

(defn proxy-handler
  [handler {:keys [auth] :as opts}]
  (wrap-proxy-handler
   (proxy [ServletHandler] []
     (doHandle [_ ^Request base-request ^HttpServletRequest request ^HttpServletResponse response]
       (try
         (let [request-map  (cond-> (common.servlet/build-request-map request)
                              auth (assoc ::common.auth/user (common.auth/user base-request)))
               response-map (handler request-map)]
           (common.auth/maybe-logout auth base-request request-map)
           (when response-map
             (if (and (common.ws/upgrade-request? request-map) (common.ws/upgrade-response? response-map))
               (ws/upgrade-websocket request response (:ws response-map) opts)
               (common.servlet/update-servlet-response response response-map))))
         (catch Throwable ex
           (log/error ex "Unhandled exception processing HTTP request")
           (.sendError response 500 (.getMessage ex)))
         (finally
           (.setHandled base-request true)))))))

(defn run-jetty
  "Starts a Jetty server.
   See https://github.com/factorhouse/slipway#usage for list of options"
  ^Server [handler {:as   options
                    :keys [configurator join? auth gzip? gzip-content-types gzip-min-size http-forwarded? error-handler]
                    :or   {gzip?              true
                           gzip-content-types common.server/default-gzip-content-types
                           gzip-min-size      1024}}]
  (log/info "configuring Jetty11")
  (let [server (common.server/create-server options)]
    (.setHandler server (proxy-handler handler options))
    (when configurator (configurator server))
    (when http-forwarded? (common.server/add-forward-request-customizer server))
    (when gzip? (common.server/enable-gzip-compression server gzip-content-types gzip-min-size))
    (when error-handler (.setErrorHandler server error-handler))
    (when auth (common.auth/configure server auth))
    (.start server)
    (when join? (.join server))
    server))

(comment
  (def handler
    (fn [req]
      (if (common.ws/upgrade-request? req)
        (common.ws/upgrade-response
         {:on-connect (fn [_] (prn "Hello world"))})
        {:status 200 :body "Hello world!"})))

  (run-jetty handler {:port 5005 :join? false}))