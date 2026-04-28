(ns slipway.sente
  (:require [clojure.core.async :as async]
            [clojure.tools.logging :as log]
            [taoensso.sente :as sente]
            [taoensso.sente.interfaces :as i])
  (:import (org.eclipse.jetty.websocket.api Callback Session)))

(extend-type Session
  i/IServerChan
  (sch-open? [sch]
    (.isOpen sch))
  (sch-close! [sch]
    (when (.isOpen sch)
      (.close sch)
      true))
  (sch-send! [sch websocket? msg]
    (when (and websocket? (.isOpen sch))
      (if (instance? CharSequence msg)
        (.sendText sch msg Callback/NOOP)
        (.sendBinary sch msg Callback/NOOP))
      true)))

(deftype JettyServerChanAdapter []

  i/IServerChanAdapter
  (ring-req->server-ch-resp [_ _request {:keys [on-open on-close on-msg on-error]}]
    {::server-adapter {:on-open    (fn [sch] (on-open sch true))
                       :on-close   (fn [sch status-code] (on-close sch true status-code))
                       :on-error   (fn [sch e] (on-error sch true e))
                       :on-message (fn [sch msg] (on-msg sch true msg))}}))

(defn send-message
  [connected-uids send-fn uid msg]
  (if (= uid :broadcast)
    (doseq [uid (:ws @connected-uids)]
      (log/trace "broadcast" uid (first msg))
      (send-fn uid msg))
    (do (log/trace "send" uid (first msg))
        (send-fn uid msg))))

(comment
  #:slipway.sente{:options "A map of options passed directly to sente/make-channel-socket-server!"})

(defn start
  [opts]
  (log/debugf "starting sente server %s" opts)
  (let [server (sente/make-channel-socket-server! (JettyServerChanAdapter.) opts)
        {:keys [ch-recv send-fn connected-uids ajax-get-or-ws-handshake-fn]} server]
    {:ch-recv         ch-recv
     :chsk-send!      (partial send-message connected-uids send-fn)
     :ws-handshake-fn ajax-get-or-ws-handshake-fn
     :connected-uids  connected-uids}))

(defn stop
  [{:keys [ch-recv]}]
  (log/debug "stopping sente server")
  (async/close! ch-recv))