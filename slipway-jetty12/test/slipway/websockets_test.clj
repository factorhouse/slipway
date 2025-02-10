(ns slipway.websockets-test
  (:require [slipway.websockets :as websocket]
    [clojure.test :refer :all]))

(deftest upgrade-request?

  (is (not (websocket/upgrade-request? {}))))