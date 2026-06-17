(ns slipway.websockets-test
  (:require [clojure.test :refer [deftest is testing]]
            [slipway.websockets :as websockets]))

(deftest path-spec

  (testing "not set"
    (is (= nil (websockets/path-spec nil)))
    (is (= nil (websockets/path-spec {}))))

  (testing "not enabled"
    (is (= nil (websockets/path-spec {::websockets/path-spec "/some-path"})))
    (is (= nil (websockets/path-spec {::websockets/enabled?  nil
                                      ::websockets/path-spec "/some-path"})))
    (is (= nil (websockets/path-spec {::websockets/enabled?  false
                                      ::websockets/path-spec "/some-path"}))))

  (testing "default path-spec"
    (is (= "/chsk" (websockets/path-spec {::websockets/enabled? true}))))

  (testing "specific path-spec"
    (is (= "/some-path" (websockets/path-spec {::websockets/enabled?  true
                                               ::websockets/path-spec "/some-path"})))))

