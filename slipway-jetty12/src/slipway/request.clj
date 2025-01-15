(ns slipway.request
  "This ns modelled on request functions in ring.util.servlet, translated to Jetty requests"
  (:import (java.util Locale)
           (org.eclipse.jetty.http HttpField HttpHeader HttpURI ImmutableHttpFields MimeTypes MimeTypes$Type)
           (org.eclipse.jetty.io EndPoint$SslSessionData)
           (org.eclipse.jetty.server Request)))

(defn get-headers
  [^Request request]
  (reduce
   (fn [ret ^HttpField field]
     (assoc ret (.getLowerCaseName field) (.getValue field)))
   {}
   (.getHeaders request)))

(defn character-encoding
  "See: org.eclipse.jetty.server.Request/getCharacterEncoding"
  [^ImmutableHttpFields headers]
  (when-let [content-type (.get headers HttpHeader/CONTENT_TYPE)]
    (or (some-> ^MimeTypes$Type (.get MimeTypes/CACHE content-type) (.getCharset) str)
        (MimeTypes/getCharsetFromContentType content-type))))

(defn ssl-client-cert
  [^Request request]
  (some-> ^EndPoint$SslSessionData (.getAttribute request EndPoint$SslSessionData/ATTRIBUTE)
          (.peerCertificates)
          (first)))

(defn request-map
  [^Request request]
  (let [^HttpURI uri                 (.getHttpURI request)
        ^ImmutableHttpFields headers (.getHeaders request)]
    {:server-port        (Request/getServerPort request)
     :server-name        (Request/getServerName request)
     :remote-addr        (Request/getRemoteAddr request)
     :uri                (Request/getPathInContext request)
     :query-string       (.getQuery uri)
     :scheme             (keyword (.getScheme uri))
     :request-method     (keyword (.toLowerCase (.getMethod request) Locale/ENGLISH))
     :protocol           (.getProtocol (.getConnectionMetaData request))
     :headers            (get-headers request)
     :content-type       (.get headers HttpHeader/CONTENT_TYPE)
     :content-length     (some-> (.get headers HttpHeader/CONTENT_LENGTH) (Integer/valueOf))
     :character-encoding (character-encoding headers)
     :ssl-client-cert    (ssl-client-cert request)
     :body               (Request/asInputStream request)}))
