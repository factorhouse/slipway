(ns slipway.user.openid
  (:require [clojure.core.protocols :as p])
  (:import (org.eclipse.jetty.security.openid OpenIdUserPrincipal)))

(extend-protocol p/Datafiable

  OpenIdUserPrincipal
  (datafy [user]
    {:type :user
     :name (.getName user)}))