(ns slipway.user
  (:refer-clojure :exclude [identity name])
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.user.identity]
            [slipway.user.jaas]
            [slipway.user.principal])
  (:import (org.eclipse.jetty.security AuthenticationState$Succeeded)
           (org.eclipse.jetty.server Request)))

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
  [{:keys [^Request slipway.handler/base-request ::identity]}]
  (when base-request
    (try
      (log/debug "logout" identity)
      (.logout base-request)
      (some-> (.getSession base-request false) (.invalidate))
      (catch Exception ex
        (log/error ex "logout error")))))