(ns slipway.response
  (:require [ring.core.protocols :as protocols])
  (:import (org.eclipse.jetty.http HttpFields$Mutable)
           (org.eclipse.jetty.server Request Response)))

(def websocket-listener ::websocket-listener)

(defn upgrade?
  [{:keys [status ::websocket-listener]}]
  (and (= 101 status) websocket-listener))

(defn upgrade
  [ws-listener]
  {:status              101
   :headers             {"Connection" "Upgrade"
                         "Upgrade"    "Websocket"}
   ::websocket-listener ws-listener})

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
      ;; TODO, consider https://github.com/sunng87/ring-jetty9-adapter/issues/122
      (protocols/write-body-to-stream body response-map output-stream))))