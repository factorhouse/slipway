(ns slipway.auth
  (:require [clojure.core.protocols :as p])
  (:import (org.eclipse.jetty.jaas JAASPrincipal JAASRole)
           (org.eclipse.jetty.security AbstractLoginService$RolePrincipal AbstractLoginService$UserPrincipal)))

(extend-protocol p/Datafiable
  JAASPrincipal
  (datafy [user]
    {:type :user
     :name (.getName user)})

  JAASRole
  (datafy [role]
    {:type :role
     :name (.getName role)})

  AbstractLoginService$UserPrincipal
  (datafy [user]
    {:type :user
     :name (.getName user)})

  AbstractLoginService$RolePrincipal
  (datafy [role]
    {:type :role
     :name (.getName role)}))