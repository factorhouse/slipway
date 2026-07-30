(ns slipway.security.openid.bearer-token
  (:import (org.eclipse.jetty.http HttpHeader HttpStatus)
           (org.eclipse.jetty.security AuthenticationState)
           (org.eclipse.jetty.security.authentication LoginAuthenticator LoginAuthenticator$UserAuthenticationSucceeded)
           (org.eclipse.jetty.server Request Response)
           (org.eclipse.jetty.util Callback)))

(def authentication-type "OPENID_BEARER_TOKEN")
(def prefix "bearer ")

(defn is-bearer-auth-type?
  [^String credentials]
  (and credentials (.regionMatches credentials true 0 prefix 0 7)))

(defn authenticator
  []
  (proxy [LoginAuthenticator] []

    ;; only really used when configuring jetty.ini or web.xml, not so important for embedded servers
    (^boolean getAuthenticationType []
      authentication-type)

    (^AuthenticationState validateRequest [^Request request ^Response response ^Callback cb]
      (or (let [^String credentials (-> (.getHeaders request) (.get HttpHeader/AUTHORIZATION))]
            (when (is-bearer-auth-type? credentials)
              (when-let [user-identity (proxy-super login nil (-> (.substring credentials 7) (.trim)) request response)]
                (LoginAuthenticator$UserAuthenticationSucceeded. authentication-type user-identity))))
          (do
            (Response/writeError request response cb HttpStatus/UNAUTHORIZED_401)
            AuthenticationState/SEND_FAILURE)))))