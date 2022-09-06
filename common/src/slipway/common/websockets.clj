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
  (let [upgrade    (get headers "upgrade")
        connection (get headers "connection")]
    (and (some? upgrade)
         (some? connection)
         (string/includes? (string/lower-case upgrade) "websocket")
         (string/includes? (string/lower-case connection) "upgrade"))))

(defn upgrade-response?
  [{:keys [status ws] :as resp}]
  (and (= 101 status) (map? ws) (upgrade-request? resp)))

;; TODO: should this response be capitalized? Generally outputted as Connection: Upgrade, see RFC6455Negotiation
(defn upgrade-response
  [ws-handler]
  {:status  101
   :headers {"connection" "upgrade"
             "upgrade"    "websocket"}
   :ws      ws-handler})