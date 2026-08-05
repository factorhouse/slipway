(ns slipway.security.hash
  (:require [clojure.tools.logging :as log]
            [slipway.security :as security])
  (:import (org.eclipse.jetty.security Authenticator Constraint HashLoginService SecurityHandler$PathMapped UserStore)
           (org.eclipse.jetty.util.resource ResourceFactory)
           (org.eclipse.jetty.util.security Credential)))

(defn property-file-service
  [realm user-file hot-reload-interval-s]
  (when (slurp user-file)
    (let [service (HashLoginService. realm (.newResource (ResourceFactory/root) ^String user-file))]
      (when hot-reload-interval-s
        (.setReloadInterval service hot-reload-interval-s))
      service)))

(defn in-memory-service
  [realm users]
  (let [user-store   (UserStore.)
        hash-service (HashLoginService. realm)]
    (doseq [[user-name credential roles] users]
      (.addUser user-store user-name (Credential/getCredential credential) (into-array String roles)))
    (.setUserStore hash-service user-store)
    hash-service))

(defn login-service ^HashLoginService
  [{::keys [realm user-file hot-reload-interval-s users]}]
  (log/debugf "initializing HashLoginService - realm: %s, realm file: %s, users: %s realm" realm user-file (count users))
  (cond
    user-file (property-file-service realm user-file hot-reload-interval-s)
    users (in-memory-service realm users)
    :else (throw (ex-info "provide a :realm and either :user-file or :users configuration" {}))))

(comment
  #:slipway.security.hash{:realm                 "optional Jetty authentication realm"
                          :user-file             "the path to a Jetty hash-user file"
                          :hot-reload-interval-s "the period in seconds to scan :user-file for changes"
                          :users                 "a sequence of [^String user-name, ^String credential, ^String[] [roles]]"
                          :authenticator         "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
                          :constraint-mappings   "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"
                          :identity-service      "an (optional) concrete Jetty IdentityService"})

(defmethod security/handler :hash
  [{::keys [realm authenticator constraint-mappings identity-service] :as opts}]
  (log/debugf "creating hash security handler with authenticator %s and %s constraints" (type authenticator) (count constraint-mappings))
  (if-let [login-service (login-service opts)]
    (let [security-handler (doto (SecurityHandler$PathMapped.)
                             (.setAuthenticator ^Authenticator authenticator)
                             (.setLoginService login-service)
                             (.setRealmName realm))]
      (doseq [[^String path-spec ^Constraint constraint] constraint-mappings]
        (.put security-handler path-spec constraint))
      (when identity-service
        (log/debugf "identity service %s" (type identity-service))
        (.setIdentityService security-handler identity-service))
      security-handler)
    (log/warn "unable to create login-service from provided configuration")))