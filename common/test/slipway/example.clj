(ns slipway.example
  (:require [clojure.test :refer :all]
            [slipway :as slipway]
            [slipway.authz :as authz]
            [slipway.example.app :as app]
            [slipway.server :as server]
            [slipway.session :as session]
            [slipway.ssl :as ssl])
  (:import (io.factorhouse.slipway SimpleErrorHandler)
           (org.eclipse.jetty.security ConstraintMapping)
           (org.eclipse.jetty.security.authentication BasicAuthenticator FormAuthenticator)
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
  (merge #::server{:ssl?     true
                   :http?    false
                   :ssl-port 3000}
         #::ssl{:keystore        "dev-resources/my-keystore.jks"
                :keystore-type   "PKCS12"
                :key-password    "password"
                :truststore      "dev-resources/my-truststore.jks"
                :trust-password  "password"
                :truststore-type "PKCS12"}))

(def jaas-opts
  (merge #::server{:error-handler (SimpleErrorHandler. (app/error-html 500 "Server Error"))}
         #::authz{:realm               "slipway"
                  :login-service       "jaas"
                  :hash-user-file      "common/dev-resources/jaas/hash-realm.properties"
                  :authenticator       (FormAuthenticator. "/login" "/login-retry" false)
                  :constraint-mappings constraints}))

(def hash-opts
  (merge #::server{:error-handler (SimpleErrorHandler. (app/error-html 500 "Server Error"))}
         #::authz{:realm               "slipway"
                  :login-service       "hash"
                  :hash-user-file      "common/dev-resources/jaas/hash-realm.properties"
                  :authenticator       (FormAuthenticator. "/login" "/login-retry" false)
                  :constraint-mappings constraints}
         #::session{:max-inactive-interval 20}))

(defn stop-server!
  []
  (when-let [server @state]
    (slipway/stop server)))

(defn start-server!
  ([opts]
   (start-server! (app/handler) opts))
  ([handler opts]
   (stop-server!)
   (reset! state (slipway/start handler opts))))

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
  (start-server! (assoc-in hash-opts [::authz/authenticator] (BasicAuthenticator.))))

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
  (start-server! (assoc-in jaas-opts [::authz/authenticator] (BasicAuthenticator.))))