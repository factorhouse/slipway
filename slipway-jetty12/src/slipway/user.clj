(ns slipway.user
  (:refer-clojure :exclude [identity name])
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.user.identity]
            [slipway.user.jaas]
            [slipway.user.principal])
  (:import (org.eclipse.jetty.security AuthenticationState AuthenticationState$Succeeded)
           (org.eclipse.jetty.server Request Response)))

(extend-protocol p/Datafiable

  AuthenticationState$Succeeded
  (datafy [user]
    #::{:identity (p/datafy (.getUserIdentity user))}))

(defn identity
  [req]
  (::identity req))

(defn name
  [req]
  (-> req identity :name))

(defn roles
  [req]
  (-> req identity :roles))

(defn logout
  [{:keys [^Request slipway.handler/request ^Response slipway.handler/response ::identity]}]
  (when request
    (try
      (log/debug "logout" identity)
      (AuthenticationState/logout request response)
      (some-> (.getSession request false) (.invalidate))
      (catch Exception ex
        (log/error ex "logout error")))))