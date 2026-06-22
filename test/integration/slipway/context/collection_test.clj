(ns slipway.context.collection-test
  (:require [clojure.test :refer :all]
            [slipway.connector.http :as http]
            [slipway.context :as context]
            [slipway.example.app :as app]
            [slipway.example.html :as html]
            [slipway.server :as server]
            [slipway.test-client :as client]
            [slipway.test-server :as test-server]))

(def of-interest [:protocol-version :status :reason-phrase :body :headers :orig-content-encoding])

(deftest single-context

  (testing "default context"
    (try
      (test-server/start!
       #::server{:connector     {::http/port 3000}
                 :handler       {::server/handler-type ::context/handler-collection
                                 ::context/handlers    [{::context/ring-handler (app/handler)}]}
                 :error-handler app/server-error-handler})

      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}
              :body                  (html/user-page {})}
             (-> (client/do-get "http://localhost:3000/user" {})
                 (select-keys of-interest))))

      (finally (test-server/stop!))))

  (testing "specific context"
    (try
      (test-server/start!
       #::server{:connector     {::http/port 3000}
                 :handler       {::server/handler-type ::context/handler-collection
                                 ::context/handlers    [{::context/ring-handler (app/handler)
                                                         ::context/path         "/x-context"}]}
                 :error-handler app/server-error-handler})

      ;; the full path is now at /x-content/user as per the context-path
      (is (= {:status        404
              :reason-phrase "Not Found"}
             (-> (client/do-get "http://localhost:3000/user" {})
                 (select-keys [:status :reason-phrase]))))

      ;; previous content accessible at /x-content/user
      (is (= {:protocol-version      {:name "HTTP" :major 1 :minor 1}
              :status                200
              :reason-phrase         "OK"
              :orig-content-encoding "gzip"
              :headers               {"Connection"   "close"
                                      "Content-Type" "text/html"
                                      "Vary"         "Accept-Encoding"}
              :body                  (html/user-page {})}
             (-> (client/do-get "http://localhost:3000/x-context/user" {})
                 (select-keys of-interest))))

      (finally (test-server/stop!)))))