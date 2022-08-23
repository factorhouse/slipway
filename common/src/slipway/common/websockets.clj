(ns slipway.common.websockets
  "The Websockets interface and related utility functions.

  Derived from:
    * https://github.com/sunng87/ring-jetty9-adapter/blob/master/src/ring/adapter/jetty9/websocket.clj"
  (:require [clojure.string :as string]))

(defprotocol WebSockets
  (send! [this msg] [this msg callback])
  (ping! [this] [this msg])
  (close! [this] [this status-code reason])
  (remote-addr [this])
  (idle-timeout! [this ms])
  (connected? [this])
  (req-of [this]))

(defprotocol WebSocketSend
  (-send! [x ws] [x ws callback]))

(defprotocol WebSocketPing
  (-ping! [x ws]))

(defn upgrade-request?
  [{:keys [headers]}]
  (let [upgrade    (get headers "upgrade")
        connection (get headers "connection")]
    (and (some? upgrade)
         (some? connection)
         (string/includes? (string/lower-case upgrade) "websocket")
         (string/includes? (string/lower-case connection) "upgrade"))))

(defn upgrade-response?
  [{:keys [status ws] :as resp}]
  (and (= 101 status) (map? ws) (upgrade-request? resp)))

(defn upgrade-response
  [ws-handler]
  {:status  101
   :headers {"upgrade"    "websocket"
             "connection" "upgrade"}
   :ws      ws-handler})