(ns slipway.server
  "A Jetty10 server that conforms to the slipway API.

  Derived from:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9.clj
    * https://github.com/ring-clojure/ring/blob/master/ring-jetty-adapter/src/ring/adapter/jetty.clj"
  (:require [clojure.tools.logging :as log]
            [ring.util.servlet :as servlet]
            [slipway.auth :as auth]
            [slipway.impl.server :as server]
            [slipway.jetty10.auth]
            [slipway.jetty10.websockets :as jetty10.ws]
            [slipway.util :as util]
            [slipway.websockets :as ws])
  (:import (javax.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.server Request Server)
           (org.eclipse.jetty.servlet ServletContextHandler ServletHandler)
           (org.eclipse.jetty.websocket.server.config JettyWebSocketServletContainerInitializer)))

(extend-protocol util/RequestMapDecoder
  HttpServletRequest
  (build-request-map [request]
    (servlet/build-request-map request)))

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
     (doHandle [target ^Request base-request ^HttpServletRequest request ^HttpServletResponse response]
       (try
         (when auth
           (auth/default-login-redirect target request auth))
         (let [auth-user    (when auth
                              (auth/user base-request))
               request-map  (cond-> (util/build-request-map request)
                              auth-user (assoc ::auth/user auth-user))
               response-map (handler request-map)]
           (when auth
             (auth/maybe-logout base-request auth))
           (when response-map
             (if (and (ws/upgrade-request? request-map) (ws/upgrade-response? response-map))
               (jetty10.ws/upgrade-websocket request response (:ws response-map) opts)
               (servlet/update-servlet-response response response-map))))
         (catch Throwable ex
           (log/error ex "Unhandled exception processing HTTP request")
           (.sendError response 500 (.getMessage ex)))
         (finally
           (.setHandled base-request true)))))))

(defn run-jetty
  "Starts a Jetty server.
   See https://github.com/operatr-io/slipway#usage for list of options"
  ^Server [handler {:as   options
                    :keys [configurator join? auth gzip? gzip-content-types gzip-min-size http-forwarded? error-handler]
                    :or   {gzip?              true
                           gzip-content-types server/default-gzip-content-types
                           gzip-min-size      1024}}]
  (log/info "configuring Jetty10")
  (let [server           (server/create-server options)
        ring-app-handler (proxy-handler handler options)]
    (.setHandler server ring-app-handler)
    (when configurator
      (configurator server))
    (when http-forwarded?
      (server/http-forwarded-configurator server))
    (when gzip?
      (server/gzip-configurator server gzip-content-types gzip-min-size))
    (when error-handler
      (.setErrorHandler server error-handler))
    (when auth
      (auth/configurator server auth))
    (.start server)
    (when join?
      (.join server))
    server))

(comment
  (def handler
    (fn [req]
      (if (ws/upgrade-request? req)
        (ws/upgrade-response
         {:on-connect (fn [_] (prn "Hello world"))})
        {:status 200 :body "Hello world!"})))

  (run-jetty handler {:port 5005 :join? false}))