(ns slipway.response
  "This ns modelled on response functions in ring.util.servlet, translated to Jetty responses"
  (:require [ring.core.protocols :as protocols])
  (:import (org.eclipse.jetty.http HttpFields$Mutable)
           (org.eclipse.jetty.server Request Response)))

(defn set-headers
  [^Response response headers]
  (let [^HttpFields$Mutable jetty-headers (.getHeaders response)]
    (doseq [[key val-or-vals] headers]
      (if (string? val-or-vals)
        (.add jetty-headers ^String key ^String val-or-vals)
        (doseq [val val-or-vals]
          (.add jetty-headers ^String key ^String val))))))

(defn update-response
  "Update the Jetty response from a ring-like response map"
  [^Request request ^Response response response-map]
  (let [{:keys [status headers body]} response-map]
    (when (nil? response) (throw (NullPointerException. "Response is nil")))
    (when (nil? response-map) (throw (NullPointerException. "Response map is nil")))
    (when status
      (.setStatus response status))
    (set-headers response headers)
    (let [output-stream (Response/asBufferedOutputStream request response)]
      (protocols/write-body-to-stream body response-map output-stream))))