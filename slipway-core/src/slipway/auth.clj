(ns slipway.auth
  (:require [clojure.tools.logging :as log]
            [clojure.core.protocols :as p])
  (:import (org.eclipse.jetty.server Authentication$User)
           (org.eclipse.jetty.server Authentication$User Request Handler Server)
           (javax.security.auth.login Configuration)
           (org.eclipse.jetty.jaas JAASLoginService)
           (org.eclipse.jetty.security HashLoginService ConstraintSecurityHandler)
           (java.util List)
           (org.eclipse.jetty.security.authentication BasicAuthenticator FormAuthenticator)
           (javax.servlet SessionTrackingMode)
           (org.eclipse.jetty.server.session SessionHandler)
           (org.eclipse.jetty.server.handler HandlerCollection)
           (org.eclipse.jetty.http HttpCookie$SameSite)))

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
                                         set)]
      (log/debugf "user: %s, roles: %s" name roles)
      {:provider :jetty
       :name     name
       :roles    roles})))

(defn maybe-logout
  "Invalidate the session IFF the URI is /logout"
  [^Request base-request opts]
  (when (= (:logout-uri opts) (.getRequestURI base-request))
    (log/infof "logout")
    (try
      (.invalidate (.getSession base-request))
      (catch Exception ex
        (log/error ex "logout error")))))

(defn default-login-redirect
  "When logging in we have some special cases to consider with the post-login uri"
  [target request {:keys [login-uri login-retry-uri post-login-uri-attr]}]
  (when (#{login-uri login-retry-uri} target)
    (let [post-login-uri (.getAttribute (.getSession request) post-login-uri-attr)]
      ;; TODO: substituting "/" is error prone when running path-proxied kpow but is least-wrong for these edge-cases
      ;; TODO: if anyone complains, take a look at emulating the ForwardedRequestCustomizer login in Jetty
      ;; TODO: I guess it's possible FRC catches this case and updates response on write, but I doubt it. We can test.
      (cond
        (nil? post-login-uri)
        (do (log/info "defaulting post-login uri to '/'")
            (.setAttribute (.getSession request) post-login-uri-attr "/"))
        (.contains post-login-uri "/chsk")
        (do (log/info "avoiding /chsk post-login, setting post-login uri to '/'")
            (.setAttribute (.getSession request) post-login-uri-attr "/"))))))

(defmulti login-service :auth-type)

(defmethod login-service "jaas"
  [{:keys [realm]}]
  (let [config (System/getProperty "java.security.auth.login.config")]
    (log/infof "Initializing JAASLoginService -> realm: %s, java.security.auth.login.config: %s " realm config)
    (if config
      (when (slurp config)                                  ;; biffs an exception if not found
        (doto (JAASLoginService. realm) (.setConfiguration (Configuration/getConfiguration))))
      (throw (ex-info (str "Start with -Djava.security.auth.login.config=/some/path/to/jaas.config to use Jetty/JAAS auth provider") {})))))

(defmethod login-service "hash"
  [{:keys [hash-user-file realm]}]
  (log/infof "initializing HashLoginService -> realm: %s, realm file: %s" realm hash-user-file)
  (if hash-user-file
    (when (slurp hash-user-file)
      (HashLoginService. realm hash-user-file))
    (throw (ex-info (str "Set the path to your hash user realm properties file") {}))))

(defn ->tracking-mode
  [mode]
  (case mode
    :cookie SessionTrackingMode/COOKIE
    :url SessionTrackingMode/URL
    :ssl SessionTrackingMode/SSL))

(defn ->same-site
  [same-site]
  (case same-site
    :none HttpCookie$SameSite/NONE
    :lax HttpCookie$SameSite/LAX
    :strict HttpCookie$SameSite/STRICT))

(defn ^SessionHandler session-handler
  ;; Apply sensible defaults in-line with ring-defaults:
  ;; https://github.com/ring-clojure/ring-defaults/blob/master/src/ring/middleware/defaults.clj#L44
  [{:keys [secure-request-only? http-only? same-site max-inactive-interval tracking-modes cookie-name]
    :or   {max-inactive-interval -1
           secure-request-only?  true
           http-only?            true
           same-site             :strict
           cookie-name           "JSESSIONID"
           tracking-modes        #{:cookie}}}]
  (let [same-site      (->same-site same-site)
        tracking-modes (into #{} (map ->tracking-mode) tracking-modes)]
    (doto (SessionHandler.)
      (.setSessionTrackingModes tracking-modes)
      (.setMaxInactiveInterval max-inactive-interval)
      (.setSecureRequestOnly secure-request-only?)
      (.setHttpOnly http-only?)
      (.setSameSite same-site)
      (.setSessionCookie cookie-name))))

(defn configurator
  [^Server server
   {:keys [auth-method login-uri login-retry-uri constraint-mappings cookie]
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
                           (into-array Handler [(session-handler cookie)
                                                security-handler]))))
    server))