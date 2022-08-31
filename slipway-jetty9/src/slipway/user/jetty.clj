(ns slipway.user.jetty
  (:require [clojure.core.protocols :as p])
  (:import (org.eclipse.jetty.security AbstractLoginService$RolePrincipal AbstractLoginService$UserPrincipal)))

(extend-protocol p/Datafiable

  AbstractLoginService$UserPrincipal
  (datafy [user]
    {:type :user
     :name (.getName user)})

  AbstractLoginService$RolePrincipal
  (datafy [role]
    {:type :role
     :name (.getName role)}))