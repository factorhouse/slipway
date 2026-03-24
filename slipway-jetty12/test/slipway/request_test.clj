(ns slipway.request-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.request :as request]))

(deftest websocket-upgrade?

  (is (not (request/websocket-upgrade? {})))
  (is (not (request/websocket-upgrade? {:headers {"Connection" "connection"}})))
  (is (not (request/websocket-upgrade? {:headers {"Upgrade" "upgrade"}})))

  (is (request/websocket-upgrade? {:headers {"Connection" "upgrade"
                                             "Upgrade"    "websocket"}}))

  (is (request/websocket-upgrade? {:headers {"connection" "UpGrAdE"
                                             "upgrade"    "wEbSOcket"}})))

(deftest websocket-protocol

  (is (= nil (request/websocket-protocol {})))
  (is (= 13 (request/websocket-protocol {::request/websocket-subprotocols [13 12]}))))
