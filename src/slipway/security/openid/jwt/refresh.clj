(ns slipway.security.openid.jwt.refresh
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [slipway.security.openid :as openid]
            [slipway.security.openid.jwt :as jwt])
  (:import (java.util Map)
           (java.util.concurrent TimeUnit)
           (org.eclipse.jetty.client FormRequestContent HttpClient)
           (org.eclipse.jetty.security.openid OpenIdConfiguration OpenIdCredentials$AuthenticationException)
           (org.eclipse.jetty.util Fields)
           (org.eclipse.jetty.util.ajax JSON)))

(defn claim-refresh-token
  [^HttpClient http-client token-endpoint client-id client-secret scope refresh-token]
  (log/debug "redeem refresh token")
  (let [fields (Fields.)]
    (.add fields "grant_type" "refresh_token")
    (.add fields "refresh_token" ^String refresh-token)
    (.add fields "client_id" ^String client-id)
    (.add fields "client_secret" ^String client-secret)
    (.add fields "scope" ^String scope)
    (let [response-content (-> (.POST http-client ^String token-endpoint)
                               (.body (FormRequestContent. fields))
                               (.timeout 10 TimeUnit/SECONDS)
                               (.send)
                               (.getContentAsString))
          response-data    (.fromJSON (JSON.) response-content)]
      (if (instance? Map response-data)
        response-data
        (throw (OpenIdCredentials$AuthenticationException. "malformed refresh token response"))))))

(defn check-response
  [response]
  (when (get response "error")
    (throw (ex-info "refresh token error" {:error response})))
  (when-not (get response "access_token")
    (throw (OpenIdCredentials$AuthenticationException. "no refresh access_token")))
  (when-not (.equalsIgnoreCase "Bearer" (get response "token_type"))
    (throw (OpenIdCredentials$AuthenticationException. "invalid refresh token_token")))
  response)

(defn validate-id-token-claims
  [original-id-token {:strs [iss sub aud azp]}]
  (when-not (= (get original-id-token "iss") iss)
    (throw (OpenIdCredentials$AuthenticationException. "refreshed id-token 'iss' field differs from original")))
  (when-not (= (get original-id-token "sub") sub)
    (throw (OpenIdCredentials$AuthenticationException. "refreshed id-token 'sub' field differs from original")))
  (when-not (= (get original-id-token "aud") aud)
    (throw (OpenIdCredentials$AuthenticationException. "refreshed id-token 'aud' field differs from original")))
  (when-not (= (get original-id-token "azp") azp)
    (throw (OpenIdCredentials$AuthenticationException. "refreshed id-token 'azp' field differs from original"))))

;; https://openid.net/specs/openid-connect-core-1_0.html#RefreshTokenResponse
;; function similar to OpenIDCredentials/redeemAuthCode behaviour
(defn redeem-token-fn
  [^OpenIdConfiguration openid-config]
  (fn [original-id-token {:strs [refresh_token scope]}]
    (log/debug "redeem refresh token")
    (if-not (str/blank? refresh_token)
      (let [response     (-> (claim-refresh-token
                              (.getHttpClient openid-config)
                              (.getTokenEndpoint openid-config)
                              (.getClientId openid-config)
                              (.getClientSecret openid-config)
                              (or scope                     ;; prefer scope from response
                                  (->> (.getScopes openid-config) ;; or reconstruct from original configuration
                                       (cons "openid")
                                       (str/join " ")))
                              refresh_token)
                             check-response)
            id-token     (some-> (get response "id_token") jwt/decode) ;; id-token is optional in refresh response
            access-token (jwt/decode (get response "access_token"))] ;; access-token is required in refresh response
        (when id-token (validate-id-token-claims original-id-token id-token))
        {::openid/id-token     id-token
         ::openid/access-token access-token
         ::openid/response     response})
      (log/debug "no refresh token provided"))))