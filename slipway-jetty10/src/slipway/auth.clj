(ns slipway.auth
  (:require [clojure.core.protocols :as p])
  (:import (org.eclipse.jetty.jaas JAASPrincipal JAASRole)
           (org.eclipse.jetty.security RolePrincipal UserPrincipal)))

(extend-protocol p/Datafiable
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
     :name (.getName role)}))