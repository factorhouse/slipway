(ns slipway.example.server.jaas
  (:require [clojure.test :refer :all]
            [reitit.ring :as reitit.ring]
            [slipway.common.auth.constraints :as constraints]
            [slipway.example.handler :as handler]
            [slipway.server :as slipway]))

(def routes
  [""
   ["/up" {:get {:handler handler/up}}]
   ["/login" {:get {:handler handler/login}}]
   ["/login-retry" {:get {:handler handler/login-retry}}]
   ["/logout" {:get {:handler handler/logout}}]
   ["/" {:get {:handler handler/home}}]
   ["/user" {:get {:handler handler/user}}]
   ["/405" {:get {:handler handler/error-405}}]
   ["/406" {:get {:handler handler/error-406}}]
   ["/500" {:get {:handler handler/error-route}}]])

(def opts
  {:auth {:realm               "slipway"
          :login-uri           "/login"
          :logout-uri          "/logout"
          :login-retry-uri     "/login-retry"
          :post-login-uri-attr "org.eclipse.jetty.security.form_URI"
          :auth-method         "form"
          :auth-type           "jaas"
          ;; first constraint wins, so this applies auth to anything not explicitly listed prior
          :constraint-mappings (constraints/constraint-mappings
                                ["/up" (constraints/no-auth)]
                                ["/css/*" (constraints/no-auth)]
                                ["/img/*" (constraints/no-auth)]
                                ["/favicon.ico" (constraints/no-auth)]
                                ["/*" (constraints/form-auth-any-constraint)])}})

(defn handler
  []
  (-> (reitit.ring/ring-handler
       (reitit.ring/router routes)
       (reitit.ring/routes
        (reitit.ring/create-resource-handler {:path "/"})
        (reitit.ring/create-default-handler
         {:not-found          handler/error-404
          :method-not-allowed handler/error-405
          :not-acceptable     handler/error-406})))
      (handler/wrap-errors)))

(defn server
  "Start a REPL with the following JVM JAAS parameter:
    - Hash User Auth  ->  -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf
    - LDAP Auth       ->  -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf"
  []
  (slipway/run-jetty (handler) opts))