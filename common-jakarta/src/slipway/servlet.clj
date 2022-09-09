(ns slipway.servlet
  "This ns pulls in most of ring.util.servlet that we would otherwise use, but are unable to do so because
   ring depends on the javax servlet API, rather than the jakarta one.
   Once a jakarta compatible version of ring is available this ns can model the javax/slipway.servlet ns."
  (:require [clojure.string :as string]
            [ring.core.protocols :as protocols]
            [slipway.session :as session])
  (:import (jakarta.servlet AsyncContext SessionTrackingMode)
           (jakarta.servlet.http HttpServletRequest HttpServletResponse)
           (java.io FilterOutputStream)
           (java.util Locale)
           (org.eclipse.jetty.websocket.server JettyWebSocketCreator JettyWebSocketServerContainer)))

(defprotocol RequestMapDecoder
  (build-request-map [r]))

(defn set-headers
  "Update a HttpServletResponse with a map of headers."
  [^HttpServletResponse response headers]
  (doseq [[key val-or-vals] headers]
    (if (string? val-or-vals)
      (.setHeader response key val-or-vals)
      (doseq [val val-or-vals]
        (.addHeader response key val))))
  ; Some headers must be set through specific methods
  (when-let [content-type (or (get headers "Content-Type") (get headers "content-type"))]
    (.setContentType response content-type)))

(defn get-headers
  "Creates a name/value map of all the request headers."
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

(defn get-content-length
  "Returns the content length, or nil if there is no content."
  [^HttpServletRequest request]
  (let [length (.getContentLength request)]
    (if (>= length 0) length)))

(defn get-client-cert
  "Returns the SSL client certificate of the request, if one exists."
  [^HttpServletRequest request]
  (first (.getAttribute request "jakarta.servlet.request.X509Certificate")))


(extend-protocol RequestMapDecoder
  HttpServletRequest
  (build-request-map [request]
    {:server-port        (.getServerPort request)
     :server-name        (.getServerName request)
     :remote-addr        (.getRemoteAddr request)
     :uri                (.getRequestURI request)
     :query-string       (.getQueryString request)
     :scheme             (keyword (.getScheme request))
     :request-method     (keyword (.toLowerCase (.getMethod request) Locale/ENGLISH))
     :protocol           (.getProtocol request)
     :headers            (get-headers request)
     :content-type       (.getContentType request)
     :content-length     (get-content-length request)
     :character-encoding (.getCharacterEncoding request)
     :ssl-client-cert    (get-client-cert request)
     :body               (.getInputStream request)}))

(defmethod session/tracking-mode :cookie
  [_]
  SessionTrackingMode/COOKIE)

(defmethod session/tracking-mode :url
  [_]
  SessionTrackingMode/URL)

(defmethod session/tracking-mode :ssl
  [_]
  SessionTrackingMode/SSL)

(defn- make-output-stream
  [^HttpServletResponse response ^AsyncContext context]
  (let [os (.getOutputStream response)]
    (if (nil? context)
      os
      (proxy [FilterOutputStream] [os]
        (write
          ([b] (.write os b))
          ([b off len] (.write os b off len)))
        (close []
          (.close os)
          (.complete context))))))

(defn update-servlet-response
  "Update the HttpServletResponse using a response map. Takes an optional AsyncContext."
  ([response response-map]
   (update-servlet-response response nil response-map))
  ([^HttpServletResponse response context response-map]
   (let [{:keys [status headers body]} response-map]
     (when (nil? response)
       (throw (NullPointerException. "HttpServletResponse is nil")))
     (when (nil? response-map)
       (throw (NullPointerException. "Response map is nil")))
     (when status
       (.setStatus response status))
     (set-headers response headers)
     (let [output-stream (make-output-stream response context)]
       (protocols/write-body-to-stream body response-map output-stream)))))

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

(defn upgrade-container
  [^JettyWebSocketServerContainer container ^JettyWebSocketCreator creator ^HttpServletRequest req ^HttpServletResponse res]
  (.upgrade container creator req res))

(defn send-error
  [^HttpServletResponse response code message]
  (.sendError response code message))