(ns slipway.example
  (:require [clojure.test :refer :all]
            [slipway :as slipway]
            [slipway.auth :as auth]
            [slipway.connector.http :as http]
            [slipway.connector.https :as https]
            [slipway.example.app :as app]
            [slipway.handler :as handler]
            [slipway.handler.gzip :as gzip]
            [slipway.server :as server]
            [slipway.session :as session])
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

(def form-authenticator (FormAuthenticator. "/login" "/login-retry" false))

(def options
  {:http                 #::server{:connectors    [http-connector]
                                   :error-handler app/server-error-handler}

   :https                #::server{:connectors    [https-connector]
                                   :error-handler app/server-error-handler}

   :http+https           #::server{:connectors    [http-connector https-connector]
                                   :error-handler app/server-error-handler}

   :http+https+forwarded #::server{:connectors    [(merge http-connector http-forwarded)
                                                   (merge https-connector https-forwarded)]
                                   :error-handler app/server-error-handler}

   :http+https+proxied   #::server{:connectors    [(merge http-connector http-proxied)
                                                   (merge https-connector https-proxied)]
                                   :error-handler app/server-error-handler}

   :jaas-auth            #::auth{:realm               "slipway"
                                 :login-service       "jaas"
                                 :authenticator       form-authenticator
                                 :constraint-mappings app/constraints}

   :hash-auth            #::auth{:realm               "slipway"
                                 :login-service       "hash"
                                 :hash-user-file      "common/dev-resources/jaas/hash-realm.properties"
                                 :authenticator       form-authenticator
                                 :constraint-mappings app/constraints}

   :basic-auth           #::auth{:authenticator (BasicAuthenticator.)}

   :gzip-nil             #::gzip{:enabled? nil}

   :gzip-false           #::gzip{:enabled? false}

   :gzip-true            #::gzip{:enabled? true}

   :custom-ws            #::handler{:ws-path "/wsx"}

   :short-session        #::session{:max-inactive-interval 10}

   :join                 #::slipway{:join? true}})

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