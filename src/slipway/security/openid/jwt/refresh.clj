(ns slipway.security.openid.jwt.refresh
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (java.util Map)
           (java.util.concurrent TimeUnit)
           (org.eclipse.jetty.client FormRequestContent HttpClient)
           (org.eclipse.jetty.security.openid OpenIdConfiguration)
           (org.eclipse.jetty.util Fields)
           (org.eclipse.jetty.util.ajax JSON)))

(defn redeem-token
  [^HttpClient http-client token-endpoint client-id client-secret scope refresh-token]
  (try
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
          (log/debug "redeem refresh token malformed" response-content))))
    (catch Exception ex
      (log/debug ex "failed to redeem refresh token"))))

(defn redeem-token-fn
  [^OpenIdConfiguration openid-config]
  (fn [{:strs [refresh_token scope]}]
    (if-not (str/blank? refresh_token)
      (redeem-token (.getHttpClient openid-config)
                    (.getTokenEndpoint openid-config)
                    (.getClientId openid-config)
                    (.getClientSecret openid-config)
                    (or scope                               ;; prefer scope from response
                        (->> (.getScopes openid-config)     ;; or reconstruct from original configuration
                             (cons "openid")
                             (str/join " ")))
                    refresh_token)
      (log/debug "missing refresh token"))))