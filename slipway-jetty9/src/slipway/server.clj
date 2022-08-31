(ns slipway.server
  "A Jetty9 server that conforms to the slipway API, inspired by:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9.clj
    * https://github.com/ring-clojure/ring/blob/master/ring-jetty-adapter/src/ring/adapter/jetty.clj"
  (:require [clojure.tools.logging :as log]
            [slipway.auth :as auth]
            [slipway.common.server :as common.server]
            [slipway.common.websockets :as common.ws]
            [slipway.servlet :as servlet]
            [slipway.websockets :as ws])
  (:import (javax.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.server Handler Request Server)
           (org.eclipse.jetty.server.handler AbstractHandler ContextHandler HandlerList)))

;(defn uri-without-chsk
;  [url]
;  (subs url 0 (- (count url) 4)))
;
;;; TODO: consider configurable 'chsk' endpoint for websockets as we're assuming our normal setup here
;(defn safe-login-redirect
;  "With dual http/ws handlers it is possible that the websocket initialization request to {..}/chsk trigers a login
;   redirection and we don't want to post-login http redirect to {..}/chsk. Dropping back to {..}/ is better"
;  [^Request request]
;  (when-let [^String post-login-uri (.getAttribute (.getSession request) FormAuthenticator/__J_URI)]
;    (when (.endsWith post-login-uri "/chsk")
;      (let [new-uri (uri-without-chsk post-login-uri)]
;        (log/infof "avoiding {..}/chsk post-login, setting post-login uri to %s" new-uri)
;        (.setAttribute (.getSession request) FormAuthenticator/__J_URI new-uri)))))

(defn handle-http
  [handler request-map base-request response]
  (try
    (let [request-map  (merge request-map
                              (auth/credentials base-request)
                              {::request base-request})
          response-map (handler request-map)]
      (when response-map
        (if (common.ws/upgrade-response? response-map)
          (servlet/update-servlet-response response {:status 406})
          (servlet/update-servlet-response response response-map))))
    (catch Throwable e
      (log/error e "unhandled exception processing HTTP request")
      (.sendError response 500 (.getMessage e)))
    (finally
      (.setHandled base-request true))))

(defn proxy-handler
  [handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request ^HttpServletRequest request ^HttpServletResponse response]
      (try
        (let [request-map (servlet/build-request-map request)]
          (if (common.ws/upgrade-request? request-map)
            (.setHandled base-request false)
            (handle-http handler request-map base-request response)))
        (catch Throwable e
          (log/error e "unhandled exception processing HTTP request")
          (.sendError response 500 (.getMessage e))
          (.setHandled base-request true))))))

(defn start-jetty
  "Starts a Jetty server.
   See https://github.com/factorhouse/slipway#usage for list of options"
  ^Server [handler {:keys [join? auth] :as opts}]
  (log/info "configuring Jetty9")
  (let [server (common.server/create-server opts)]
    (.setHandler server (doto (HandlerList.)
                          (.setHandlers (into-array Handler [(proxy-handler handler) (ws/proxy-ws-handler handler opts)]))))
    (when auth (auth/configure server auth))
    (let [handler (.getHandler server)]
      (.setHandler server (doto (ContextHandler.)
                            (.setContextPath "/")
                            (.setAllowNullPathInfo true)
                            (.setHandler handler))))
    (common.server/enable-gzip-compression server opts)
    (.start server)
    (when join? (.join server))
    server))

(defn stop-jetty
  [^Server server]
  (.stop server))