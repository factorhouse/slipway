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

(defn resource-url
  [request-map]
  (-> (request/uri-context request-map)
      (:uri-builder)
      (.toString)))

(defn metadata
  [{::keys [resource authorization-servers jwks-uri scopes bearer-methods resource-documentation
            resource-signing-alg-values resource-name resource-policy-uri resource-tos-uri
            tls-client-certificate-bound-access-tokens authorization-details-types-supported
            dpop-signing-alg-values-supported dpop-bound-access-tokens-required]}]
  (cond-> {}
    resource (assoc "resource" resource)
    authorization-servers (assoc "authorization_servers" authorization-servers)
    jwks-uri (assoc "jwks_uri" jwks-uri)
    scopes (assoc "scopes_supported" scopes)
    bearer-methods (assoc "bearer_methods_supported" bearer-methods)
    resource-signing-alg-values (assoc "resource_signing_alg_values_supported" resource-signing-alg-values)
    resource-name (assoc "resource_name" resource-name)
    resource-documentation (assoc "resource_documentation" resource-documentation)
    resource-policy-uri (assoc "resource_policy_uri" resource-policy-uri)
    resource-tos-uri (assoc "resource_tos_uri" resource-tos-uri)
    tls-client-certificate-bound-access-tokens (assoc "tls_client_certificate_bound_access_tokens" tls-client-certificate-bound-access-tokens)
    authorization-details-types-supported (assoc "authorization_details_types_supported" authorization-details-types-supported)
    dpop-signing-alg-values-supported (assoc "dpop_signing_alg_values_supported" dpop-signing-alg-values-supported)
    dpop-bound-access-tokens-required (assoc "dpop_bound_access_tokens_required" dpop-bound-access-tokens-required)))