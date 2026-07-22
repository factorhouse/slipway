(ns slipway.security.openid
  (:require [clojure.core.protocols :as p]
            [clojure.tools.logging :as log]
            [slipway.security :as security])
  (:import (java.security Principal)
           (java.util.function Function)
           (javax.security.auth Subject)
           (org.eclipse.jetty.security Constraint IdentityService LoginService SecurityHandler$PathMapped UserIdentity)
           (org.eclipse.jetty.security.openid JwtDecoder OpenIdAuthenticator OpenIdConfiguration OpenIdConfiguration$Builder OpenIdCredentials OpenIdLoginService OpenIdUserPrincipal)
           (org.eclipse.jetty.server Request)
           (slipway.security.openid.user.principal OpenIdUserPrincipalWithState)))

(extend-protocol p/Datafiable

  OpenIdCredentials
  (datafy [credentials]
    {:id-token (.getClaims credentials)                     ;; decoded version of ["response" "id_token"]
     :response (.getResponse credentials)})

  OpenIdUserPrincipalWithState
  (datafy [principal]
    (merge {:type ::principal}
           (.getState principal))))

(defn access-token
  [^OpenIdCredentials creds]
  (try
    ;; TODO: further claims validation on the access token, similar to the ID token validation (see OpenIdCredentials).
    (JwtDecoder/decode (get (.getResponse creds) "access_token"))
    (catch Exception ex
      (log/debug ex "error decoding access_token"))))

(defn state
  [creds access-token {::keys [user-roles-token user-roles-path user-id-path]
                       :or    {user-roles-token :access-token
                               user-roles-path  ["roles"]
                               user-id-path     ["sub"]}}]
  (let [{:keys [id-token response]} (p/datafy creds)
        roles-value (get-in (if (= :id-token user-roles-token)
                              id-token
                              access-token)
                            user-roles-path)]
    {:name           (get-in id-token user-id-path)
     :roles          (if (string? roles-value) #{roles-value} (set roles-value))
     ::response      response
     ::id-token      id-token
     ::access-token  access-token
     ::refresh-token (get response "refresh_token")}))

(defn direct-authorization-code-flow-roles-service
  "This roles-service is applicable only to OpenID Connect (OIDC) direct Authorization Code flow via the Token endpoint
   (that is how Jetty navigates OpenID authentication).
   Under that flow you can rely on TLS (HTTPS) to authenticate the issuer instead of verifying the JWT signature.
   While that normally only applies to the ID token, in our case the Access token is intended for local use inside this
   client service JVM, and so the same logic applies."
  ^LoginService [realm opts]
  (let [id-service-state (atom nil)]
    (reify LoginService
      (^String getName [_]
        (str realm "-roles"))
      (^UserIdentity login [_ ^String _username ^Object _credentials ^Request _request ^Function _get-or-create])
      (^UserIdentity getUserIdentity [_ ^Subject _subject ^Principal user-principal ^boolean _create?]
        (let [user-creds        (.getCredentials ^OpenIdUserPrincipal user-principal) ;; roles-service is only used with OpenID
              user-access-token (access-token user-creds)
              user-state        (state user-creds user-access-token opts)
              new-principal     (OpenIdUserPrincipalWithState. user-creds user-state)
              new-subject       (Subject.)]
          (-> (.getPrincipals new-subject) (.add new-principal))
          (-> (.getPrivateCredentials new-subject) (.add user-creds))
          (.setReadOnly new-subject)
          (.newUserIdentity ^IdentityService @id-service-state new-subject new-principal (into-array String (:roles user-state)))))
      (^boolean validate [_ ^UserIdentity _user]
        true)
      (^IdentityService getIdentityService [_]
        @id-service-state)
      (^void setIdentityService [_ ^IdentityService identity-service]
        (reset! id-service-state identity-service))
      (^void logout [_ ^UserIdentity _user]))))

(defn configuration ^OpenIdConfiguration
  [{::keys [issuer client-id client-secret authorization-endpoint token-endpoint end-session-endpoint
            authentication-method http-client scopes logout-when-id-token-is-expired?]}]
  (let [config-builder (cond-> (OpenIdConfiguration$Builder. issuer client-id client-secret)
                         authorization-endpoint (.authorizationEndpoint authorization-endpoint)
                         token-endpoint (.tokenEndpoint token-endpoint)
                         end-session-endpoint (.endSessionEndpoint end-session-endpoint)
                         authentication-method (.authenticationMethod authentication-method)
                         http-client (.httpClient http-client)
                         (some? scopes) (.scopes (some->> scopes (into-array String)))
                         (some? logout-when-id-token-is-expired?) (.logoutWhenIdTokenIsExpired logout-when-id-token-is-expired?))]
    (.build config-builder)))

(defn authenticator ^OpenIdAuthenticator
  [config {::keys [oidc-redirect-success oidc-redirect-error oidc-redirect-logout]
           :or    {oidc-redirect-success OpenIdAuthenticator/J_SECURITY_CHECK}}]
  (OpenIdAuthenticator. config oidc-redirect-success oidc-redirect-error oidc-redirect-logout))

(comment
  #:slipway.security.openid{:issuer                           "the URL of the OpenID provider"
                            :client-id                        "OAuth 2.0 Client Identifier valid at the OpenID provider"
                            :client-secret                    "the client secret known only by the Client and the OpenID"
                            :authorization-endpoint           "the URL of the OpenID provider's authorization endpoint if configured"
                            :token-endpoint                   "the URL of the OpenID provider's token endpoint if configured"
                            :end-session-endpoint             "the URL of the OpenID provider's end session endpoint if configured"
                            :authentication-method            "authentication method to use with the Token Endpoint"
                            :http-client                      "the (optional) HttpClient instance to use"
                            :scopes                           "a sequence of ^String scopes to request, Jetty default is [\"openid\"]"
                            :user-roles-token                 "the token containing user roles, either :access-token or :id-token (default is :access-token)"
                            :user-roles-path                  "the path within the :roles token to find user roles, default is [\"roles\"]"
                            :user-id-path                     "the path within the :id-token token to find user name, default is [\"sub\"]"
                            :logout-when-id-token-is-expired? "whether to logout when the ID token is expired"
                            :oidc-redirect-success            "the path where the OIDC provider redirects back to Jetty"
                            :oidc-redirect-error              "optional page where authentication errors are redirected"
                            :oidc-redirect-logout             "optional page where the user is redirected to this page after logout"
                            :identity-service                 "a concrete Jetty IdentityService"
                            :constraint-mappings              "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"})

(defmethod security/handler "openid"
  [{::keys [issuer constraint-mappings identity-service] :as opts}]
  (log/debugf "creating openid security handler with %s constraints" (count constraint-mappings))
  (let [openid-config    (configuration opts)
        login-service    (OpenIdLoginService. openid-config (direct-authorization-code-flow-roles-service issuer opts))
        security-handler (doto (SecurityHandler$PathMapped.)
                           (.setAuthenticator (authenticator openid-config opts))
                           (.setLoginService login-service)
                           (.setRealmName issuer))]
    (doseq [[^String path-spec ^Constraint constraint] constraint-mappings]
      (.put security-handler path-spec constraint))
    (when identity-service
      (log/debugf "identity service %s" (type identity-service))
      (.setIdentityService security-handler identity-service))
    security-handler))