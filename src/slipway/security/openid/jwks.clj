(ns slipway.security.openid.jwks
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (com.nimbusds.jose.jwk.source JWKSource JWKSourceBuilder)
           (java.net URI)))

;; This configuration is interesting because many of them are required to be input as pairs.
;; The user should familiarise themselves with the underlying builder implementation.
(comment
  #:slipway.security.openid.jwks{:endpoint                  "the jwks endpoint url"
                                 :cache?                    "enable caching of the jwks set"
                                 :cache-ttl                 "the time to live of the cached JWK set, in milliseconds"
                                 :cache-refresh-timeout     "the cache refresh timeout, in milliseconds."
                                 :cache-forever?            "enable caching of the jwks set without expiration"
                                 :refresh-ahead-cache?      "enable refresh-ahead caching of the JWK set"
                                 :refresh-ahead-time        "the refresh ahead time, in milliseconds"
                                 :scheduled?                "refresh in a scheduled manner, regardless of requests"
                                 :rate-limited?             "rate limit the JWK set retrieval"
                                 :rate-limited-min-interval "the minimum allowed time interval between two JWK set retrievals"
                                 :retrying?                 "enables single retrial to retrieve the JWK set to work around transient network issues"
                                 :outage-tolerant?          "enable outage tolerance by serving a cached JWK set in case of outage"
                                 :outage-tolerant-forever?  "enable outage tolerance without expiration"
                                 :outage-tolerant-ttl       "the time to live of the cached JWK set to cover outages, in milliseconds"})

(defn source ^JWKSource
  [{::keys [endpoint cache? cache-ttl cache-refresh-timeout cache-forever? refresh-ahead-cache? refresh-ahead-time
            scheduled? rate-limited? rate-limited-min-interval retrying? outage-tolerant? outage-tolerant-forever?
            outage-tolerant-ttl]}]
  (log/debugf "creating jwks source with endpoint %s" endpoint)
  (if (str/blank? endpoint)
    (throw (ex-info "required jwks endpoint url is missing" {}))
    (let [builder (JWKSourceBuilder/create (.toURL (URI. endpoint)))]
      (when cache?
        (.cache builder true))
      (when (and cache-ttl cache-refresh-timeout)
        (.cache builder cache-refresh-timeout))
      (when cache-forever?
        (.cacheForever builder))
      (when refresh-ahead-cache?
        (.refreshAheadCache builder true))
      (when (and refresh-ahead-time (some? scheduled?))
        (.refreshAheadCache refresh-ahead-time scheduled?))
      (when rate-limited?
        (.rateLimited builder true))
      (when rate-limited-min-interval
        (.rateLimited rate-limited-min-interval))
      (when retrying?
        (.retrying builder true))
      (when outage-tolerant?
        (.outageTolerant builder true))
      (when outage-tolerant-forever?
        (.outageTolerantForever builder))
      (when outage-tolerant-ttl
        (.outageTolerant outage-tolerant-ttl))
      (.build builder))))
