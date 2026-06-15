(ns slipway.security.openid
  (:require [clojure.tools.logging :as log])
  (:import (org.eclipse.jetty.security Authenticator Constraint
                                       LoginService SecurityHandler SecurityHandler$PathMapped)))

(defn login-service "openid"
  [{::keys []}]
  )

(comment
  #:slipway.security{:realm               "the Jetty authentication realm"
                     :hash-user-file      "the path to a Jetty Hash User File"
                     :login-service       "a Jetty LoginService identifier, 'jaas' and 'hash' supported by default"
                     :identity-service    "a concrete Jetty IdentityService"
                     :authenticator       "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
                     :constraint-mappings "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"})

(defn handler ^SecurityHandler
  [{::keys [realm authenticator constraint-mappings identity-service] :as opts}]
  (log/debugf "creating security handler with authenticator %s and %s constraints" (type authenticator) (count constraint-mappings))
  (when-let [^LoginService login-service (login-service opts)]
    (let [security-handler (doto (SecurityHandler$PathMapped.)
                             (.setAuthenticator ^Authenticator authenticator)
                             (.setLoginService login-service)
                             (.setRealmName realm))]
      (doseq [[^String path-spec ^Constraint constraint] constraint-mappings]
        (.put security-handler path-spec constraint))
      (when identity-service
        (log/debugf "identity service %s" (type identity-service))
        (.setIdentityService security-handler identity-service))
      security-handler)))