(ns slipway.handler
  (:require [clojure.tools.logging :as log]
            [slipway.authz :as authz]
            [slipway.common.websockets :as common.ws]
            [slipway.server :as server]
            [slipway.servlet :as servlet]
            [slipway.session :as session]
            [slipway.websockets :as ws])
  (:import (javax.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.security.authentication FormAuthenticator)
           (org.eclipse.jetty.server Handler Request)
           (org.eclipse.jetty.server.handler AbstractHandler ContextHandler HandlerList)))

(defn uri-without-chsk
  [url]
  (subs url 0 (- (count url) 4)))

;;; TODO: consider configurable 'chsk' endpoint for websockets as we're assuming our normal setup here
(defn safe-login-redirect
  "With dual http/ws handlers it is possible that the websocket initialization request to {..}/chsk trigers a login
   redirection and we don't want to post-login http redirect to {..}/chsk. Dropping back to {..}/ is better"
  [^Request request]
  (when-let [^String post-login-uri (some-> (.getSession request false) (.getAttribute FormAuthenticator/__J_URI))]
    (when (.endsWith post-login-uri "/chsk")
      (let [new-uri (uri-without-chsk post-login-uri)]
        (log/debugf "avoiding {..}/chsk post-login, setting post-login uri to %s" new-uri)
        (.setAttribute (.getSession request) FormAuthenticator/__J_URI new-uri)))))

(defn request-map
  [^Request base-request ^HttpServletRequest request]
  (merge (servlet/build-request-map request)
         (authz/user base-request)
         {::base-request base-request}))

(defn handler
  [handler]
  (proxy [AbstractHandler] []
    (handle [_ ^Request base-request ^HttpServletRequest request ^HttpServletResponse response]
      (try
        (safe-login-redirect request)
        (let [request-map (request-map base-request request)]
          (when-not (common.ws/upgrade-request? request-map)
            (let [response-map (handler request-map)]
              (servlet/update-servlet-response response response-map)
              (.setHandled base-request true))))
        (catch Throwable e
          (log/error e "unhandled exception processing HTTP request")
          (.sendError response 500 (.getMessage e))
          (.setHandled base-request true))))))

(defn handler-list
  [ring-handler opts]
  (HandlerList. (into-array Handler [(handler ring-handler) (ws/handler ring-handler opts)])))

(defmethod server/handler :default
  [ring-handler login-service {::keys [context-path null-path-info?] :or {context-path "/"} :as opts}]
  (log/info "slipway Jetty 9, default root handler")
  (let [context (doto (ContextHandler.)
                  (.setContextPath context-path)
                  (.setAllowNullPathInfo (not (false? null-path-info?)))
                  (.setHandler (handler-list ring-handler opts)))]
    (when login-service
      (.insertHandler context (authz/handler login-service opts))
      (.insertHandler context (session/handler opts)))
    (some->> (server/gzip-handler opts) (.insertHandler context))
    context))