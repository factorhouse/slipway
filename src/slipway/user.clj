(ns slipway.user
  (:refer-clojure :exclude [identity name])
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.security.user])
  (:import (java.time Instant)
           (org.eclipse.jetty.security AuthenticationState AuthenticationState$Succeeded)
           (org.eclipse.jetty.server Request Response)))

(extend-protocol p/Datafiable

  AuthenticationState$Succeeded
  (datafy [authentication-state]
    #::{:identity (p/datafy (.getUserIdentity authentication-state))}))

(defn identity
  [req]
  (::identity req))

(defn name
  [req]
  (-> req identity :name))

(defn roles
  [req]
  (-> req identity :roles))

(defn expired?
  ([req]
   (expired? req (Instant/now)))
  ([req ^Instant at]
   (when-let [^Instant expires-at (-> req identity :expires-at)]
     (.isBefore expires-at at))))

(defn logout
  [{:keys [^Request slipway.request/request ^Response slipway.request/response ::identity]}]
  (when request
    (try
      (log/debug "logout" (select-keys identity [:type :name]))
      (AuthenticationState/logout request response)
      (some-> (.getSession request false) (.invalidate))
      (catch Exception ex
        (log/error ex "logout error")))))