(ns slipway.common.util
  (:require [clojure.string :as string])
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