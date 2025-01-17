(ns slipway.user.jaas
  (:require [clojure.core.protocols :as p])
  (:import (org.eclipse.jetty.security.jaas JAASPrincipal JAASRole JAASUserPrincipal)))

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
     :name (.getName role)}))
