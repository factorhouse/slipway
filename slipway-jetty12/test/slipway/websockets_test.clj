(ns slipway.websockets-test
  (:require [clojure.test :refer :all]
            [slipway.websockets :as websocket]))

(deftest upgrade-request?

  (is (not (websocket/upgrade-request? {}))))