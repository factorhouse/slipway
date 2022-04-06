(ns slipway.util
  "Utility functions.

  Derived from:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9.clj"
  (:require [clojure.string :as string])
  (:import (javax.servlet.http HttpServletRequest HttpServletResponse)
           (java.util Locale)))

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
  (into {} (map (fn [^String name]
                  [(.toLowerCase name Locale/ENGLISH)
                   (->> (.getHeaders request name)
                        (enumeration-seq)
                        (string/join ","))]))
        (enumeration-seq (.getHeaderNames request))))
