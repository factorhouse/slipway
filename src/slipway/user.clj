(ns slipway.user
  (:refer-clojure :exclude [identity name type])
  (:require [clojure.core.protocols :as p]
            [slipway.principal :as principal]
            [slipway.security.oidc :as-alias oidc])
  (:import (java.time Instant)
           (java.time.temporal ChronoUnit)
           (org.eclipse.jetty.security AuthenticationState$Succeeded RolePrincipal UserIdentity UserPrincipal)))

(extend-protocol p/Datafiable

  AuthenticationState$Succeeded
  (datafy [authentication-state]
    (p/datafy (.getUserIdentity authentication-state)))

  UserIdentity
  (datafy [identity]
    (let [user-principal (p/datafy (.getUserPrincipal identity))]
      (if (= (principal/type user-principal) ::oidc/principal)
        user-principal                                      ;; OIDC UserIdentity
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

(def type principal/type)

(def name principal/name)

(defn roles
  "Return the set of roles assigned to a user"
  [user]
  (::roles user))

(defn in-role?
  [{::keys [roles]} role]
  (when roles
    (roles role)))

(defn expires-in
  "Seconds until user expiry:
    - nil if no ::expires-at available for the user
    - pos? if the user has not expired
    - neg? or zero? if the user is expired"
  ([user]
   (expires-in user (Instant/now)))
  ([user ^Instant now]
   (when-let [^Instant expires-at (::expires-at user)]
     (.between ChronoUnit/SECONDS now expires-at))))

(defn expired?
  ([user]
   (expired? user (Instant/now)))
  ([user ^Instant now]
   (when-let [^Instant expires-at (::expires-at user)]
     (.isBefore expires-at now))))