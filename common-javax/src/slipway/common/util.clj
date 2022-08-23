(ns slipway.common.util
  (:require [clojure.string :as string]
            [ring.util.servlet :as servlet])
  (:import (java.util Locale)
           (javax.servlet.http HttpServletRequest)))

(defprotocol RequestMapDecoder
  (build-request-map [r]))

(defn get-headers
  "Creates a name/value map of all the request headers."
  [^HttpServletRequest request]
  (into {} (map (fn [^String name]
                  [(.toLowerCase name Locale/ENGLISH)
                   (->> (.getHeaders request name)
                        (enumeration-seq)
                        (string/join ","))]))
        (enumeration-seq (.getHeaderNames request))))

(defn get-client-cert
  "Returns the SSL client certificate of the request, if one exists."
  [^HttpServletRequest request]
  (first (.getAttribute request "javax.servlet.request.X509Certificate")))

(defn updgrade-servlet-request-map
  [request]
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

(defn update-servlet-response
  [response response-map]
  (servlet/update-servlet-response response response-map))

(extend-protocol RequestMapDecoder
  HttpServletRequest
  (build-request-map [request]
    (servlet/build-request-map request)))