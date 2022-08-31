(ns slipway.example.server
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

(defn stop!
  []
  (when-let [server @state]
    (slipway/stop-jetty server)))

(defn start!
  ([opts]
   (start! (handler/ring-handler) opts))
  ([handler opts]
   (stop!)
   (reset! state (slipway/start-jetty handler opts))))

(defn basic-http!
  []
  (start! handler/hello {}))

(defn basic-https!
  []
  (start! handler/hello ssl-opts))

(defn jaas-form-auth!
  "Start a REPL with the following JVM JAAS parameter:
    - Hash User Auth  ->  -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf
    - LDAP Auth       ->  -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf"
  []
  (start! jaas-opts))

(defn jaas-basic-auth!
  "Start a REPL with the following JVM JAAS parameter:
    - Hash User Auth  ->  -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf
    - LDAP Auth       ->  -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf"
  []
  (start! (assoc-in jaas-opts [:auth :auth-method] "basic")))

(defn hash-form-auth!
  []
  (start! hash-opts))