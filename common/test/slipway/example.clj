(ns slipway.example
  (:require [clojure.test :refer :all]
            [slipway :as slipway]
            [slipway.authz :as authz]
            [slipway.example.app :as app]
            [slipway.handler :as handler]
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

(def options
  {:http          #::server{:error-handler app/server-error-handler}
   :https         (merge #::server{:ssl?          true
                                   :http?         false
                                   :ssl-port      3000
                                   :error-handler app/server-error-handler}
                         #::ssl{:keystore        "dev-resources/my-keystore.jks"
                                :keystore-type   "PKCS12"
                                :key-password    "password"
                                :truststore      "dev-resources/my-truststore.jks"
                                :trust-password  "password"
                                :truststore-type "PKCS12"})
   :jaas-auth     #::authz{:realm               "slipway"
                           :login-service       "jaas"
                           :hash-user-file      "common/dev-resources/jaas/hash-realm.properties"
                           :authenticator       (FormAuthenticator. "/login" "/login-retry" false)
                           :constraint-mappings constraints}
   :hash-auth     #::authz{:realm               "slipway"
                           :login-service       "hash"
                           :hash-user-file      "common/dev-resources/jaas/hash-realm.properties"
                           :authenticator       (FormAuthenticator. "/login" "/login-retry" false)
                           :constraint-mappings constraints}
   :basic-auth    #::authz{:authenticator (BasicAuthenticator.)}
   :gzip-nil      #::server{:gzip? nil}
   :gzip-false    #::server{:gzip? false}
   :gzip-true     #::server{:gzip? true}
   :custom-ws     #::handler{:ws-path "/wsx"}
   :join          #::slipway{:join? true}
   :short-session #::session{:max-inactive-interval 10}})

(defn stop!
  []
  (when-let [server @state]
    (slipway/stop server)))

"To run a JAAS authenticated server, start a REPL with the following JVM JAAS parameter:
   - Hash User Auth  ->  -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf
   - LDAP Auth       ->  -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf

 E.g: (start! [:http :hash-auth :basic-auth])"
(defn start!
  [keys]
  (stop!)
  (reset! state (slipway/start (app/handler) (reduce (fn [ret k] (merge ret (get options k))) {} keys))))