(ns slipway.server
  "A Jetty10 server that conforms to the slipway API, inspired by:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9.clj
    * https://github.com/ring-clojure/ring/blob/master/ring-jetty-adapter/src/ring/adapter/jetty.clj"
  (:require [clojure.tools.logging :as log]
            [slipway.auth :as auth]
            [slipway.common.server :as common.server]
            [slipway.common.websockets :as common.ws]
            [slipway.servlet :as servlet]
            [slipway.websockets :as ws])
  (:import (javax.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.server Request Server)
           (org.eclipse.jetty.servlet ServletContextHandler ServletHandler)
           (org.eclipse.jetty.websocket.server.config JettyWebSocketServletContainerInitializer)))

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

(defn start-jetty ^Server
  [handler {:keys [join? auth] :as opts}]
  (log/info "configuring Jetty10")
  (let [server (common.server/create-server opts)]
    (.setHandler server (-> (proxy-handler handler opts)))
    (when auth (auth/configure server auth))
    (let [handler (.getHandler server)]
      (.setHandler server (doto (ServletContextHandler.)
                            (.setContextPath "/")
                            (.setAllowNullPathInfo true)
                            (JettyWebSocketServletContainerInitializer/configure nil)
                            (.setHandler handler))))
    (common.server/enable-gzip-compression server opts)
    (.start server)
    (when join? (.join server))
    server))

(defn stop-jetty
  [^Server server]
  (.stop server))