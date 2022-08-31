(ns slipway.user
  (:refer-clojure :exclude [name])
  (:require [clojure.core.protocols :as p]
            [slipway.common.auth :as auth]
            [slipway.user.jaas]
            [slipway.user.jetty])
  (:import (org.eclipse.jetty.server Authentication$User UserIdentity)))

(extend-protocol p/Datafiable

  UserIdentity
  (datafy [identity]
    {:name  (:name (p/datafy (.getUserPrincipal identity)))
     :roles (->> (.getSubject identity)
                 (.getPrincipals)
                 (map p/datafy)
                 (filter #(= :role (:type %)))
                 (map :name)
                 set)})

  Authentication$User
  (datafy [user]
    {::credentials (p/datafy (.getUserIdentity ^Authentication$User user))}))

(defn credentials
  [req]
  (::credentials req))

(defn name
  [req]
  (-> req credentials :name))

(defn roles
  [req]
  (-> req credentials :roles))

(defn logout
  [req]
  (auth/logout req))