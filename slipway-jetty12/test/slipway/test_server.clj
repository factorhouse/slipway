(ns slipway.test-server
  "This ns contains helper functions for stopping/starting test servers.
   Feel free to add any further configuration in the same style."
  (:require [slipway :as slipway]
            [slipway.connector.http :as http]
            [slipway.connector.https :as https]
            [slipway.example.app :as app]
            [slipway.handler.compression :as compression]
            [slipway.security :as security]
            [slipway.sente]
            [slipway.server :as server]
            [slipway.session :as session]
            [slipway.websockets :as websockets])
  (:import (org.eclipse.jetty.security.authentication BasicAuthenticator FormAuthenticator)))

(def state (atom nil))

(def http-forwarded #::http{:http-forwarded? true})
(def http-proxied #::http{:proxy-protocol? true})

(def http-connector #::http{:port 3000})

(def https-forwarded #::https{:http-forwarded? true})
(def https-proxied #::https{:proxy-protocol? true})

(def https-connector #::https{:port                3443
                              :keystore            "dev-resources/my-keystore.jks"
                              :keystore-type       "PKCS12"
                              :keystore-password   "password"
                              :truststore          "dev-resources/my-truststore.jks"
                              :truststore-password "password"
                              :truststore-type     "PKCS12"})

(def hsts #::https{:sts-max-age-s           31536000
                   :sts-include-subdomains? true})

(def hsts-no-subdomains #::https{:sts-max-age-s 31536000})

(def hsts-no-max-age #::https{:sts-include-subdomains? true})

(def options
  {:http                 #::server{:connectors    [http-connector]
                                   :error-handler app/server-error-handler}

   :websockets           #::websockets{:path-spec "/chsk"}

   :https                #::server{:connectors    [https-connector]
                                   :error-handler app/server-error-handler}

   :hsts                 #::server{:connectors    [(merge https-connector hsts)]
                                   :error-handler app/server-error-handler}

   :hsts-no-subdomains   #::server{:connectors    [(merge https-connector hsts-no-subdomains)]
                                   :error-handler app/server-error-handler}

   ;; this is an error condition / incorrect configuration - subdomains requires max-age set
   :hsts-no-max-age      #::server{:connectors    [(merge https-connector hsts-no-max-age)]
                                   :error-handler app/server-error-handler}

   :http+https           #::server{:connectors    [http-connector https-connector]
                                   :error-handler app/server-error-handler}

   :http+https+forwarded #::server{:connectors    [(merge http-connector http-forwarded)
                                                   (merge https-connector https-forwarded)]
                                   :error-handler app/server-error-handler}

   :http+https+proxied   #::server{:connectors    [(merge http-connector http-proxied)
                                                   (merge https-connector https-proxied)]
                                   :error-handler app/server-error-handler}

   :compression-nil      #::compression{:enabled? nil}

   :compression-false    #::compression{:enabled? false}

   :compression-true     #::compression{:enabled? true}

   :short-session        #::session{:max-inactive-interval-s 10}

   :join                 #::slipway{:join? true}})

(defmulti authentication identity)

(defmethod authentication :default
  [_]
  {})

(defmethod authentication :jaas-auth
  [_]
  #::security{:realm               "slipway"
              :login-service       "jaas"
              :authenticator       (FormAuthenticator. "/login" "/login-retry" false)
              :constraint-mappings app/constraints})

(defmethod authentication :hash-auth
  [_]
  #::security{:realm               "slipway"
              :login-service       "hash"
              :hash-user-file      "dev-resources/jaas/hash-realm.properties"
              :authenticator       (FormAuthenticator. "/login" "/login-retry" false)
              :constraint-mappings app/constraints})

(defmethod authentication :basic-auth
  [_]
  #::security{:realm               "slipway"
              :login-service       "hash"
              :hash-user-file      "dev-resources/jaas/hash-realm.properties"
              :authenticator       (BasicAuthenticator.)
              :constraint-mappings app/constraints})

(defn stop!
  []
  (when-let [server @state]
    (slipway/stop server)))

"To run a JAAS authenticated server, start a REPL with the following JVM JAAS parameter:
   - Hash User Auth  ->  -Djava.security.auth.login.config=/dev-resources/jaas/hash-jaas.conf
   - LDAP Auth       ->  -Djava.security.auth.login.config=/dev-resources/jaas/ldap-jaas.conf
 Then: (start! [:http] :jaas-auth)

 Note: Authentication loginHandlers are stateful, so they must be created fresh for each server"
(defn start!
  ([keys]
   (start! keys nil))
  ([keys auth]
   (stop!)
   (reset! state (slipway/start (app/handler)
                                (merge (reduce (fn [ret k] (merge ret (get options k))) {} keys)
                                       (authentication auth))))))