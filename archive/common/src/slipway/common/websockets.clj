(ns slipway.common.websockets
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
  (let [connection (or (get headers "Connection") (get headers "connection"))
        upgrade    (or (get headers "Upgrade") (get headers "upgrade"))]
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
   :headers {"Connection" "Upgrade"
             "Upgrade"    "Websocket"}
   :ws      ws-handler})