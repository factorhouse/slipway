(ns slipway.example.handler
  (:require [clojure.test :refer :all]))

(def hello-handler-content "<html><h1>Hello world</h1></html>")

(defn hello
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    hello-handler-content})