(ns slipway.example
  (:require [clojure.test :refer :all]
            [slipway.common.auth.constraints :as constraints]
            [slipway.example.handler :as handler]
            [slipway.server :as slipway])
  (:import (io.factorhouse.slipway SimpleErrorHandler)))

(def state (atom nil))

;; first constraint wins, so this applies auth to anything not explicitly listed prior
(def constraints (constraints/constraint-mappings
                  ["/up" (constraints/no-auth)]
                  ["/css/*" (constraints/no-auth)]
                  ["/img/*" (constraints/no-auth)]
                  ["/favicon.ico" (constraints/no-auth)]
                  ["/*" (constraints/form-auth-any-constraint)]))

(def ssl-opts
  {:ssl?            true
   :http?           false
   :ssl-port        3000
   :keystore        "dev-resources/my-keystore.jks"
   :keystore-type   "PKCS12"
   :key-password    "password"
   :truststore      "dev-resources/my-truststore.jks"
   :trust-password  "password"
   :truststore-type "PKCS12"})

(def jaas-opts
  {:error-handler (SimpleErrorHandler. (handler/error-html 500 "Server Error"))
   :auth          {:realm               "slipway"
                   :login-uri           "/login"
                   :logout-uri          "/logout"
                   :login-retry-uri     "/login-retry"
                   :auth-method         "form"
                   :auth-type           "jaas"
                   :constraint-mappings constraints}})

(def hash-opts
  {:error-handler (SimpleErrorHandler. (handler/error-html 500 "Server Error"))
   :auth          {:realm               "slipway"
                   :login-uri           "/login"
                   :logout-uri          "/logout"
                   :login-retry-uri     "/login-retry"
                   :auth-method         "form"
                   :auth-type           "hash"
                   :hash-user-file      "common/dev-resources/jaas/hash-realm.properties"
                   :session             {:max-inactive-interval 20}
                   :constraint-mappings constraints}})

(defn stop-server!
  []
  (when-let [server @state]
    (slipway/stop-jetty server)))

(defn start-server!
  ([opts]
   (start-server! (handler/ring-handler) opts))
  ([handler opts]
   (stop-server!)
   (reset! state (slipway/start-jetty handler opts))))

(defn http-server
  []
  (start-server! handler/hello {}))

(defn https-server
  []
  (start-server! handler/hello ssl-opts))

(defn hash-server
  []
  (start-server! hash-opts))

(defn hash-basic-server
  []
  (start-server! (assoc-in hash-opts [:auth :auth-method] "basic")))

(defn jaas-server
  "Start a REPL with the following JVM JAAS parameter:
    - Hash User Auth  ->  -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf
    - LDAP Auth       ->  -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf"
  []
  (start-server! jaas-opts))

(defn jaas-basic-server
  "Start a REPL with the following JVM JAAS parameter:
    - Hash User Auth  ->  -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf
    - LDAP Auth       ->  -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf"
  []
  (start-server! (assoc-in jaas-opts [:auth :auth-method] "basic")))