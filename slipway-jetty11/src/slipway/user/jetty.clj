(ns slipway.user.jetty
  (:require [clojure.core.protocols :as p])
  (:import (org.eclipse.jetty.security RolePrincipal UserPrincipal)))

(extend-protocol p/Datafiable

  UserPrincipal
  (datafy [user]
    {:type :user
     :name (.getName user)})

  RolePrincipal
  (datafy [role]
    {:type :role
     :name (.getName role)}))