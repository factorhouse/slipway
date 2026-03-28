(ns slipway.handler.sync-handler
  (:require [clojure.tools.logging :as log]
            [slipway.request :as request]
            [slipway.response :as response])
  (:import (org.eclipse.jetty.http HttpStatus)
           (org.eclipse.jetty.http.pathmap PathSpec)
           (org.eclipse.jetty.server Request Response)
           (org.eclipse.jetty.util Callback)
           (slipway.handler SyncHandler))
  (:gen-class
   :name slipway.handler.SyncHandler
   :extends org.eclipse.jetty.server.Handler$Abstract
   :state state
   :init init
   :constructors {[clojure.lang.IFn java.lang.String] []}
   :prefix "-"))

(defn -init
  [handler ws-path-spec]
  [[] [handler (some-> ws-path-spec PathSpec/from)]])

(defn -handle
  "Synchronous override for `Handler.Abstract/handle`"
  [^SyncHandler this ^Request request ^Response response ^Callback cb]
  (let [[handler ^PathSpec ws-path-spec] (.state this)]
    (try
      (let [response-map (handler (request/request-map request response))]
        (if (some-> ws-path-spec (.matches (Request/getPathInContext request)))
          (response/update-response request response {:status 400 :body "Bad Request"})
          (response/update-response request response response-map)))
      (.succeeded cb)
      (catch Throwable ex
        (log/error ex "Unhandled exception processing HTTP request")
        (Response/writeError request response cb HttpStatus/INTERNAL_SERVER_ERROR_500 "slipway proxy error" ex)
        (.failed cb ex)))
    true))
