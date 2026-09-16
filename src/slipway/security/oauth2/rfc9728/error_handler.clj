(ns slipway.security.oauth2.rfc9728.error-handler
  (:gen-class
   :name slipway.security.oauth2.RFC9728ErrorHandler
   :extends org.eclipse.jetty.server.handler.ErrorHandler
   :state state
   :init init
   :post-init post-init
   :constructors {[java.lang.Boolean]                  []
                  [java.lang.String java.lang.Boolean] []}
   :exposes-methods {generateAcceptableResponse superGenerateAcceptableResponse}
   :methods []
   :prefix "-")
  (:require [clojure.tools.logging :as log]
            [slipway.security.oauth2.rfc9728 :as rfc9728])
  (:import (java.nio.charset StandardCharsets)
           (org.eclipse.jetty.http MimeTypes$Type)
           (org.eclipse.jetty.server Request Response)
           (org.eclipse.jetty.util Callback)))

(defn -init
  ([show-causes?]
   [[] {:show-causes? show-causes?}])
  ([resource show-causes?]
   [[] {:resource      resource
        :show-causes? show-causes?}]))

(defn -post-init
  ([this show-causes?]
   (-post-init this nil show-causes?))
  ([this resource show-causes?]
   ;; likely not needed here, but fine to keep the ErrorHandler internally consistent
   (.setDefaultResponseMimeType this (.asString MimeTypes$Type/APPLICATION_JSON))
   (when resource
     (log/debugf "oauth2 resource set to %s" resource))
   (when show-causes?
     (log/debug "show-causes? true")
     (.setShowCauses this true))))

(defn -generateResponse
  [this ^Request request ^Response response code message cause ^Callback callback]

  ;; iff 401/UNAUTHORIZED then set the www-authenticate header
  (when (= 401 code)
    (let [resource (:resource (.state this))]
      (-> (.getHeaders response)
          (.put "WWW-Authenticate"
                ^String (rfc9728/metadata-url-header-value (or resource (rfc9728/resource-url request)))))))

  ;; then move directly to Application/JSON + UTF8 acceptable response
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