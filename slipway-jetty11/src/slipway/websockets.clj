(ns slipway.websockets
  "Jetty11 impl of the Websockets API + handler, inspired by:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9/websocket.clj"
  (:require [slipway.common.websockets :as common.ws]
            [slipway.servlet :as servlet])
  (:import (clojure.lang IFn)
           (jakarta.servlet.http HttpServletRequest HttpServletResponse)
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

(defn upgrade-websocket
  [^HttpServletRequest req ^HttpServletResponse res ws opts]
  (let [{::keys [ws-max-idle-time ws-max-text-message-size]
         :or    {ws-max-idle-time         500000
                 ws-max-text-message-size 65536}} opts
        creator   (reify-ws-creator ws)
        container (JettyWebSocketServerContainer/getContainer (.getServletContext req))]
    (.setIdleTimeout container (Duration/ofMillis ws-max-idle-time))
    (.setMaxTextMessageSize container ws-max-text-message-size)
    (.upgrade container creator req res)))