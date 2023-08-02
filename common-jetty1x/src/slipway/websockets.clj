(ns slipway.websockets
  (:require [slipway.common.websockets :as common.ws]
            [slipway.servlet :as servlet])
  (:import (clojure.lang IFn)
           (java.nio ByteBuffer)
           (java.time Duration)
           (org.eclipse.jetty.websocket.api RemoteEndpoint Session WebSocketAdapter WebSocketPingPongListener WriteCallback)
           (org.eclipse.jetty.websocket.server JettyServerUpgradeRequest JettyWebSocketCreator JettyWebSocketServerContainer)))

(def no-op (constantly nil))

(extend-protocol servlet/RequestMapDecoder

  JettyServerUpgradeRequest
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
  [{:keys [on-connect on-error on-text on-close on-bytes on-ping on-pong]
    :or   {on-connect no-op
           on-error   no-op
           on-text    no-op
           on-close   no-op
           on-bytes   no-op
           on-ping    no-op
           on-pong    no-op}}]
  (proxy [WebSocketAdapter WebSocketPingPongListener] []
    (onWebSocketConnect [^Session session]
      (let [^WebSocketAdapter _ this]
        (proxy-super onWebSocketConnect session))
      (on-connect this))
    (onWebSocketError [^Throwable e]
      (on-error this e))
    (onWebSocketText [^String message]
      (on-text this message))
    (onWebSocketClose [statusCode ^String reason]
      (let [^WebSocketAdapter _ this]
        (proxy-super onWebSocketClose statusCode reason))
      (on-close this statusCode reason))
    (onWebSocketBinary [^bytes payload offset len]
      (on-bytes this payload offset len))
    (onWebSocketPing [^ByteBuffer bytebuffer]
      (on-ping this bytebuffer))
    (onWebSocketPong [^ByteBuffer bytebuffer]
      (on-pong this bytebuffer))))

(defn reify-ws-creator
  [ws-fns]
  (reify JettyWebSocketCreator
    (createWebSocket [_ _ _]
      (proxy-ws-adapter ws-fns))))

(comment
  #:slipway.websockets{:idle-timeout            "max websocket idle time (in ms), default 500000"
                       :input-buffer-size       "max websocket input buffer size"
                       :output-buffer-size      "max websocket output buffer size"
                       :max-text-message-size   "max websocket text message size"
                       :max-binary-message-size "max websocket binary message size"
                       :max-frame-size          "max websocket frame size"
                       :auto-fragment           "websocket auto fragment (boolean)"})

(defn upgrade-websocket
  [req res ws opts]
  (let [{::keys [idle-timeout input-buffer-size output-buffer-size max-text-message-size max-binary-message-size max-frame-size auto-fragment]
         :or    {idle-timeout 500000}} opts
        creator   (reify-ws-creator ws)
        container (JettyWebSocketServerContainer/getContainer (servlet/get-context req))]
    (some->> idle-timeout (Duration/ofMillis) (.setIdleTimeout container))
    (some->> input-buffer-size (.setInputBufferSize container))
    (some->> output-buffer-size (.setOutputBufferSize container))
    (some->> max-text-message-size (.setMaxTextMessageSize container))
    (some->> max-binary-message-size (.setMaxBinaryMessageSize container))
    (some->> max-frame-size (.setMaxFrameSize container))
    (some->> auto-fragment (.setAutoFragment container))
    (.upgrade container creator req res)))