(ns slipway.websockets.adapter
  (:import (java.nio ByteBuffer)
           (org.eclipse.jetty.util Callback)
           (slipway.websockets SenteAdapter))
  (:gen-class
   :name slipway.websockets.SenteAdapter
   :extends org.eclipse.jetty.websocket.api.Session$Listener$AbstractAutoDemanding
   :state state
   :init init
   :constructors {[clojure.lang.IPersistentMap] []}
   :prefix "-"))

(defn -init
  [callbacks]
  [[] [callbacks]])

(defn -onWebSocketClose
  [^SenteAdapter this ^long status ^String _reason ^Callback cb]
  (let [{:keys [on-close]} (.state this)]
    (on-close (.getSession this) status)
    (when cb (cb))))

(defn -onWebSocketText
  [^SenteAdapter this ^String text]
  (let [{:keys [on-message]} (.state this)]
    (on-message (.getSession this) text)))

(defn -onWebSocketBinary
  [^SenteAdapter this ^ByteBuffer payload ^Callback cb]
  (let [{:keys [on-message]} (.state this)]
    (on-message (.getSession this) payload)
    (when cb (cb))))