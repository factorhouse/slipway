(ns slipway.session
  (:import (org.eclipse.jetty.http HttpCookie$SameSite)
           (org.eclipse.jetty.server.session SessionHandler)))

(defmulti tracking-mode identity)

(defn cookie-same-site
  [same-site]
  (case same-site
    :none HttpCookie$SameSite/NONE
    :lax HttpCookie$SameSite/LAX
    :strict HttpCookie$SameSite/STRICT))

;; Apply sensible defaults in-line with ring-defaults:
;; https://github.com/ring-clojure/ring-defaults/blob/master/src/ring/middleware/defaults.clj#L44
(defn handler ^SessionHandler
  [{:keys [secure-request-only? http-only? same-site max-inactive-interval tracking-modes cookie-name]
    :or   {max-inactive-interval -1
           secure-request-only?  true
           http-only?            true
           same-site             :strict
           cookie-name           "JSESSIONID"
           tracking-modes        #{:cookie}}}]
  (let [same-site      (cookie-same-site same-site)
        tracking-modes (into #{} (map tracking-mode) tracking-modes)]
    (doto (SessionHandler.)
      (.setSessionTrackingModes tracking-modes)
      (.setMaxInactiveInterval max-inactive-interval)
      (.setSecureRequestOnly secure-request-only?)
      (.setHttpOnly http-only?)
      (.setSameSite same-site)
      (.setSessionCookie cookie-name))))