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
           (org.eclipse.jetty.server.handler ContextHandler HandlerCollection)
           (org.eclipse.jetty.server.session SessionHandler)))

(defmulti session-tracking-mode identity)

(defn user
  "Derive user credentials (name + roles) from a base jetty request"
  [^Request req]
  (when (instance? Authentication$User (.getAuthentication req))
    (let [^Authentication$User auth (.getAuthentication req)
          user-id                   (.getUserIdentity auth)
          name                      (some-> user-id (.getUserPrincipal) (.getName))
          roles                     (->> (some-> user-id (.getSubject) (.getPrincipals))
                                         (map p/datafy)
                                         (filter #(= :role (:type %)))
                                         (map :name)
                                         set)
          user                      {:provider :jetty
                                     :name     name
                                     :roles    roles}]
      (log/debug "user" user)
      user)))

(defn maybe-logout
  "Invalidate the session IFF the URI is /logout"
  [{:keys [logout-uri] :as auth} ^Request base-request request-map]
  (when auth
    (try
      (when (= logout-uri (.getRequestURI base-request))
        (log/debug "logout" (::user request-map))
        (.invalidate (.getSession base-request)))
      (catch Exception ex
        (log/error ex "logout error")))))

;(defn default-login-redirect
;  "When logging in we have some special cases to consider with the post-login uri"
;  [{:keys [login-uri login-retry-uri] :as auth} target ^HttpServletRequest request]
;  (when (and auth (#{login-uri login-retry-uri} target))
;    (let [^String post-login-uri (.getAttribute (.getSession request) FormAuthenticator/__J_URI)]
;      ;; Note: post-login-uri can be:
;      ;;  - nil if a user session starts on the login page (no concept of where to go after) - causes a NullPointerException post login
;      ;;    - d-t-w 04.08.22 - I think this is only possible with Jetty9 now (jetty10 appears to return "" from the request.getContextPath(). Leaving in to be defensive as Jetty returns "/" as default regardless.
;      ;;  - /chsk if a websocket request triggers the auth-flow (possible with expired session) - causes a redirect to /chsk + 404 post login
;      ;;
;      ;;  substituting "/" is in the nil case is error prone when running path-proxied apps but is least-wrong for these edge-cases
;      ;;  if anyone complains, take a look at emulating the ForwardedRequestCustomizer login in Jetty or take a configurable default post-login full url for this specific nil case.
;      ;;  I guess it's possible FRC catches this case and updates response on write, but I doubt it. We can test.
;      (cond
;        (nil? post-login-uri)
;        (do (log/info "defaulting post-login uri to '/'")
;            (.setAttribute (.getSession request) FormAuthenticator/__J_URI "/"))
;        (.contains post-login-uri "/chsk")
;        (do (log/info "avoiding /chsk post-login, setting post-login uri to '/'")
;            (.setAttribute (.getSession request) FormAuthenticator/__J_URI "/"))))))

(defmulti login-service :auth-type)

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
                           (.setHandler (.getHandler server)))
        security-context (doto (ContextHandler.)
                           (.setContextPath "/")
                           (.setAllowNullPathInfo true)
                           (.setHandler security-handler))]
    (.addBean server login)
    (if (= "basic" auth-method)
      (.setHandler server security-handler)
      (.setHandler server (HandlerCollection.
                           (into-array Handler [(session-handler (or session cookie))
                                                security-context]))))
    server))