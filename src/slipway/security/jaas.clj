(ns slipway.security.jaas
  (:require [clojure.tools.logging :as log]
            [slipway.security :as security])
  (:import (javax.security.auth.login Configuration)
           (org.eclipse.jetty.security Authenticator Constraint SecurityHandler$PathMapped)
           (org.eclipse.jetty.security.jaas JAASLoginService)))

(defn login-service ^JAASLoginService
  [{::keys [realm]}]
  (let [config (System/getProperty "java.security.auth.login.config")]
    (log/debugf "initializing JAASLoginService - realm: %s, java.security.auth.login.config: %s " realm config)
    (if config
      (when (slurp config)
        (doto (JAASLoginService. realm) (.setConfiguration (Configuration/getConfiguration))))
      (throw (ex-info "start with -Djava.security.auth.login.config=/some/path/to/jaas.config to use Jetty/JAAS auth provider" {})))))

(comment
  #:slipway.security.jaas{:realm               "the Jetty authentication realm"
                          :authenticator       "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
                          :constraint-mappings "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"
                          :identity-service    "a concrete Jetty IdentityService"})

(defmethod security/handler "jaas"
  [{::keys [realm authenticator constraint-mappings identity-service] :as opts}]
  (log/debugf "creating security handler with authenticator %s and %s constraints" (type authenticator) (count constraint-mappings))
  (when-let [login-service (login-service opts)]
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