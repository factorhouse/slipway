(ns slipway.websockets
  (:require
   [clojure.tools.logging :as log]
   [slipway.common.websockets :as common.ws]
   [slipway.servlet :as servlet])
  (:import (clojure.lang IFn)
           (java.nio ByteBuffer)
           (org.eclipse.jetty.websocket.api RemoteEndpoint Session WebSocketAdapter WriteCallback)
           (org.eclipse.jetty.websocket.api.extensions ExtensionConfig)
           (org.eclipse.jetty.websocket.server WebSocketHandler)
           (org.eclipse.jetty.websocket.servlet ServletUpgradeRequest WebSocketCreator WebSocketServletFactory)))

(def no-op (constantly nil))

(extend-protocol servlet/RequestMapDecoder

  ServletUpgradeRequest
  (build-request-map [request]
    (assoc (-> (.getHttpServletRequest request) (servlet/updgrade-servlet-request-map))
           :websocket-subprotocols (into [] (.getSubProtocols request))
           :websocket-extensions (into [] (.getExtensions request)))))

(defn write-callback
  [{:keys [write-failed write-success]
    :or   {write-failed  no-op
           write-success no-op}}]
  (reify WriteCallback
    (writeFailed [_ throwable]
      (write-failed throwable))
    (writeSuccess [_]
      (write-success))))

(extend-protocol common.ws/WebSocketSend

  #_:clj-kondo/ignore
  (Class/forName "[B")
  (-send!
    ([ba ws]
     (common.ws/-send! (ByteBuffer/wrap ba) ws))
    ([ba ws callback]
     (common.ws/-send! (ByteBuffer/wrap ba) ws callback))))

(extend-protocol common.ws/WebSocketSend

  ByteBuffer
  (-send!
    ([bb ws]
     (-> ^WebSocketAdapter ws .getRemote (.sendBytes ^ByteBuffer bb)))
    ([bb ws callback]
     (-> ^WebSocketAdapter ws .getRemote (.sendBytes ^ByteBuffer bb ^WriteCallback (write-callback callback)))))

  String
  (-send!
    ([s ws]
     (-> ^WebSocketAdapter ws .getRemote (.sendString ^String s)))
    ([s ws callback]
     (-> ^WebSocketAdapter ws .getRemote (.sendString ^String s ^WriteCallback (write-callback callback)))))

  IFn
  (-send! [f ws]
    (-> ^WebSocketAdapter ws .getRemote f))

  Object
  (send!
    ([this ws]
     (-> ^WebSocketAdapter ws .getRemote
         (.sendString ^RemoteEndpoint (str this))))
    ([this ws callback]
     (-> ^WebSocketAdapter ws .getRemote
         (.sendString ^RemoteEndpoint (str this) ^WriteCallback (write-callback callback))))))

(extend-protocol common.ws/WebSocketPing

  #_:clj-kondo/ignore
  (Class/forName "[B")
  (-ping! [ba ws] (common.ws/-ping! (ByteBuffer/wrap ba) ws)))

(extend-protocol common.ws/WebSocketPing

  ByteBuffer
  (-ping! [bb ws] (-> ^WebSocketAdapter ws .getRemote (.sendPing ^ByteBuffer bb)))

  String
  (-ping! [s ws] (common.ws/-ping! (.getBytes ^String s "UTF-8") ws))

  Object
  (-ping! [o ws] (common.ws/-ping! (.getBytes (str o) "UTF-8") ws)))

(extend-protocol common.ws/WebSockets

  WebSocketAdapter
  (send!
    ([this msg]
     (common.ws/-send! msg this))
    ([this msg callback]
     (common.ws/-send! msg this callback)))
  (ping!
    ([this]
     (common.ws/-ping! (ByteBuffer/allocate 0) this))
    ([this msg]
     (common.ws/-ping! msg this)))
  (close!
    ([this]
     (.close (.getSession this)))
    ([this status-code reason]
     (.close (.getSession this) status-code reason)))
  (remote-addr [this]
    (.getRemoteAddress (.getSession this)))
  (idle-timeout! [this ms]
    (.setIdleTimeout (.getSession this) ms))
  (connected? [this]
    (. this (isConnected)))
  (req-of [this]
    (servlet/build-request-map (.getUpgradeResponse (.getSession this)))))

(defn proxy-ws-adapter
  [{:keys [on-connect on-error on-text on-close on-bytes]
    :or   {on-connect no-op
           on-error   no-op
           on-text    no-op
           on-close   no-op
           on-bytes   no-op}}]
  (proxy [WebSocketAdapter] []
    (onWebSocketConnect [^Session session]
      (let [^WebSocketAdapter this this]
        (proxy-super onWebSocketConnect session))
      (on-connect this))
    (onWebSocketError [^Throwable e]
      (on-error this e))
    (onWebSocketText [^String message]
      (on-text this message))
    (onWebSocketClose [statusCode ^String reason]
      (let [^WebSocketAdapter this this]
        (proxy-super onWebSocketClose statusCode reason))
      (on-close this statusCode reason))
    (onWebSocketBinary [^bytes payload offset len]
      (on-bytes this payload offset len))))

(defn reify-ws-creator
  [handler]
  (reify WebSocketCreator
    (createWebSocket [_ req resp]
      (let [req-map (servlet/build-request-map req)]
        (if (common.ws/upgrade-request? req-map)
          (let [resp-map (handler req-map)]
            (if (common.ws/upgrade-response? resp-map)
              (let [ws-results (:ws resp-map)]
                (when-let [sp (:subprotocol ws-results)]
                  (.setAcceptedSubProtocol resp sp))
                (when-let [exts (not-empty (:extensions ws-results))]
                  (.setExtensions resp (mapv #(ExtensionConfig. ^String %) exts)))
                (proxy-ws-adapter ws-results))
              ;; If we don't get a ws-response, send appropriate status code + error message
              (.sendError resp (:status resp-map 400) (str (:body resp-map "Bad Request")))))
          ;; This should be handled by the ring-app-handler handler, but be extra defensive anyway
          (.sendError resp 400 "Bad Request"))))))

(comment
  #:slipway.websockets{:idle-timeout            "max websocket idle time (in ms), default 500000"
                       :input-buffer-size       "max websocket input buffer size"
                       :max-text-message-size   "max websocket text message size"
                       :max-binary-message-size "max websocket binary message size"})

(defn handler
  [handler {::keys [idle-timeout input-buffer-size max-text-message-size max-binary-message-size]
            :or    {idle-timeout 500000}}]
  (log/infof "handler with: idle-timeout %s, input-buffer-size %s, max-text-message-size %s, max-binary-message-size %s"
             idle-timeout (or input-buffer-size "default") (or max-text-message-size "default") (or max-binary-message-size "default"))
  (proxy [WebSocketHandler] []
    (configure [^WebSocketServletFactory factory]
      (let [policy (.getPolicy factory)]
        (some->> idle-timeout (.setIdleTimeout policy))
        (some->> input-buffer-size (.setInputBufferSize policy))
        (some->> max-binary-message-size (.setMaxBinaryMessageSize policy))
        (some->> max-text-message-size (.setMaxTextMessageSize policy)))
      (.setCreator factory (reify-ws-creator handler)))))