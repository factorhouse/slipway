(ns slipway.websockets-test
  (:require [clojure.test :refer :all]
            [slipway.websockets :as ws]))

(deftest websocket-req
  (is (false? (ws/upgrade-request? {})))
  (is (false? (ws/upgrade-request? {:headers {"connection" "upgrade"}})))
  (is (false? (ws/upgrade-request? {:headers {"upgrade" "websocket"}})))
  (is (false? (ws/upgrade-request? {:headers {"connection" "a"
                                              "upgrade"    "b"}})))
  (is (ws/upgrade-request? {:headers {"connection" "upgrade"
                                      "upgrade"    "websocket"}}))
  (is (ws/upgrade-request? {:headers {"connection" "Upgrade"
                                      "upgrade"    "Websocket"}})))

(deftest ws-response
  (is (false? (ws/upgrade-response? {:ws {}})))
  (is (false? (ws/upgrade-response? {:status 200 :body "Hello world"})))
  (is (ws/upgrade-response? (ws/upgrade-response {}))))