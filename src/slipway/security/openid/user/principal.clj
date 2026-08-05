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
  [{::openid/keys [id-token response] :as user-state} {:keys [refresh-token-fn user-state-fn]}]
  (if refresh-token-fn
    (if (user/expired? user-state)
      (if-let [refresh-result (refresh-token-fn id-token response)]
        (user-state-fn (or (::openid/id-token refresh-result) id-token) ;; id-token refresh is optional
                       (::openid/access-token refresh-result)
                       (::openid/response refresh-result)))
      (log/debug "user is not expired"))
    (log/debug "no refresh-token-fn configured")))

(defn refresh-user-state
  "Refresh the user state, will return:
   - nil if no action was performed
   - map with :error key if any error occurs
   - map of refreshed user state if refresh successful
   The internal state atom is updated with refreshed user state on succesfull refresh only"
  [state-atom]
  (try
    (let [{:keys [user-state refresh-fns]} @state-atom]
      (log/debug "attempting to refresh user state")
      (when-let [new-user-state (redeem-refresh-token user-state refresh-fns)]
        (swap! state-atom conj :user-state new-user-state)
        new-user-state))
    (catch Exception ex
      (log/debug ex "refresh token redemption failed")
      (or (ex-data ex)
          {:error {:cause ex}}))))

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
  (let [state-atom (.state this)]
    (locking state-atom (refresh-user-state state-atom))))

(defn -getState
  [this]
  (:user-state @(.state this)))

