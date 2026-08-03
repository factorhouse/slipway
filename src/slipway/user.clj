(ns slipway.user
  (:refer-clojure :exclude [identity name type])
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.principal :as principal]
            [slipway.security.openid :as openid])
  (:import (java.time Instant)
           (org.eclipse.jetty.security AuthenticationState AuthenticationState$Succeeded RolePrincipal UserIdentity UserPrincipal)
           (org.eclipse.jetty.server Request Response)))

(extend-protocol p/Datafiable

  AuthenticationState$Succeeded
  (datafy [authentication-state]
    #::{:identity (p/datafy (.getUserIdentity authentication-state))})

  UserIdentity
  (datafy [identity]
    (let [user-principal (p/datafy (.getUserPrincipal identity))]
      (if (= (principal/type user-principal) ::openid/principal)
        user-principal                                      ;; OpenID UserIdentity
        (merge user-principal                               ;; Jaas/LDAP/Hash UserIdentity
               {::roles (->> (.getSubject identity)
                             (.getPrincipals)
                             (map p/datafy)
                             (filter #(= ::role (principal/type %)))
                             (map principal/name)
                             set)}))))

  UserPrincipal
  (datafy [principal]
    (principal/from ::principal (.getName principal)))

  RolePrincipal
  (datafy [role]
    (principal/from ::role (.getName role))))

(defn identity
  [req]
  (::identity req))

(defn type
  [request]
  (-> request identity principal/type))

(defn name
  [req]
  (-> req identity principal/name))

(defn roles
  [req]
  (-> req identity ::roles))

(defn expired?
  ([req]
   (expired? req (Instant/now)))
  ([req ^Instant at]
   (when-let [^Instant expires-at (-> req identity ::expires-at)]
     (.isBefore expires-at at))))

(defn logout
  [{:keys [^Request slipway.request/request ^Response slipway.request/response ::identity]}]
  (when request
    (try
      (log/debug "logout" (select-keys identity [::type ::name]))
      (AuthenticationState/logout request response)
      (some-> (.getSession request false) (.invalidate))
      (catch Exception ex
        (log/error ex "logout error")))))