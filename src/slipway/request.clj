(ns slipway.request
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.user :as user])
  (:import (java.time Instant)
           (java.util Locale)
           (org.eclipse.jetty.http HttpField HttpHeader HttpURI ImmutableHttpFields)
           (org.eclipse.jetty.io EndPoint$SslSessionData)
           (org.eclipse.jetty.security AuthenticationState AuthenticationState$Succeeded)
           (org.eclipse.jetty.server Request Response)))

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

(defn authenticated-user
  [^Request request]
  (when-let [^AuthenticationState authentication-state (Request/getAuthenticationState request)]
    (when (instance? AuthenticationState$Succeeded authentication-state)
      (p/datafy authentication-state))))

(defn request-map
  [^Request request ^Response response]
  (merge (ring-like-map request)
         {::request       request
          ::response      response
          ::user/identity (authenticated-user request)}))

(defn user
  [request-map]
  (::user/identity request-map))

(defn user-type
  [request-map]
  (user/type (user request-map)))

(defn user-name
  [request-map]
  (user/name (user request-map)))

(defn user-roles
  [request-map]
  (user/roles (user request-map)))

(defn user-expired?
  ([request-map]
   (user/expired? (user request-map) (Instant/now)))
  ([request-map ^Instant at]
   (user/expired? (user request-map) at)))

(defn logout-user
  [{:keys [^Request slipway.request/request ^Response slipway.request/response] :as req}]
  (when request
    (try
      (log/debug "logout" (user-type req) (user-name req))
      (AuthenticationState/logout request response)
      (some-> (.getSession request false) (.invalidate))
      (catch Exception ex
        (log/error ex "logout error")))))