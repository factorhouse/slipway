(ns slipway.server
  "A Jetty9 server that conforms to the slipway API.

  Derived from:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9.clj
    * https://github.com/ring-clojure/ring/blob/master/ring-jetty-adapter/src/ring/adapter/jetty.clj"
  (:require [clojure.tools.logging :as log]
            [ring.util.servlet :as servlet]
            [slipway.auth :as auth]
            [slipway.impl.server :as server]
            [slipway.jetty9.auth]
            [slipway.jetty9.websockets :as jetty9.ws]
            [slipway.util :as util]
            [slipway.websockets :as ws])
  (:import (javax.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.server Handler Request Server)
           (org.eclipse.jetty.server.handler AbstractHandler ContextHandler HandlerList)))

(extend-protocol util/RequestMapDecoder
  HttpServletRequest
  (build-request-map [request]
    (servlet/build-request-map request)))

(defn handle*
  [target handler request request-map base-request response {:keys [auth]}]
  (try
    (auth/default-login-redirect auth target request)
    (let [request-map  (cond-> request-map
                         auth (assoc ::auth/user (auth/user base-request)))
          response-map (handler request-map)]
      (auth/maybe-logout auth base-request request-map)
      (when response-map
        (if (ws/upgrade-response? response-map)
          (servlet/update-servlet-response response {:status 406})
          (servlet/update-servlet-response response response-map))))
    (catch Throwable e
      (log/error e "unhandled exception processing HTTP request")
      (.sendError response 500 (.getMessage e)))
    (finally
      (.setHandled base-request true))))

(defn proxy-handler
  [handler options]
  (proxy [AbstractHandler] []
    (handle [target ^Request base-request ^HttpServletRequest request ^HttpServletResponse response]
      (try
        (let [request-map (util/build-request-map request)]
          (if (ws/upgrade-request? request-map)
            (.setHandled base-request false)                ;; Let the WS handler take care of ws-upgrade-requests
            (handle* target handler request request-map base-request response options)))
        (catch Throwable e
          (log/error e "unhandled exception processing HTTP request")
          (.sendError response 500 (.getMessage e))
          (.setHandled base-request true))))))

(defn run-jetty
  "Starts a Jetty server.
   See https://github.com/factorhouse/slipway#usage for list of options"
  ^Server [handler {:as   options
                    :keys [configurator join? auth gzip? gzip-content-types gzip-min-size http-forwarded? error-handler]
                    :or   {gzip?              true
                           gzip-content-types server/default-gzip-content-types
                           gzip-min-size      1024}}]
  (log/info "configuring Jetty9")
  (let [server           (server/create-server options)
        ring-app-handler (proxy-handler handler options)
        ws-handler       (doto (ContextHandler.)
                           (.setContextPath "/")
                           (.setAllowNullPathInfo true)
                           (.setHandler (jetty9.ws/proxy-ws-handler handler options)))
        contexts         (doto (HandlerList.)
                           (.setHandlers
                            (into-array Handler [ring-app-handler ws-handler])))]
    (.setHandler server contexts)
    (when configurator (configurator server))
    (when http-forwarded? (server/add-forward-request-customizer server))
    (when gzip? (server/enable-gzip-compression server gzip-content-types gzip-min-size))
    (when error-handler (.setErrorHandler server error-handler))
    (when auth (auth/configure server auth))
    (.start server)
    (when join? (.join server))
    server))

(comment
  (def handler
    (fn [req]
      (if (ws/upgrade-request? req)
        (ws/upgrade-response
         {:on-connect (fn [_] (prn "Hello world"))})
        {:status 200 :body "Hello world!"})))

  (run-jetty handler {:port 5005 :join? false}))