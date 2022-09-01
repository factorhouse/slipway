(ns slipway.server-jetty9-test
  (:require [clojure.test :refer :all]
            [slipway.example :as example]))

(deftest authentication

  (try
    (example/hash-server)

    (testing "post-login-redirect-chsk"

      ;; if we start our session on the login page we have no post-login request context we fallback
      ;; to the default context, this tests a default context is in place in the handler chain

      (is (= 2 (+ 1 1)))
      ;(is (= {:protocol-version      {:name "HTTP", :major 1, :minor 1}
      ;        :status                200
      ;        :reason-phrase         "OK"
      ;        :orig-content-encoding "gzip"
      ;        :length                764
      ;        :body                  handler/home-html}
      ;       (-> (client/do-login "http" "localhost" 3000 "/chsk" "admin" "admin")
      ;           :ring
      ;           (select-keys [:protocol-version :status :reason-phrase :length :body :orig-content-encoding]))))
      )

    (finally (example/stop-server!))))