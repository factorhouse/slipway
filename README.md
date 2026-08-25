# Slipway: a Clojure Companion to Jetty 12.1

[![Slipway Test](https://github.com/factorhouse/slipway/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/factorhouse/slipway/actions/workflows/ci.yml)
[![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/slipway-jetty12.svg)](https://clojars.org/io.factorhouse/slipway-jetty12)

----

## Slipway by [Factor House](https://factorhouse.io)

* [Introduction](#introduction)
    * [Archived Versions](#archived-versions)
* [Using Slipway](#using-slipway)
    * [JVM Version Support](#jvm-version-support)
* [Configuring and Starting Slipway](#configuring-and-starting-slipway)
* [License](#license)

----

# Introduction

Slipway provides first-class support for embedded [Eclipse Jetty](https://www.eclipse.org/jetty/) 12.1 in [Clojure](https://clojure.org/).

The goal of this project is to provide access in Clojure to the full set of capabilities implemented by Jetty.

In addition to those capabilities, this library extends Jetty's feature-set where required to meet the needs
of the [Factor House](https://factorhouse.io/) team in their product delivery work.

Slipway configuration models Jetty instead of exposing a simplified DSL or a new web-server abstraction. This allows
leverage of Jetty capabilities while providing sensible defaults in a data-oriented way. 

In a very simple sense, Slipway is currently:

1. Eclipse Jetty
2. Websockets (combinging Jetty with [Sente](https://github.com/taoensso/sente))
3. Extended OIDC support including Authorization Code Flow, Client Credentials Flow, and Refresh Token redemption

Predominantly Slipway is Jetty, if in doubt read the [Jetty docs](https://jetty.org/docs/).

### JVM Version Support

Slipway (and Jetty 12.1) Requires Java 17+.

### Archived Versions

Implementations supporting Jetty 9, 10, and 11 are preserved in the [`archive/`](archive/) directory, but no longer maintained.

## Using Slipway

Add `io.factorhouse/slipway-jetty12` to your project dependencies:

```clojure
;; deps.edn
{io.factorhouse/slipway-jetty12 {:mvn/version "2.0.7"}}

;; project.clj
[io.factorhouse/slipway-jetty12 "2.0.7"]
```

## Configuring and Starting Slipway

**Note**: This guide will be properly updated shortly, prior to the upcoming 2.1 release.

Slipway provides namespaced configuration that closely models the Jetty domain.

In this example we have:

* A web UI running on https/3443 with websockets enabled and OIDC authorization code auth at path `/`
* An API running on https/3443 with OIDC client credentials auth at path `/api`
* An OTEL endpoint on http/3000 with Basic/Hash-User auth at path `/otel`

See the integration tests for runnable, example servers.

See [slipway.clj](src/slipway.clj) for all configuration options.

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
                                                   ::security/handler             :oidc
                                                   ::oidc/issuer                "http://localhost:8080/realms/master"
                                                   ::oidc/client-id             "https://slipway.io/demo-web"
                                                   ::oidc/client-secret         "81a0d6ea-1468-4b20-b115-fa68a8df9cf8"
                                                   ::oidc.jwt/user-id-path      ["name"]
                                                   ::oidc.jwt/user-roles-path   ["realm_access" "roles"]
                                                   ::oidc/oidc-redirect-success "/oauth2/openid/callback"
                                                   ::oidc/oidc-redirect-error   "/login-error"
                                                   ::oidc/oidc-redirect-logout  "/logout-success"
                                                   ::hash/constraint-mappings     app/constraints}
                                                  {::context/path                         "/api"
                                                   ::context/virtual-hosts                ["@connector-3443"]
                                                   ::context/ring-handler                 (app/api-handler)
                                                   ::security/handler                     :oidc
                                                   ::session/enabled?                     false
                                                   ::oidc/authorization-flow            :client-credentials
                                                   ::oidc.jwks/endpoint                 "http://localhost:8080/realms/master/protocol/openid-connect/certs"
                                                   ::oidc.jwt.at.verification/exact-iss "http://localhost:8080/realms/master"
                                                   ::oidc.jwt.at.verification/exact-aud "https://slipway.io/demo-api"
                                                   ::oidc.jwt/user-id-path              ["preferred_username"]
                                                   ::oidc.jwt/user-roles-path           ["realm_access" "roles"]
                                                   ::oidc/constraint-mappings           app/constraints}
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
