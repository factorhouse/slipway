(ns slipway.common.auth
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log])
  (:import (java.util List)
           (org.eclipse.jetty.server Authentication$User)
           (javax.security.auth.login Configuration)        ;; Jetty9/10/11 all use javax in this case.
           (org.eclipse.jetty.http HttpCookie$SameSite)
           (org.eclipse.jetty.jaas JAASLoginService)
           (org.eclipse.jetty.security ConstraintSecurityHandler HashLoginService)
           (org.eclipse.jetty.security.authentication BasicAuthenticator FormAuthenticator)
           (org.eclipse.jetty.server Authentication$User Handler Request Server)
           (org.eclipse.jetty.server.handler HandlerCollection)
           (org.eclipse.jetty.server.session SessionHandler)))

(defmulti login-service :auth-type)

(defmulti session-tracking-mode identity)

(defn credentials
  "Derive user credentials (name + roles) from a base jetty request"
  [^Request req]
  (when-let [authentication (.getAuthentication req)]
    (when (instance? Authentication$User authentication)
      (p/datafy authentication))))

(defn logout
  "Logout user and invalidate the session"
  [{:keys [slipway.user/user ^Request slipway.server/request]}]
  (try
    (log/debug "logout" user)
    (.logout request)
    (.invalidate (.getSession request))
    (catch Exception ex
      (log/error ex "logout error"))))

(defmethod login-service "jaas"
  [{:keys [realm]}]
  (let [config (System/getProperty "java.security.auth.login.config")]
    (log/infof "initializing JAASLoginService -> realm: %s, java.security.auth.login.config: %s " realm config)
    (if config
      (when (slurp config)                                  ;; biffs an exception if not found
        (doto (JAASLoginService. realm) (.setConfiguration (Configuration/getConfiguration))))
      (throw (ex-info (str "start with -Djava.security.auth.login.config=/some/path/to/jaas.config to use Jetty/JAAS auth provider") {})))))

(defmethod login-service "hash"
  [{:keys [hash-user-file realm]}]
  (log/infof "initializing HashLoginService -> realm: %s, realm file: %s" realm hash-user-file)
  (if hash-user-file
    (when (slurp hash-user-file)
      (HashLoginService. realm hash-user-file))
    (throw (ex-info (str "set the path to your hash user realm properties file") {}))))

(defn cookie-same-site
  [same-site]
  (case same-site
    :none HttpCookie$SameSite/NONE
    :lax HttpCookie$SameSite/LAX
    :strict HttpCookie$SameSite/STRICT))

(defn session-handler ^SessionHandler
  ;; Apply sensible defaults in-line with ring-defaults:
  ;; https://github.com/ring-clojure/ring-defaults/blob/master/src/ring/middleware/defaults.clj#L44
  [{:keys [secure-request-only? http-only? same-site max-inactive-interval tracking-modes cookie-name]
    :or   {max-inactive-interval -1
           secure-request-only?  true
           http-only?            true
           same-site             :strict
           cookie-name           "JSESSIONID"
           tracking-modes        #{:cookie}}}]
  (let [same-site      (cookie-same-site same-site)
        tracking-modes (into #{} (map session-tracking-mode) tracking-modes)]
    (doto (SessionHandler.)
      (.setSessionTrackingModes tracking-modes)
      (.setMaxInactiveInterval max-inactive-interval)
      (.setSecureRequestOnly secure-request-only?)
      (.setHttpOnly http-only?)
      (.setSameSite same-site)
      (.setSessionCookie cookie-name))))

(defn configure
  [^Server server
   {:keys [auth-method login-uri login-retry-uri constraint-mappings session cookie]
    :as   opts}]
  (let [login            (login-service opts)
        security-handler (doto (ConstraintSecurityHandler.)
                           (.setConstraintMappings ^List constraint-mappings)
                           (.setAuthenticator (if (= "basic" auth-method)
                                                (BasicAuthenticator.)
                                                (FormAuthenticator. login-uri login-retry-uri false)))
                           (.setLoginService login)
                           (.setHandler (.getHandler server)))]
    (.addBean server login)
    (if (= "basic" auth-method)
      (.setHandler server security-handler)
      (.setHandler server (HandlerCollection.
                           (into-array Handler [(session-handler (or session cookie))
                                                security-handler]))))
    server))