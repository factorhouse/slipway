(ns slipway.session
  (:require [clojure.tools.logging :as log])
  (:import (org.eclipse.jetty.http HttpCookie$SameSite)
           (org.eclipse.jetty.session SessionHandler)))

(defn cookie-same-site
  [same-site]
  (case same-site
    :none HttpCookie$SameSite/NONE
    :lax HttpCookie$SameSite/LAX
    :strict HttpCookie$SameSite/STRICT
    HttpCookie$SameSite/STRICT))

(comment
  #:slipway.session{:secure-request-only?    "set the secure flag on session cookies"
                    :http-only?              "set the http-only flag on session cookies"
                    :same-site               "set session cookie same-site policy to :none, :lax, or :strict"
                    :max-inactive-interval-s "max session idle time (in s)"
                    :cookie-name             "the name of the session cookie"
                    :session-id-manager      "the meta manager used for cross context session management"
                    :refresh-cookie-age-s    "max time before a session cookie is re-set (in s)"
                    :using-cookies           "true if cookies are used to track sessions (default true)"
                    :using-uri-parameters    "true if uri parameters are used to track sessions (default false)"
                    :path-parameter-name     "name of path parameter used for URL session tracking"})

;; TODO: consider these cookie options, particularly PARTITIONED
;String COMMENT_ATTRIBUTE = "Comment";
;String DOMAIN_ATTRIBUTE = "Domain";
;String EXPIRES_ATTRIBUTE = "Expires";
;String HTTP_ONLY_ATTRIBUTE = "HttpOnly";
;String MAX_AGE_ATTRIBUTE = "Max-Age";
;String PATH_ATTRIBUTE = "Path";
;String SAME_SITE_ATTRIBUTE = "SameSite";
;String SECURE_ATTRIBUTE = "Secure";
;String PARTITIONED_ATTRIBUTE = "Partitioned";

(defn handler ^SessionHandler
  [{::keys [secure-request-only? http-only? same-site max-inactive-interval-s cookie-name session-id-manager
            refresh-cookie-age-s path-parameter-name using-cookies using-uri-parameters]
    ;; in-line with ring https://github.com/ring-clojure/ring-defaults/blob/master/src/ring/middleware/defaults.clj#L44
    :or    {secure-request-only?    true
            http-only?              true
            same-site               :strict
            max-inactive-interval-s -1
            cookie-name             "JSESSIONID"
            using-cookies           true
            using-uri-parameters    false}
    :as    opts}]
  (log/debugf "creating session handler with %s" opts)
  (let [same-site       (cookie-same-site same-site)
        session-handler (doto (SessionHandler.)
                          (.setUsingCookies using-cookies)
                          (.setUsingUriParameters using-uri-parameters)
                          (.setSecureRequestOnly secure-request-only?)
                          (.setHttpOnly http-only?)
                          (.setSameSite same-site)
                          (.setMaxInactiveInterval max-inactive-interval-s)
                          (.setSessionCookie cookie-name))]
    (some->> session-id-manager (.setSessionIdManager session-handler))
    (some->> refresh-cookie-age-s (.setRefreshCookieAge session-handler))
    (some->> path-parameter-name (.setSessionIdPathParameterName session-handler))
    session-handler))