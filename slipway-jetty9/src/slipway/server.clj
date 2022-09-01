(ns slipway.server
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

(defn request-map
  [^Request base-request ^HttpServletRequest request]
  (merge (servlet/build-request-map request)
         (auth/user base-request)
         {::base-request base-request}))

(defn handle-request
  [handler request-map base-request response]
  (try
    (let [response-map (handler request-map)]
      (when response-map
        (if (common.ws/upgrade-response? response-map)
          (servlet/update-servlet-response response {:status 406})
          (servlet/update-servlet-response response response-map))))
    (catch Throwable e
      (log/error e "unhandled exception processing HTTP request")
      (.sendError response 500 (.getMessage e)))
    (finally
      (.setHandled base-request true))))

(defn handler
  [handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request ^HttpServletRequest request ^HttpServletResponse response]
      (try
        (let [request-map (request-map base-request request)]
          (if (common.ws/upgrade-request? request-map)
            (.setHandled base-request false)
            (handle-request handler request-map base-request response)))
        (catch Throwable e
          (log/error e "unhandled exception processing HTTP request")
          (.sendError response 500 (.getMessage e))
          (.setHandled base-request true))))))

(defn start ^Server
  [ring-handler {:keys [join? auth] :as opts}]
  (log/info "start slipway > Jetty 9")
  (let [server (common.server/create-server opts)]
    (.setHandler server (doto (HandlerList.)
                          (.setHandlers (into-array Handler [(handler ring-handler) (ws/handler ring-handler opts)]))))
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

(defn stop
  [^Server server]
  (.stop server))