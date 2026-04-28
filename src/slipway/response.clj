(ns slipway.response
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
    (when status
      (.setStatus response status))
    (set-headers response headers)
    ;; Note: ring.core.protocols/StreamableResponseBody not supported as a body format
    ;; See: https://github.com/ring-clojure/ring/issues/491
    (protocols/write-body-to-stream body response-map (Response/asBufferedOutputStream request response))))