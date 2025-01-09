(ns slipway.handler
  (:require [clojure.tools.logging :as log]

            [slipway.handler.gzip :as gzip]
            [slipway.security :as security]
            [slipway.server :as server]
            [slipway.servlet :as servlet]
            [slipway.session :as session]
            )
  (:import
    (org.eclipse.jetty.ee10.servlet ServletContextHandler ServletContextRequest ServletCoreRequest ServletHandler)
    (org.eclipse.jetty.util Callback)))

(defn request-map
  [request]
  (merge (servlet/build-request-map (.getServletApiRequest ^ServletContextRequest request))
         (security/user request)))

(defn proxy-handler
  [handler {:keys [idle-timeout input-buffer-size output-buffer-size max-text-message-size max-binary-message-size max-frame-size auto-fragment] :as opts}]
  (log/infof "websocket handler with: idle-timeout %s, input-buffer-size %s, output-buffer-size %s, max-text-message-size %s, max-binary-message-size %s, max-frame-size %s, auto-fragment %s"
             (or idle-timeout "default") (or input-buffer-size "default") (or output-buffer-size "default") (or max-text-message-size "default") (or max-binary-message-size "default")
             (or max-frame-size "default") (or auto-fragment "default"))
  (proxy [ServletHandler] []
    (handle [request response cb]
      (prn "request" request)
      (prn "response" response)
      (prn "cb" cb)
      (try
        (let [request-map  (request-map request)
              response-map (handler request-map)]
          (servlet/update-servlet-response response response-map))
        (catch Throwable ex
          (log/error ex "Unhandled exception processing HTTP request")
          (servlet/send-error response 500 (.getMessage ex)))
        (finally
          (.succeeded ^Callback cb))))))

(comment
  #:slipway.handler{:context-path    "the root context path, default '/'"
                    :ws-path         "the path serving the websocket upgrade handler, default '/chsk'"
                    :null-path-info? "true if /path is not redirected to /path/, default true"})

(defmethod server/handler :default
  [ring-handler login-service {::keys [context-path null-path-info?] :or {context-path "/"} :as opts}]
  (log/infof "default server handler, context path %s, null-path-info? %s" context-path null-path-info?)
  (let [context (doto (ServletContextHandler.)
                  (.setContextPath context-path)
                  ;(.setAllowNullPathInfo (not (false? null-path-info?)))
                  (.setServletHandler (proxy-handler ring-handler opts))
                  ;l(JettyWebSocketServletContainerInitializer/configure nil)
                  )]
    (when login-service
      (.setSecurityHandler context (security/handler login-service opts))
      (.setSessionHandler context (session/handler opts)))
    (some->> (gzip/handler opts) (.insertHandler context))
    context))