(ns slipway.auth
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log])
  (:import (java.util List)
           (org.eclipse.jetty.server Authentication$User)
           (javax.security.auth.login Configuration)        ;; Jetty9/10/11 all use javax in this specific case.
           (org.eclipse.jetty.jaas JAASLoginService)
           (org.eclipse.jetty.security Authenticator ConstraintSecurityHandler HashLoginService LoginService)
           (org.eclipse.jetty.server Authentication$User Request)))

(defmulti login-service :login-service)

(defn user
  "Derive user identity from a jetty base request"
  [^Request base-request]
  (when-let [authentication (.getAuthentication base-request)]
    (when (instance? Authentication$User authentication)
      (p/datafy authentication))))

(defmethod login-service "jaas"
  [{:keys [realm]}]
  (let [config (System/getProperty "java.security.auth.login.config")]
    (log/infof "initializing JAASLoginService -> realm: %s, java.security.auth.login.config: %s " realm config)
    (if config
      (when (slurp config)
        (doto (JAASLoginService. realm) (.setConfiguration (Configuration/getConfiguration))))
      (throw (ex-info (str "start with -Djava.security.auth.login.config=/some/path/to/jaas.config to use Jetty/JAAS auth provider") {})))))

(defmethod login-service "hash"
  [{:keys [realm hash-user-file]}]
  (log/infof "initializing HashLoginService -> realm: %s, realm file: %s" realm hash-user-file)
  (if hash-user-file
    (when (slurp hash-user-file)
      (HashLoginService. realm hash-user-file))
    (throw (ex-info (str "set the path to your hash user realm properties file") {}))))

(defn handler
  [^LoginService login-service {:keys [authenticator constraint-mappings]}]
  (doto (ConstraintSecurityHandler.)
    (.setConstraintMappings ^List constraint-mappings)
    (.setAuthenticator ^Authenticator authenticator)
    (.setLoginService login-service)))