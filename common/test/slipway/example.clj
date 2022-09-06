(ns slipway.example
  (:require [clojure.test :refer :all]
            [slipway :as slipway]
            [slipway.authz :as authz]
            [slipway.example.app :as app]
            [slipway.server :as server]
            [slipway.session :as session]
            [slipway.ssl :as ssl])
  (:import (org.eclipse.jetty.security ConstraintMapping)
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

(def http-opts
  (merge #::server{:error-handler app/server-error-handler}))

(def https-opts
  (merge #::server{:ssl?          true
                   :http?         false
                   :ssl-port      3000
                   :error-handler app/server-error-handler}
         #::ssl{:keystore        "dev-resources/my-keystore.jks"
                :keystore-type   "PKCS12"
                :key-password    "password"
                :truststore      "dev-resources/my-truststore.jks"
                :trust-password  "password"
                :truststore-type "PKCS12"}))

(def jaas-opts
  (merge #::server{:error-handler app/server-error-handler}
         #::authz{:realm               "slipway"
                  :login-service       "jaas"
                  :hash-user-file      "common/dev-resources/jaas/hash-realm.properties"
                  :authenticator       (FormAuthenticator. "/login" "/login-retry" false)
                  :constraint-mappings constraints}))

(def hash-opts
  (merge #::server{:error-handler app/server-error-handler}
         #::authz{:realm               "slipway"
                  :login-service       "hash"
                  :hash-user-file      "common/dev-resources/jaas/hash-realm.properties"
                  :authenticator       (FormAuthenticator. "/login" "/login-retry" false)
                  :constraint-mappings constraints}
         #::session{:max-inactive-interval 20}))

(def basic-authenticator-opts
  #::authz{:authenticator (BasicAuthenticator.)})

(defn stop-server!
  []
  (when-let [server @state]
    (slipway/stop server)))

(defn start-server!
  [handler opts]
  (stop-server!)
  (reset! state (slipway/start handler opts)))

(defn http-server
  []
  (start-server! (app/handler) http-opts))

(defn https-server
  []
  (start-server! (app/handler) https-opts))

(defn http-hash-server
  []
  (start-server! (app/handler) hash-opts))

(defn http-hash-basic-server
  []
  (start-server! (app/handler) (merge hash-opts basic-authenticator-opts)))

(defn https-hash-server
  []
  (start-server! (app/handler) (merge https-opts hash-opts)))

(defn https-hash-basic-server
  []
  (start-server! (app/handler) (merge https-opts hash-opts basic-authenticator-opts)))

(defn http-jaas-server
  "Start a REPL with the following JVM JAAS parameter:
    - Hash User Auth  ->  -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf
    - LDAP Auth       ->  -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf"
  []
  (start-server! (app/handler) jaas-opts))

(defn http-jaas-basic-server
  "Start a REPL with the following JVM JAAS parameter:
    - Hash User Auth  ->  -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf
    - LDAP Auth       ->  -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf"
  []
  (start-server! (app/handler) (merge jaas-opts basic-authenticator-opts)))

(defn https-jaas-server
  "Start a REPL with the following JVM JAAS parameter:
    - Hash User Auth  ->  -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf
    - LDAP Auth       ->  -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf"
  []
  (start-server! (app/handler) (merge jaas-opts https-opts)))

(defn https-jaas-basic-server
  "Start a REPL with the following JVM JAAS parameter:
    - Hash User Auth  ->  -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf
    - LDAP Auth       ->  -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf"
  []
  (start-server! (app/handler) (merge jaas-opts https-opts basic-authenticator-opts)))