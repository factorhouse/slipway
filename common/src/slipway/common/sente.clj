(ns slipway.common.sente
  (:require [clojure.tools.logging :as log]
            [slipway.common.websockets :as common.websockets]
            [taoensso.sente.interfaces :as i])
  (:import (org.eclipse.jetty.websocket.api WebSocketAdapter)))

(def ws-cbs
  {:write-failed  (fn [ex] (log/error ex "websocket send failure"))
   :write-success (fn [] (log/debug "websocket send success"))})

(defn ajax-cbs [ws]
  {:write-failed  (fn [_] (common.websockets/close! ws))
   :write-success (fn [] (common.websockets/close! ws))})

(extend-protocol i/IServerChan
  WebSocketAdapter
  (sch-open? [ws]
    (common.websockets/connected? ws))

  (sch-close! [ws]
    (common.websockets/close! ws))

  (sch-send! [ws ws? msg]
    (if ws?
      ;; Note: it is important we async send as we send concurrently from multiple threads to one RemoteEndpoint
      ;;       in normal operation and this is not supported by the WS protocol. With sync send that results in the
      ;;       'Blocking message pending 10000 for BLOCKING' error that we see frequently where any client has
      ;;       a reasonable amount of snapshots / any network latency at all. This can lead to dropped messages,
      ;;       which in turn can be mildly problematic with our more complex UI (Data Inspect)
      ;;       jetty websockets maintain an internal queue per RemoteEndpoint that can be bounded from 9.4.32
      ;;         - see last comment: https://github.com/eclipse/jetty.project/issues/4824
      ;;       regardless it's fine to fire and forget as Sente has internal ws keepalive heartbeat implemented at 25s
      ;;         - see :ws-kalive-ms configuration
      ;;       so all ws channels are bounded in terms of our attempts to send, regardless if hard/half closed
      ;;       though we should bound the RemoteEndpoint queue on 9.4 availability all the same
      (common.websockets/send! ws msg ws-cbs)
      (common.websockets/send! ws msg (ajax-cbs ws)))))

(defn server-ch-resp
  [ws? {:keys [on-open on-close on-msg on-error]}]
  (if ws?
    (common.websockets/upgrade-response
     {:on-connect (fn [ws]
                    (on-open ws ws?))
      :on-text    (fn [ws msg]
                    (on-msg ws ws? msg))
      :on-close   (fn [ws status-code _]
                    (on-close ws ws? status-code))
      :on-error   (fn [ws e]
                    (on-error ws ws? e))})

    ;; Only support :ws as a protocol
    {:status 406}))

(deftype JettyServerChanAdapter []
  i/IServerChanAdapter
  (ring-req->server-ch-resp [_ req callbacks-map]
    (server-ch-resp (common.websockets/upgrade-request? req) callbacks-map)))

(defn get-sch-adapter []
  (JettyServerChanAdapter.))