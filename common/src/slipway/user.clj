(ns slipway.user
  (:refer-clojure :exclude [identity name])
  (:require [clojure.core.protocols :as p]
            [slipway.auth :as auth]
            [slipway.user.identity]
            [slipway.user.jaas]
            [slipway.user.principal])
  (:import (org.eclipse.jetty.server Authentication$User)))

(extend-protocol p/Datafiable

  Authentication$User
  (datafy [user]
    {::identity (p/datafy (.getUserIdentity ^Authentication$User user))}))

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
  [req]
  (auth/logout req))