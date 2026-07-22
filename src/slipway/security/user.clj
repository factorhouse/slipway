(ns slipway.security.user
  (:require [clojure.core.protocols :as p])
  (:import (org.eclipse.jetty.security RolePrincipal UserIdentity UserPrincipal)))

(extend-protocol p/Datafiable

  UserIdentity
  (datafy [identity]
    (let [{:keys [type] :as principal} (p/datafy (.getUserPrincipal identity))]
      (if (= type :slipway.security.openid/principal)
        principal                                           ;; OpenID UserIdentity
        {:name  (:name principal)                           ;; Jaas/LDAP/Hash UserIdentity
         :roles (or (:roles principal)
                    (->> (.getSubject identity)
                         (.getPrincipals)
                         (map p/datafy)
                         (filter #(= :role (:type %)))
                         (map :name)
                         set))})))

  UserPrincipal
  (datafy [principal]
    {:type ::principal
     :name (.getName principal)})

  RolePrincipal
  (datafy [role]
    {:type :role
     :name (.getName role)}))
