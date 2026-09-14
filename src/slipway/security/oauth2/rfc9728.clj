(ns slipway.security.oauth2.rfc9728
  (:require [slipway.request :as request]))

;;https://www.rfc-editor.org/info/rfc9728/#RFC8414

(def metadata-path
  "/.well-known/oauth-protected-resource")

(defn metadata-url
  [resource-url]
  (str resource-url metadata-path))

(defn metadata-url-header-value
  [resource-url]
  (format "Bearer resource_metadata=\"%s\"" (metadata-url resource-url)))

(defn response-401
  [resource-url]
  {:status  401
   :headers {"WWW-Authenticate" (metadata-url-header-value resource-url)}})

(defn resource-url
  [request-map]
  (-> (request/uri-context request-map)
      (:uri-builder)
      (.toString)))

(comment
  #:slipway.security.oauth2.rfc9728{:resource                                   "the protected resource's resource identifier"
                                    :authorization-servers                      "array containing a list of OAuth authorization server issuer identifiers"
                                    :jwks-uri                                   "URL of the protected resource's JSON Web Key (JWK) Set"
                                    :scopes-supported                           "array containing a list of scope values used in authorization requests"
                                    :bearer-methods-supported                   "array containing a list of the supported methods of sending an OAuth 2.0 bearer token to the protected resource"
                                    :resource-signing-alg-values-supported      "array containing a list of the JWS [JWS] signing algorithms (alg values) [JWA] supported by the protected resource for signing resource responses"
                                    :resource-name                              "name of the protected resource intended for display to the end user"
                                    :resource-documentation                     "URL of a page containing human-readable information that developers might want or need to know when using the protected resource"
                                    :resource-policy-uri                        "URL of a page containing human-readable information about the protected resource's requirements on how the client can use the data provided by the protected resource"
                                    :resource-tos-uri                           "URL of a page containing human-readable information about the protected resource's terms of service"
                                    :tls-client-certificate-bound-access-tokens "boolean value indicating protected resource support for mutual-TLS client certificate-bound access tokens"
                                    :authorization-details-types-supported      "array containing a list of the authorization details type values supported by the resource server when the authorization_details request parameter [RFC9396] is used"
                                    :dpop-signing-alg-values-supported          "array containing a list of the JWS alg values (from the \"JSON Web Signature and Encryption Algorithms\" registry [IANA.JOSE]) supported by the resource server for validating Demonstrating Proof of Possession (DPoP) proof JWTs [RFC9449]"
                                    :dpop-bound-access-tokens-required          "boolean value specifying whether the protected resource always requires the use of DPoP-bound access tokens [RFC9449]"})

(defn metadata
  [{::keys [resource authorization-servers jwks-uri scopes-supported bearer-methods-supported resource-documentation
            resource-signing-alg-values-supported resource-name resource-policy-uri resource-tos-uri
            tls-client-certificate-bound-access-tokens authorization-details-types-supported
            dpop-signing-alg-values-supported dpop-bound-access-tokens-required]}]
  (cond-> {}
    resource (assoc "resource" resource)
    authorization-servers (assoc "authorization_servers" authorization-servers)
    jwks-uri (assoc "jwks_uri" jwks-uri)
    scopes-supported (assoc "scopes_supported" scopes-supported)
    bearer-methods-supported (assoc "bearer_methods_supported" bearer-methods-supported)
    resource-signing-alg-values-supported (assoc "resource_signing_alg_values_supported" resource-signing-alg-values-supported)
    resource-name (assoc "resource_name" resource-name)
    resource-documentation (assoc "resource_documentation" resource-documentation)
    resource-policy-uri (assoc "resource_policy_uri" resource-policy-uri)
    resource-tos-uri (assoc "resource_tos_uri" resource-tos-uri)
    tls-client-certificate-bound-access-tokens (assoc "tls_client_certificate_bound_access_tokens" tls-client-certificate-bound-access-tokens)
    authorization-details-types-supported (assoc "authorization_details_types_supported" authorization-details-types-supported)
    dpop-signing-alg-values-supported (assoc "dpop_signing_alg_values_supported" dpop-signing-alg-values-supported)
    dpop-bound-access-tokens-required (assoc "dpop_bound_access_tokens_required" dpop-bound-access-tokens-required)))