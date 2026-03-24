(ns slipway.websockets
  (:require [clojure.tools.logging :as log]
            [slipway.request :as request]
            [slipway.response :as response])
  (:import (java.nio ByteBuffer)
           (java.time Duration)
           (org.eclipse.jetty.server Request Response)
           (org.eclipse.jetty.websocket.api Callback Session Session$Listener$AutoDemanding)
           (org.eclipse.jetty.websocket.server ServerWebSocketContainer WebSocketCreator)))

(defn proxy-ws-adapter
  [{:keys [on-open on-close _on-error on-message]}]
  (let [session (atom nil)]
    (reify Session$Listener$AutoDemanding
      (^void onWebSocketOpen [_ ^Session current-session]
        (on-open (reset! session current-session)))
      (^void onWebSocketClose [_ ^int status ^String _reason ^Callback cb]
        (on-close @session status)
        (reset! session nil)
        (when cb (cb)))
      (^void onWebSocketError [_ ^Throwable error]
       ;; sente currently log/errors, we mute down to log/debug because the common ChannelClosedException when a user
       ;; hards shuts their browser session can be a bit chatty in the logs. If sente does something further on-error
       ;; we should consider reapplying here
       ;; (on-error @session error)
        (log/debug "websocket error" error))
      (^void onWebSocketText [_ ^String message]
        (on-message @session message))
      (^void onWebSocketBinary [_ ^ByteBuffer payload ^Callback cb]
        (on-message @session payload)
        (when cb (cb))))))

(defn reify-ws-creator
  [request-map response-map]
  (reify WebSocketCreator
    (createWebSocket [_this _request response _cb]
      (let [listener   (response/websocket-listener response-map)
            protocol   (request/websocket-protocol request-map)
            extensions (request/websocket-extensions)]
        (when protocol (.setAcceptedSubProtocol response protocol))
        (when extensions (.setExtensions response extensions))
        (proxy-ws-adapter listener)))))

(comment
  #:slipway.websockets{:idle-timeout-ms          "max websocket idle time, default 500000"
                       :input-buffer-bytes       "max websocket input buffer size"
                       :output-buffer-bytes      "max websocket output buffer size"
                       :max-text-message-bytes   "max websocket text message size"
                       :max-binary-message-bytes "max websocket binary message size"
                       :max-frame-bytes          "max websocket frame size"
                       :auto-fragment            "websocket auto fragment (boolean)"})

(defn upgrade-websocket
  [^Request request ^Response response ^Callback cb request-map response-map opts]
  (let [{::keys [idle-timeout-ms input-buffer-bytes output-buffer-bytes max-text-message-bytes max-binary-message-bytes
                 max-frame-bytes auto-fragment]
         :or    {idle-timeout-ms 500000}} opts
        creator   (reify-ws-creator request-map response-map)
        container (ServerWebSocketContainer/get (.getContext request))]
    (some->> idle-timeout-ms (Duration/ofMillis) (.setIdleTimeout container))
    (some->> input-buffer-bytes (.setInputBufferSize container))
    (some->> output-buffer-bytes (.setOutputBufferSize container))
    (some->> max-text-message-bytes (.setMaxTextMessageSize container))
    (some->> max-binary-message-bytes (.setMaxBinaryMessageSize container))
    (some->> max-frame-bytes (.setMaxFrameSize container))
    (some->> auto-fragment (.setAutoFragment container))
    (.upgrade container creator request response cb)))