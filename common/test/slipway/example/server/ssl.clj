(ns slipway.example.server.ssl
  (:require [clojure.test :refer :all]
            [slipway.example.handler :as handler]
            [slipway.server :as slipway]))

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

(defn server
  []
  (slipway/run-jetty handler/hello opts))