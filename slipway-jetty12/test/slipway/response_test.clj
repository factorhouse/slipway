(ns slipway.response-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.response :as response]))

(deftest websocket-listener

  (is (not (response/websocket-listener {})))
  (is (response/websocket-listener {::response/websocket-listener {}})))

(deftest upgrade

  (is (= {::response/websocket-listener {:some :listener}}
         (response/websocket-upgrade {:some :listener}))))