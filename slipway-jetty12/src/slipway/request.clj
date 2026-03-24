(ns slipway.request
  (:require [clojure.string :as string]
            [slipway.security :as security])
  (:import (java.util Locale)
           (org.eclipse.jetty.http HttpField HttpHeader HttpURI ImmutableHttpFields)
           (org.eclipse.jetty.io EndPoint$SslSessionData)
           (org.eclipse.jetty.server Request)
           (org.eclipse.jetty.websocket.core.server ServerUpgradeRequest)))

(defprotocol Decoder
  (decode [r]))

(defn websocket-upgrade?
  [{:keys [headers]}]
  (let [connection (or (get headers "Connection") (get headers "connection"))
        upgrade    (or (get headers "Upgrade") (get headers "upgrade"))]
    (and (some? upgrade)
         (some? connection)
         (string/includes? (string/lower-case upgrade) "websocket")
         (string/includes? (string/lower-case connection) "upgrade"))))

(defn get-headers
  [^Request request]
  (reduce
   (fn [ret ^HttpField field]
     (assoc ret (.getLowerCaseName field) (.getValue field)))
   {}
   (.getHeaders request)))

(defn ssl-client-cert
  [^Request request]
  (some-> ^EndPoint$SslSessionData (.getAttribute request EndPoint$SslSessionData/ATTRIBUTE)
          (.peerCertificates)
          (first)))

(defn ring-like-map
  "Create a ring-like request map from a Jetty request"
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
     :character-encoding (some-> (Request/getCharset request) str)
     :ssl-client-cert    (ssl-client-cert request)
     :body               (Request/asInputStream request)}))

(extend-protocol Decoder

  Request
  (decode [request]
    (ring-like-map request))

  ServerUpgradeRequest
  (decode [request]
    (assoc (ring-like-map request)
           ::websocket-protocol-version (.getProtocolVersion request)
           ::websocket-subprotocols (.getSubProtocols request)
           ::websocket-extensions (.getExtensions request))))

(defn websocket-protocol-version
  [request-map]
  (::websocket-protocol-version request-map))

(defn websocket-protocol
  [request-map]
  (first (::websocket-subprotocols request-map)))

(defn websocket-extensions
  [request-map]
  (seq (::websocket-extensions request-map)))

(defn request-map
  [request response]
  (merge (decode request)
         (security/user request)
         {::request request}
         {::response response}))                            ;; TODO: consider slipway.response/response