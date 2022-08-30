(ns slipway.auth
  (:require [clojure.core.protocols :as p]
            [slipway.common.auth :as common.auth])
  (:import (javax.servlet SessionTrackingMode)
           (org.eclipse.jetty.jaas JAASPrincipal JAASRole)
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

(defmethod common.auth/session-tracking-mode :cookie
  [_]
  SessionTrackingMode/COOKIE)

(defmethod common.auth/session-tracking-mode :url
  [_]
  SessionTrackingMode/URL)

(defmethod common.auth/session-tracking-mode :ssl
  [_]
  SessionTrackingMode/SSL)