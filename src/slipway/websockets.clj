(ns slipway.websockets
  (:require [clojure.tools.logging :as log]
            [slipway.request :as request]
            [slipway.response :as response]
            [slipway.sente :as sente])
  (:import (java.nio ByteBuffer)
           (java.time Duration)
           (org.eclipse.jetty.server Server)
           (org.eclipse.jetty.server.handler ContextHandler)
           (org.eclipse.jetty.websocket.api Callback Session Session$Listener$AutoDemanding)
           (org.eclipse.jetty.websocket.server WebSocketCreator WebSocketUpgradeHandler)))

(defn proxy-ws-adapter
  [{:keys [on-open on-close _on-error on-message]}]
  (let [session (atom nil)]
    (reify Session$Listener$AutoDemanding
      (^void onWebSocketOpen [_ ^Session current-session]
        (on-open (reset! session current-session)))
      (^void onWebSocketClose [_ ^int status ^String _reason ^Callback cb]
        (on-close @session status)
        (reset! session nil)
        (when cb (.succeed cb)))
      (^void onWebSocketError [_ ^Throwable error]
       ;; We choose to log the error type/message without the stacktrace here, these can be quite insignificant and
       ;; can cause a lot of chat in the logs, particularly ChannelClosedExceptions which can happen as a normal 
       ;; part of user interaction (hard shutting the browser tab).
        (log/error "websocket error" (some-> error (.toString))))
      (^void onWebSocketText [_ ^String message]
        (on-message @session message))
      (^void onWebSocketBinary [_ ^ByteBuffer payload ^Callback cb]
        (on-message @session payload)
        (when cb (.succeed cb))))))

(defn reify-ws-creator
  ^WebSocketCreator [ring-handler]
  (reify WebSocketCreator
    (createWebSocket [_this request response cb]
      (let [handshake (ring-handler (request/request-map request response))]
        (or (some-> handshake ::sente/server-adapter (proxy-ws-adapter))
            (do (response/update-response request response handshake)
                (.succeeded cb)))))))

(comment
  #:slipway.websockets{:enabled?                 "are websockets enabled? default true"
                       :path-spec                "the websocket path-spec, default '/chsk'"
                       :idle-timeout-ms          "max websocket idle time, default 300000"
                       :input-buffer-bytes       "max websocket input buffer size"
                       :output-buffer-bytes      "max websocket output buffer size"
                       :max-text-message-bytes   "max websocket text message size that can be received"
                       :max-binary-message-bytes "max websocket binary message size that can be received"
                       :max-frame-bytes          "max websocket frame size"
                       :max-outgoing-frames      "max websocket frames waiting to be sent per session, default -1"
                       :auto-fragment            "websocket auto fragment (boolean), default true"})

(defn handler ^WebSocketUpgradeHandler
  [^Server server ^ContextHandler handler ring-handler opts]
  (let [{::keys [enabled? path-spec idle-timeout-ms input-buffer-bytes output-buffer-bytes max-text-message-bytes
                 max-binary-message-bytes max-frame-bytes max-outgoing-frames auto-fragment]
         :or    {path-spec       "/chsk"
                 idle-timeout-ms 300000}} opts]
    (when (not (false? enabled?))
      (log/debugf "configuring websockets at %s with %s" path-spec opts)
      (WebSocketUpgradeHandler/from
       server
       handler
       (fn [container]
         (some->> idle-timeout-ms (Duration/ofMillis) (.setIdleTimeout container))
         (some->> input-buffer-bytes (.setInputBufferSize container))
         (some->> output-buffer-bytes (.setOutputBufferSize container))
         (some->> max-text-message-bytes (.setMaxTextMessageSize container))
         (some->> max-binary-message-bytes (.setMaxBinaryMessageSize container))
         (some->> max-frame-bytes (.setMaxFrameSize container))
         (some->> max-outgoing-frames (.setMaxOutgoingFrames container))
         (some->> auto-fragment (.setAutoFragment container))
         (.addMapping container "/chsk" (reify-ws-creator ring-handler)))))))