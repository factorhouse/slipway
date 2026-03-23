(ns slipway.response-test
  (:require [clojure.test :refer [deftest is]]
            [slipway.response :as response]))

(deftest websocket-listener

  (is (not (response/websocket-listener {})))
  (is (response/websocket-listener {::response/websocket-listener {}})))

(deftest upgrade?

  (is (not (response/upgrade? {})))
  (is (not (response/upgrade? {:status 101})))
  (is (response/upgrade? {:status 101 ::response/websocket-listener {}})))

(deftest upgrade

  (is (= {:headers                      {"Connection" "Upgrade"
                                         "Upgrade"    "Websocket"}
          :status                       101
          ::response/websocket-listener {:some :listener}}
         (response/upgrade {:some :listener}))))
