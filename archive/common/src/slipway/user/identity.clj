(ns slipway.user.identity
  (:require [clojure.core.protocols :as p])
  (:import (org.eclipse.jetty.server UserIdentity)))

(extend-protocol p/Datafiable

  UserIdentity
  (datafy [identity]
    {:name  (:name (p/datafy (.getUserPrincipal identity)))
     :roles (->> (.getSubject identity)
                 (.getPrincipals)
                 (map p/datafy)
                 (filter #(= :role (:type %)))
                 (map :name)
                 set)}))
