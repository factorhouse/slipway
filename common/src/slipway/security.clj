(ns slipway.security
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log])
  (:import (java.util List)
           (org.eclipse.jetty.server Authentication$User)
           (javax.security.auth.login Configuration)        ;; Jetty9/10/11 all use javax in this specific class.
           (org.eclipse.jetty.jaas JAASLoginService)
           (org.eclipse.jetty.security Authenticator ConstraintSecurityHandler HashLoginService LoginService SecurityHandler)
           (org.eclipse.jetty.server Authentication$User Request)))

(defmulti login-service ::login-service)

(defmethod login-service :default [_] nil)

(defmethod login-service "jaas"
  [{::keys [realm]}]
  (let [config (System/getProperty "java.security.auth.login.config")]
    (log/infof "initializing JAASLoginService - realm: %s, java.security.auth.login.config: %s " realm config)
    (if config
      (when (slurp config)
        (doto (JAASLoginService. realm) (.setConfiguration (Configuration/getConfiguration))))
      (throw (ex-info "start with -Djava.security.auth.login.config=/some/path/to/jaas.config to use Jetty/JAAS auth provider" {})))))

(defmethod login-service "hash"
  [{::keys [realm hash-user-file]}]
  (log/infof "initializing HashLoginService - realm: %s, realm file: %s" realm hash-user-file)
  (if hash-user-file
    (when (slurp hash-user-file)
      (HashLoginService. realm hash-user-file))
    (throw (ex-info "set the path to your hash user realm properties file" {}))))

(defn user
  [^Request base-request]
  (when-let [authentication (.getAuthentication base-request)]
    (when (instance? Authentication$User authentication)
      (p/datafy authentication))))

(comment
  #:slipway.security{:realm               "the Jetty authentication realm"
                     :hash-user-file      "the path to a Jetty Hash User File"
                     :login-service       "a Jetty LoginService identifier, 'jaas' and 'hash' supported by default"
                     :identity-service    "a concrete Jetty IdentityService"
                     :authenticator       "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
                     :constraint-mappings "a list of concrete Jetty ConstraintMapping"})

(defn handler ^SecurityHandler
  [^LoginService login-service {::keys [realm authenticator constraint-mappings identity-service]}]
  (log/infof "authenticator %s with %s constraints" (type authenticator) (count constraint-mappings))
  (let [security-handler (doto (ConstraintSecurityHandler.)
                           (.setConstraintMappings ^List constraint-mappings)
                           (.setAuthenticator ^Authenticator authenticator)
                           (.setLoginService login-service)
                           (.setRealmName realm))]
    (when identity-service
      (log/infof "identity service %s" (type identity-service))
      (.setIdentityService security-handler identity-service))
    security-handler))