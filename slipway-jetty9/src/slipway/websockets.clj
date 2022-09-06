(ns slipway.websockets
  "Jetty9 impl of the Websockets API + handler, inspired by:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9/websocket.clj"
  (:require [slipway.common.websockets :as common.ws]
            [slipway.servlet :as servlet])
  (:import (clojure.lang IFn)
           (java.nio ByteBuffer)
           (javax.servlet.http HttpServletRequest HttpServletResponse)
           (org.eclipse.jetty.server Request)
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

(defn handler
  [handler {::keys [ws-max-idle-time ws-max-text-message-size]
            :or    {ws-max-idle-time         500000
                    ws-max-text-message-size 65536}}]
  (proxy [WebSocketHandler] []
    (configure [^WebSocketServletFactory factory]
      (doto (.getPolicy factory)
        (.setIdleTimeout ws-max-idle-time)
        (.setMaxTextMessageSize ws-max-text-message-size))
      (.setCreator factory (reify-ws-creator handler)))))