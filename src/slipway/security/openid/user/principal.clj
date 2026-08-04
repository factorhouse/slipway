(ns slipway.security.openid.user.principal
  (:require [slipway.principal :as principal]
            [slipway.security.openid :as-alias openid])
  (:gen-class
   :name slipway.security.openid.user.principal.OpenIdUserPrincipalWithState
   :extends org.eclipse.jetty.security.openid.OpenIdUserPrincipal
   :state state
   :init init
   :constructors {[org.eclipse.jetty.security.openid.OpenIdCredentials
                   clojure.lang.IPersistentMap
                   clojure.lang.IFn]
                  [org.eclipse.jetty.security.openid.OpenIdCredentials]}
   :methods [[redeemRefreshToken [] boolean]
             [getState [] clojure.lang.IPersistentMap]]
   :prefix "-"))

(defn -init
  [credentials user-state refresh-token-fn]
  [[credentials] (atom {:user-state user-state :refresh-token-fn refresh-token-fn})])

(defn -getName
  [this]
  (::principal/name (:user-state @(.state this))))

(defn -toString
  [this]
  (::principal/name (:user-state @(.state this))))

(defn -redeemRefreshToken
  [this]
  (let [{:keys [user-state refresh-token-fn]} @(.state this)]
    (let [new-request (refresh-token-fn (::openid/response user-state))])))

(defn -getState
  [this]
  (:user-state @(.state this)))

