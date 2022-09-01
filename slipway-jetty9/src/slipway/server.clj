(ns slipway.server
  (:require [clojure.tools.logging :as log]
            [slipway.auth :as auth]
            [slipway.common.server :as common.server]
            [slipway.common.websockets :as common.ws]
            [slipway.servlet :as servlet]
            [slipway.session :as session]
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

(defn handler-list
  [ring-handler opts]
  (HandlerList. (into-array Handler [(handler ring-handler) (ws/handler ring-handler opts)])))

(defn context-handler ^ContextHandler
  [ring-handler login-service {:keys [auth context-path null-path-info?] :or {context-path "/"} :as opts}]
  (log/info "slipway Jetty 9 > default handler")
  (let [context (doto (ContextHandler.)
                  (.setContextPath context-path)
                  (.setAllowNullPathInfo (not (false? null-path-info?)))
                  (.setHandler (handler-list ring-handler opts)))]
    (when login-service
      (.insertHandler context (auth/handler login-service auth))
      (.insertHandler context (session/handler auth)))
    (some->> (common.server/gzip-handler opts) (.insertHandler context))
    context))

(defn start ^Server
  [ring-handler {:keys [join? auth] :as opts}]
  (let [server        (common.server/create-server opts)
        login-service (some-> auth auth/login-service)]
    (.setHandler server (context-handler ring-handler login-service opts))
    (some->> login-service (.addBean server))
    (.start server)
    (when join? (.join server))
    server))

(defn stop
  [^Server server]
  (.stop server))