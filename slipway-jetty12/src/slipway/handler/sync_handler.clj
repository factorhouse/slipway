(ns slipway.handler.sync-handler
  (:require [clojure.tools.logging :as log]
            [slipway.request :as request]
            [slipway.response :as response]
            [slipway.websockets :as ws])
  (:import (org.eclipse.jetty.http HttpStatus)
           (org.eclipse.jetty.server Request Response)
           (org.eclipse.jetty.util Callback)
           (slipway.handler SyncHandler))
  (:gen-class
   :name slipway.handler.SyncHandler
   :extends org.eclipse.jetty.server.Handler$Abstract
   :state state
   :init init
   :constructors {[clojure.lang.IFn
                   clojure.lang.IPersistentMap] []}
   :prefix "-"))

(defn -init
  [handler opts]
  [[] [handler opts]])

(defn -handle
  "Synchronous override for `Handler.Abstract/handle`"
  [^SyncHandler this ^Request request ^Response response ^Callback cb]
  (let [[handler opts] (.state this)]
    (try
      (let [request-map  (request/request-map request response)
            response-map (handler request-map)]
        (if (request/websocket-upgrade? request-map)
          (when-not (and (response/websocket-listener response-map)
                         (ws/upgrade-websocket request response cb request-map response-map opts))
            (response/update-response request response {:status 400 :body "Bad Request"}))
          (response/update-response request response response-map)))
      ;; TODO, consider succeeded here, when/how it applies and the true/false below
      (.succeeded cb)
      (catch Throwable ex
        (log/error ex "Unhandled exception processing HTTP request")
        (Response/writeError request response cb HttpStatus/INTERNAL_SERVER_ERROR_500 "slipway proxy error" ex)
        (.failed cb ex)))
    ;; TODO, consider https://github.com/sunng87/ring-jetty9-adapter/issues/122
    true))
