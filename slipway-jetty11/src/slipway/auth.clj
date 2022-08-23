(ns slipway.auth
  (:require [clojure.core.protocols :as p]
            [slipway.common.auth :as common.auth])
  (:import (jakarta.servlet SessionTrackingMode)
           (org.eclipse.jetty.jaas JAASPrincipal JAASRole)
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

(defmethod common.auth/session-tracking-mode :cookie
  [_]
  SessionTrackingMode/COOKIE)

(defmethod common.auth/session-tracking-mode :url
  [_]
  SessionTrackingMode/URL)

(defmethod common.auth/session-tracking-mode :ssl
  [_]
  SessionTrackingMode/SSL)