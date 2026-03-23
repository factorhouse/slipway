(ns slipway.websockets
  (:require [slipway.request :as request]
            [slipway.response :as response])
  (:import (java.nio ByteBuffer)
           (java.time Duration)
           (org.eclipse.jetty.server Request Response)
           (org.eclipse.jetty.websocket.api Callback Session Session$Listener$AutoDemanding)
           (org.eclipse.jetty.websocket.server ServerWebSocketContainer WebSocketCreator)))

(defn proxy-ws-adapter
  [{:keys [on-open on-close on-error on-message]}]
  (let [session (atom nil)]
    (reify Session$Listener$AutoDemanding
      (^void onWebSocketOpen [_ ^Session current-session]
        (on-open (reset! session current-session)))
      (^void onWebSocketClose [_ ^int status ^String _reason ^Callback cb]
        (on-close @session status)
        (reset! session nil)
        (when cb (cb)))
      (^void onWebSocketError [_ ^Throwable error]
        (on-error @session error))
      (^void onWebSocketText [_ ^String message]
        (on-message @session message))
      (^void onWebSocketBinary [_ ^ByteBuffer payload ^Callback cb]
        (on-message @session payload)
        (when cb (cb))))))

(defn reify-ws-creator
  [request-map response-map]
  (reify WebSocketCreator
    (createWebSocket [_this _requrest response _cb]
      (let [listener (response/websocket-listener response-map)
            protocol (request/websocket-protocol request-map)]
        (when (some? protocol)
          (.setAcceptedSubProtocol response protocol))
        (proxy-ws-adapter listener)))))

(comment
  #:slipway.websockets{:idle-timeout            "max websocket idle time (in ms), default 500000"
                       :input-buffer-size       "max websocket input buffer size"
                       :output-buffer-size      "max websocket output buffer size"
                       :max-text-message-size   "max websocket text message size"
                       :max-binary-message-size "max websocket binary message size"
                       :max-frame-size          "max websocket frame size"
                       :auto-fragment           "websocket auto fragment (boolean)"})

(defn upgrade-websocket
  [^Request request ^Response response ^Callback cb request-map response-map opts]
  (let [{::keys [idle-timeout input-buffer-size output-buffer-size max-text-message-size max-binary-message-size max-frame-size auto-fragment]
         :or    {idle-timeout 500000}} opts
        creator   (reify-ws-creator request-map response-map)
        container (ServerWebSocketContainer/get (.getContext request))]
    (some->> idle-timeout (Duration/ofMillis) (.setIdleTimeout container))
    (some->> input-buffer-size (.setInputBufferSize container))
    (some->> output-buffer-size (.setOutputBufferSize container))
    (some->> max-text-message-size (.setMaxTextMessageSize container))
    (some->> max-binary-message-size (.setMaxBinaryMessageSize container))
    (some->> max-frame-size (.setMaxFrameSize container))
    (some->> auto-fragment (.setAutoFragment container))
    (.upgrade container creator request response cb)))