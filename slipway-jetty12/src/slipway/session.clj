(ns slipway.session
  (:require [clojure.tools.logging :as log])
  (:import (org.eclipse.jetty.http HttpCookie$SameSite)
           (org.eclipse.jetty.ee10.servlet SessionHandler)))

(defmulti tracking-mode identity)

(defn cookie-same-site
  [same-site]
  (case same-site
    :none HttpCookie$SameSite/NONE
    :lax HttpCookie$SameSite/LAX
    :strict HttpCookie$SameSite/STRICT))

(comment
  #:slipway.session{:secure-request-only?  "set the secure flag on session cookies"
                    :http-only?            "set the http-only flag on session cookies"
                    :same-site             "set session cookie same-site policy to :none, :lax, or :strict"
                    :max-inactive-interval "max session idle time (in s)"
                    :tracking-modes        "a set (colloection) of #{:cookie, :ssl, or :url}"
                    :cookie-name           "the name of the session cookie"
                    :session-id-manager    "the meta manager used for cross context session management"
                    :refresh-cookie-age    "max time before a session cookie is re-set (in s)"
                    :path-parameter-name   "name of path parameter used for URL session tracking"})

(defn handler ^SessionHandler
  [{::keys [secure-request-only? http-only? same-site max-inactive-interval tracking-modes cookie-name
            session-id-manager refresh-cookie-age path-parameter-name]
    ;; in-line with ring https://github.com/ring-clojure/ring-defaults/blob/master/src/ring/middleware/defaults.clj#L44
    :or    {secure-request-only?  true
            http-only?            true
            same-site             :strict
            max-inactive-interval -1
            tracking-modes        #{:cookie}
            cookie-name           "JSESSIONID"}}]
  (log/infof "max-inactive-interval %s" max-inactive-interval)
  (let [same-site       (cookie-same-site same-site)
        tracking-modes  (into #{} (map tracking-mode) tracking-modes)
        session-handler (doto (SessionHandler.)
                          (.setSecureRequestOnly secure-request-only?)
                          (.setHttpOnly http-only?)
                          (.setSameSite same-site)
                          (.setMaxInactiveInterval max-inactive-interval)
                          (.setSessionTrackingModes tracking-modes)
                          (.setSessionCookie cookie-name))]
    (some->> session-id-manager (.setSessionIdManager session-handler))
    (some->> refresh-cookie-age (.setRefreshCookieAge session-handler))
    (some->> path-parameter-name (.setSessionIdPathParameterName session-handler))
    session-handler))