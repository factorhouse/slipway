(ns slipway.user
  (:refer-clojure :exclude [identity name])
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.user.identity]
            [slipway.user.jaas]
            [slipway.user.principal])
  (:import (org.eclipse.jetty.server Authentication$User Request)))

(extend-protocol p/Datafiable

  Authentication$User
  (datafy [user]
    #::{:identity (p/datafy (.getUserIdentity ^Authentication$User user))}))

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
  "Logout user and invalidate the session"
  [{:keys [^Request slipway.handler/base-request ::identity]}]
  (when base-request
    (try
      (log/debug "logout" identity)
      (.logout base-request)
      (.invalidate (.getSession base-request))
      (catch Exception ex
        (log/error ex "logout error")))))