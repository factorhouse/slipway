(ns slipway.security.openid.user.principal
  (:require [slipway.principal :as principal])
  (:gen-class
   :name slipway.security.openid.user.principal.OpenIdUserPrincipalWithState
   :extends org.eclipse.jetty.security.openid.OpenIdUserPrincipal
   :state state
   :init init
   :constructors {[org.eclipse.jetty.security.openid.OpenIdCredentials clojure.lang.IPersistentMap]
                  [org.eclipse.jetty.security.openid.OpenIdCredentials]}
   :methods [[redeemRefreshToken [] boolean]
             [getState [] clojure.lang.IPersistentMap]]
   :prefix "-"))

(defn -init
  [credentials state]
  [[credentials] (atom state)])

(defn -getName
  [this]
  (::principal/name @(.state this)))

(defn -toString
  [this]
  (::principal/name @(.state this)))

(defn -redeemRefreshToken
  [this]
  (if-let [_refresh-token (:slipway.security.openid/refresh-token @(.state this))]
    ;; TODO: implement refresh logic and swap state
    true
    false))

(defn -getState
  [this]
  @(.state this))

