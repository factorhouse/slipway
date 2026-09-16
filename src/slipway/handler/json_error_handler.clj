(ns slipway.handler.json-error-handler
  (:gen-class
   :name slipway.handler.JSONErrorHandler
   :extends org.eclipse.jetty.server.handler.ErrorHandler
   :state state
   :init init
   :post-init post-init
   :constructors {[java.lang.Boolean] []}
   :exposes-methods {generateAcceptableResponse superGenerateAcceptableResponse}
   :methods []
   :prefix "-")
  (:require [clojure.tools.logging :as log])
  (:import (java.nio.charset StandardCharsets)
           (org.eclipse.jetty.http MimeTypes$Type)
           (org.eclipse.jetty.server Request Response)
           (org.eclipse.jetty.util Callback)))

(defn -init
  [show-causes?]
  [[] {:show-causes? show-causes?}])

(defn -post-init
  [this show-causes?]
  ;; likely not needed here, but fine to keep the ErrorHandler internally consistent
  (.setDefaultResponseMimeType this (.asString MimeTypes$Type/APPLICATION_JSON))
  (when show-causes?
    (log/debug "show-causes? true")
    (.setShowCauses this true)))

(defn -generateResponse
  [this ^Request request ^Response response code message cause ^Callback callback]

  ;; move directly to Application/JSON + UTF8 acceptable response
  (.superGenerateAcceptableResponse this
                                    request
                                    response
                                    callback
                                    (.asString MimeTypes$Type/APPLICATION_JSON)
                                    [StandardCharsets/UTF_8]
                                    code
                                    message
                                    cause)

  ;; update the callback and return
  (.succeeded callback))