(ns slipway.example.server.ssl
  (:require [clojure.test :refer :all]))

(def opts
  {:ssl?            true
   :http?           false
   :ssl-port        3000
   :keystore        "dev-resources/my-keystore.jks"
   :keystore-type   "PKCS12"
   :key-password    "password"
   :truststore      "dev-resources/my-truststore.jks"
   :trust-password  "password"
   :truststore-type "PKCS12"})