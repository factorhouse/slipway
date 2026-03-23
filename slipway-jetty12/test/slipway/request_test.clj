(ns slipway.request-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.request :as request]))

(deftest upgrade?

  (is (not (request/upgrade? {})))
  (is (not (request/upgrade? {:headers {"Connection" "connection"}})))
  (is (not (request/upgrade? {:headers {"Upgrade" "upgrade"}})))

  (is (request/upgrade? {:headers {"Connection" "upgrade"
                                   "Upgrade"    "websocket"}}))

  (is (request/upgrade? {:headers {"connection" "UpGrAdE"
                                   "upgrade"    "wEbSOcket"}})))

(deftest websocket-protocol

  (is (= nil (request/websocket-protocol {})))
  (is (= 13 (request/websocket-protocol {::request/websocket-subprotocols [13 12]}))))
