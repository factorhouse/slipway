(ns slipway.user
  (:refer-clojure :exclude [name])
  (:require [clojure.core.protocols :as p]
            [slipway.common.auth :as auth])
  (:import (org.eclipse.jetty.jaas JAASPrincipal JAASRole JAASUserPrincipal)
           (org.eclipse.jetty.security RolePrincipal UserPrincipal)
           (org.eclipse.jetty.server Authentication$User UserIdentity)))

(extend-protocol p/Datafiable

  JAASUserPrincipal
  (datafy [user]
    {:type :user
     :name (.getName user)})

  JAASPrincipal
  (datafy [user]
    {:type :user
     :name (.getName user)})

  JAASRole
  (datafy [role]
    {:type :role
     :name (.getName role)})

  UserPrincipal
  (datafy [user]
    {:type :user
     :name (.getName user)})

  RolePrincipal
  (datafy [role]
    {:type :role
     :name (.getName role)})

  UserIdentity
  (datafy [identity]
    {:name  (:name (p/datafy (.getUserPrincipal identity)))
     :roles (->> (.getSubject identity)
                 (.getPrincipals)
                 (map p/datafy)
                 (filter #(= :role (:type %)))
                 (map :name)
                 set)})

  Authentication$User
  (datafy [user]
    {::credentials (p/datafy (.getUserIdentity ^Authentication$User user))}))

(defn credentials
  [req]
  (::credentials req))

(defn name
  [req]
  (-> req credentials :name))

(defn roles
  [req]
  (-> req credentials :roles))

(defn logout
  [req]
  (auth/logout req))