(ns slipway.example
  (:require [clojure.test :refer :all]
            [slipway.example.app :as app]
            [slipway.server :as server])
  (:import (io.factorhouse.slipway SimpleErrorHandler)
           (org.eclipse.jetty.security ConstraintMapping)
           (org.eclipse.jetty.util.security Constraint)))

(def state (atom nil))

(def constraints
  (let [require-auth (doto (Constraint. "auth" Constraint/ANY_AUTH) (.setAuthenticate true))
        none         (doto (Constraint.) (.setName "no-auth"))]
    [(doto (ConstraintMapping.) (.setConstraint none) (.setPathSpec "/up"))
     (doto (ConstraintMapping.) (.setConstraint none) (.setPathSpec "/css/*"))
     (doto (ConstraintMapping.) (.setConstraint none) (.setPathSpec "/img/*"))
     (doto (ConstraintMapping.) (.setConstraint require-auth) (.setPathSpec "/*"))]))

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
  {:error-handler (SimpleErrorHandler. (app/error-html 500 "Server Error"))
   :auth          {:realm               "slipway"
                   :login-uri           "/login"
                   :logout-uri          "/logout"
                   :login-retry-uri     "/login-retry"
                   :auth-method         "form"
                   :auth-type           "jaas"
                   :constraint-mappings constraints}})

(def hash-opts
  {:error-handler (SimpleErrorHandler. (app/error-html 500 "Server Error"))
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
    (server/stop server)))

(defn start-server!
  ([opts]
   (start-server! (app/handler) opts))
  ([handler opts]
   (stop-server!)
   (reset! state (server/start handler opts))))

(defn http-server
  []
  (start-server! app/hello-handler {}))

(defn https-server
  []
  (start-server! app/hello-handler ssl-opts))

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