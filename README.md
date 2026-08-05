# Slipway: a Clojure Companion to Jetty 12.1

[![Slipway Test](https://github.com/factorhouse/slipway/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/factorhouse/slipway/actions/workflows/ci.yml)
[![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/slipway-jetty12.svg)](https://clojars.org/io.factorhouse/slipway-jetty12)

----

## Slipway by [Factor House](https://factorhouse.io)

* [Introduction](#introduction)
    * [Archived Versions](#archived-versions)
* [Using Slipway](#using-slipway)
    * [JVM Version Support](#jvm-version-support)
* [Configuring Slipway](#configuring-slipway)
* [License](#license)

----

# Introduction

Slipway provides first-class support for embedded [Eclipse Jetty](https://www.eclipse.org/jetty/) 12.1 with WebSocket support in [Clojure](https://clojure.org/).

Jetty is the web server at the heart of our products at [Factor House](https://factorhouse.io/), and Slipway is the library that we use to build those products.

Slipway configuration models Jetty instead of exposing a simplified DSL. This approach allows leverage of all Jetty
capabilities while providing sensible defaults for basic behaviour. If in doubt, read
the [Jetty docs](https://jetty.org/docs/).

### Archived Versions

Previous support for Jetty 9, 10, and 11 is preserved in the [`archive/`](archive/) directory
but is no longer maintained. Slipway 2.x targets Jetty 12.1 exclusively.

## Using Slipway

Add `io.factorhouse/slipway-jetty12` to your project dependencies:

```clojure
;; deps.edn
{io.factorhouse/slipway-jetty12 {:mvn/version "2.0.7"}}

;; project.clj
[io.factorhouse/slipway-jetty12 "2.0.7"]
```

### JVM Version Support

Slipway (and Jetty 12.1) Requires Java 17+.

## Configuring Slipway

**Note**: This guide will be properly updated shortly, prior to the upcoming 2.1 release.

Slipway provides namespaced configuration that closely models the Jetty domain.

In this example we have:

* A web UI running on https/3443 with websockets enabled and OIDC authorization code auth at path `/`
* An API running on https/3443 with OIDC client credentials auth at path `/api`
* A OTEL endpoint on http/3000 with Basic/Hash-User auth at path `/otel`

See the integration tests For runnable, example servers.

```clojure
(slipway/start
 #::server{:connectors    [{::http/name "connector-3000"
                            ::http/port 3000}
                           {::https/name                 "connector-3443"
                            ::https/:port                3443
                            ::https/:keystore            "dev-resources/my-keystore.jks"
                            ::https/:keystore-type       "PKCS12"
                            ::https/:keystore-password   "password"
                            ::https/:truststore          "dev-resources/my-truststore.jks"
                            ::https/:truststore-password "password"
                            ::https/:truststore-type     "PKCS12"}]
           :handler       {::server/handler-type ::context/handler-collection
                           ::context/handlers    [{::context/virtual-hosts        ["@connector-3443"]
                                                   ::context/ring-handler         (handler/ui-handler)
                                                   ::websockets/enabled?          true
                                                   ::security/handler             :openid
                                                   ::openid/issuer                "http://localhost:8080/realms/master"
                                                   ::openid/client-id             "https://slipway.io/demo-web"
                                                   ::openid/client-secret         "81a0d6ea-1468-4b20-b115-fa68a8df9cf8"
                                                   ::openid.jwt/user-id-path      ["name"]
                                                   ::openid.jwt/user-roles-path   ["realm_access" "roles"]
                                                   ::openid/oidc-redirect-success "/oauth2/openid/callback"
                                                   ::openid/oidc-redirect-error   "/login-error"
                                                   ::openid/oidc-redirect-logout  "/logout-success"
                                                   ::hash/constraint-mappings     app/constraints}
                                                  {::context/path                         "/api"
                                                   ::context/virtual-hosts                ["@connector-3443"]
                                                   ::context/ring-handler                 (app/api-handler)
                                                   ::security/handler                     :openid
                                                   ::session/enabled?                     false
                                                   ::openid/authorization-flow            :client-credentials
                                                   ::openid.jwks/endpoint                 "http://localhost:8080/realms/master/protocol/openid-connect/certs"
                                                   ::openid.jwt.at.verification/exact-iss "http://localhost:8080/realms/master"
                                                   ::openid.jwt.at.verification/exact-aud "https://slipway.io/demo-api"
                                                   ::openid.jwt/user-id-path              ["preferred_username"]
                                                   ::openid.jwt/user-roles-path           ["realm_access" "roles"]
                                                   ::openid/constraint-mappings           app/constraints}
                                                  {::context/path             "/otel"
                                                   ::context/virtual-hosts    ["@connector-3000"]
                                                   ::context/ring-handler     (handler/otel-handler)
                                                   ::security/handler         :hash
                                                   ::hash/realm               "slipway"
                                                   ::hash/users               [["prometheus" "password" ["metrics"]]]
                                                   ::hash/authenticator       (BasicAuthenticator.)
                                                   ::hash/constraint-mappings app/constraints}]}
           :error-handler app/server-error-handler})
```

## License

Distributed under the Apache 2.0 License.

Copyright (c) [Factor House](https://factorhouse.io)
