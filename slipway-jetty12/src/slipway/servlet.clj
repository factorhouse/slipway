(ns slipway.servlet
  (:require [clojure.string :as string]
            [ring.util.jakarta.servlet :as servlet]
            [slipway.session :as session])
  (:import (java.util Locale)
           (jakarta.servlet SessionTrackingMode)
           (jakarta.servlet.http HttpServletRequest HttpServletResponse)))

(defprotocol RequestMapDecoder
  (build-request-map [r]))

(extend-protocol RequestMapDecoder

  HttpServletRequest
  (build-request-map [request]
    (servlet/build-request-map request)))

(defmethod session/tracking-mode :cookie
  [_]
  SessionTrackingMode/COOKIE)

(defmethod session/tracking-mode :url
  [_]
  SessionTrackingMode/URL)

(defmethod session/tracking-mode :ssl
  [_]
  SessionTrackingMode/SSL)

(defn get-headers
  "Creates a name/value map of all the request headers.
   ring.util.servlet/get-headers is -private, so we copy here"
  [^HttpServletRequest request]
  (reduce
   (fn [headers ^String name]
     (assoc headers
            (.toLowerCase name Locale/ENGLISH)
            (->> (.getHeaders request name)
                 (enumeration-seq)
                 (string/join ","))))
   {}
   (enumeration-seq (.getHeaderNames request))))

(defn get-client-cert
  "Returns the SSL client certificate of the request, if one exists.
   ring.util.servlet/get-client-cert is -private, so we copy here"
  [^HttpServletRequest request]
  (first (.getAttribute request "javax.servlet.request.X509Certificate")))

(defn update-servlet-response
  [^HttpServletResponse response response-map]
  (servlet/update-servlet-response response response-map))

(defn updgrade-servlet-request-map
  [^HttpServletRequest request]
  {:server-port     (.getServerPort request)
   :server-name     (.getServerName request)
   :remote-addr     (.getRemoteAddr request)
   :uri             (.getRequestURI request)
   :query-string    (.getQueryString request)
   :scheme          (keyword (.getScheme request))
   :request-method  (keyword (.toLowerCase (.getMethod request) Locale/ENGLISH))
   :protocol        (.getProtocol request)
   :headers         (get-headers request)
   :ssl-client-cert (get-client-cert request)})

(defn get-context
  [^HttpServletRequest req]
  (.getServletContext req))

(defn send-error
  [^HttpServletResponse response code message]
  (.sendError response code message))
