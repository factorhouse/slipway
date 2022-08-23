(ns slipway.example.handler
  (:require [clojure.test :refer :all]))

(def hello-handler-content "Hello world")

(defn hello
  [_]
  {:status 200 :body hello-handler-content})