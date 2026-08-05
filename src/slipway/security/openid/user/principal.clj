(ns slipway.security.openid.user.principal
  (:require [clojure.tools.logging :as log]
            [slipway.principal :as principal]
            [slipway.security.openid :as-alias openid]
            [slipway.user :as user])
  (:gen-class
   :name slipway.security.openid.user.principal.OpenIdUserPrincipalWithState
   :extends org.eclipse.jetty.security.openid.OpenIdUserPrincipal
   :state state
   :init init
   :constructors {[org.eclipse.jetty.security.openid.OpenIdCredentials
                   clojure.lang.IPersistentMap
                   clojure.lang.IPersistentMap]
                  [org.eclipse.jetty.security.openid.OpenIdCredentials]}
   :methods [[redeemRefreshToken [] clojure.lang.IPersistentMap]
             [getState [] clojure.lang.IPersistentMap]]
   :prefix "-"))

(defn redeem-refresh-token
  [refresh-token-fn user-state-fn id-token response]
  ;; refresh-response may be nil if no refresh_token available in previous openid response
  (when-let [refresh-response (refresh-token-fn id-token response)]
    ;; id-token refresh is optional, so we fall-back to previous id-token if necessary
    (user-state-fn (or (::openid/id-token refresh-response) id-token)
                   (::openid/access-token refresh-response)
                   (::openid/response refresh-response))))

(defn refresh-user-state
  [state-atom]
  (let [{:keys [user-state refresh-fns]} @state-atom
        {:keys [refresh-token-fn user-state-fn]} refresh-fns
        {::openid/keys [id-token response]} user-state]
    (when (and refresh-token-fn (user/expired? user-state))
      (let [refreshed-user-state (redeem-refresh-token refresh-token-fn user-state-fn id-token response)]
        (swap! state-atom conj :user-state refreshed-user-state)
        refreshed-user-state))))

(defn -init
  [credentials user-state refresh-fns]
  [[credentials] (atom {:user-state user-state :refresh-fns refresh-fns})])

(defn -getName
  [this]
  (::principal/name (:user-state @(.state this))))

(defn -toString
  [this]
  (::principal/name (:user-state @(.state this))))

(defn -redeemRefreshToken
  [this]
  (try
    (let [state-atom (.state this)]
      (locking state-atom
        (refresh-user-state state-atom)))
    (catch Exception ex
      (log/debug ex "refresh token redemption failed")
      (or (ex-data ex)
          {:error {:cause ex}}))))

(defn -getState
  [this]
  (:user-state @(.state this)))

