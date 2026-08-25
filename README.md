# Slipway: a Clojure Companion to Jetty 12.1

[![Slipway Test](https://github.com/factorhouse/slipway/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/factorhouse/slipway/actions/workflows/ci.yml)
[![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/slipway-jetty12.svg)](https://clojars.org/io.factorhouse/slipway-jetty12)

----

## Slipway by [Factor House](https://factorhouse.io)

* [Introduction](#introduction)
* [Using Slipway](#using-slipway)
  * [Installation](#installation)
  * [JVM Support](#jvm-support)
  * [Archived Versions](#archived-versions)
* [Eclipse Jetty](#eclipse-jetty)
  * [Slipway Requests](#slipway-requests)
* [Future Goals](#future-goals)
* [Configuration](#configuration)
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

* Embedded Jetty 12.1 with native handlers (no Servlet/EE dependencies).
* Websockets (combining Jetty with [Sente](https://github.com/taoensso/sente)).
* Extended OIDC support including:
  * Authorization Code Flow with Refresh Token redemption
  * Client Credentials Flow

Predominantly Slipway is Jetty, when in doubt read the [Jetty docs](https://jetty.org/docs/).

## Using Slipway

### Installation

Add `io.factorhouse/slipway-jetty12` to your project dependencies:

```clojure
;; deps.edn
{io.factorhouse/slipway-jetty12 {:mvn/version "2.1.0"}}

;; project.clj
[io.factorhouse/slipway-jetty12 "2.1.0"]
```

### JVM Support

Slipway (and Jetty 12.1) Requires Java 17+. Archived version support Java 8 and Java 11.

### Archived Versions

Implementations supporting Jetty 9, 10, and 11 are no longer maintained and preserved in the [`archive/`](archive/) directory.

## Eclipse Jetty

Eclipse Jetty is a widely deployed, open-source Java web server that is considered ubiquitous across enterprise Java 
applications and cloud-native development.

Key Jetty concepts are the [Server](https://jetty.org/docs/jetty/12.1/programming-guide/server/http.html) that manages
network connections, [Connectors](https://jetty.org/docs/jetty/12.1/programming-guide/server/http.html#connector) that 
listen on network ports and decode incoming messages using protocols like HTTP 1.1, and 
[Handlers](https://jetty.org/docs/jetty/12.1/programming-guide/server/http.html#handler) that process a request and 
response.

A Jetty Server is composed of Connectors and Handlers. Virtual Hosts can pin a Handler to a specific Connector.

Slipway supports complex Jetty deployments where one Server can expose many Connectors and Handlers.

Slipway provides a base ContextHandler that processes a Request in the following order:

```
-> Request
-> ContextHandler
-> CompressionHandler (optional)
-> SessionHandler (optional)
-> SecurityHandler (optional)
-> WebsocketHandler (optional)
-> SyncHandler
-> ring-handler
```

### Slipway Requests

Slipway decodes incoming Jetty Request objects into a Clojure map that resembles a [Ring](https://github.com/ring-clojure/ring)
map with additional request, response, and authentication information, see: [src/slipway/request.clj](src/slipway/request.clj):

```Clojure
(defn request-map
  [^Request request ^Response response]
  (merge (ring-like-map request)
         {::request       request
          ::response      response
          ::user/identity (authenticated-user request)}))
```

Maintaining compatibility with Ring is not a goal of this project and future request-maps may not be 'Ring-like'.

## Future Goals

* Remove `org.ring-clojure/ring-core-protocols` dependency.
* Replace Sente with a Slipway-like [Socket.IO](https://socket.io/) implementation.
* Advance Slipway OIDC capabilities to support OAuth 2.1 by either:
  * Contributing to Jetty (see: https://github.com/jetty/jetty.project/discussions/15611), or;
  * Adoption and integration of [pac4j](https://www.pac4j.org/), augmenting existing Jetty-provided security model.

## Full-Stack Development

Slipway is constrained to the stated goals of this project, and those goals exclude any interest beyond Jetty.

[Factor House](https://factorhouse.io) has a commmon product architecture which is a conservative, security-conscious
web-server deployment with websocket support and a rich, data-oriented UI.

To meet the needs of that architecture we leverage [HSX](https://github.com/factorhouse/hsx) and
[RFX](https://github.com/factorhouse/rfx) among other libraries.

This project's [integration tests](test/integration/slipway) contain many example server deployments, but those examples
have a very simple non-websocket architecture due to the constraints of this library.

It is our desire to provide a full-stack reference architecture in [Shortcut](https://github.com/factorhouse/shortcut), 
however that has been a desire for quite some time and we have lots of other priorites.

## Configuration

In this example we have:

* A web UI running on https/3443 with websockets enabled and OIDC authorization code auth at path `/`
* An API running on https/3443 with OIDC client credentials auth at path `/api`
* An OTEL endpoint on http/3000 with Basic/Hash-User auth at path `/otel`

See the [integration tests](test/integration/slipway) for runnable, example servers.

See [slipway.clj](src/slipway.clj) for all configuration options.

```clojure
(let [connector-http  {::http/name "connector-3000"
                       ::http/port 3000}
      connector-https {::https/name                 "connector-3443"
                       ::https/:port                3443
                       ::https/:keystore            "dev-resources/my-keystore.jks"
                       ::https/:keystore-type       "PKCS12"
                       ::https/:keystore-password   "password"
                       ::https/:truststore          "dev-resources/my-truststore.jks"
                       ::https/:truststore-password "password"
                       ::https/:truststore-type     "PKCS12"}
      handler-ui      {::context/virtual-hosts      ["@connector-3443"]
                       ::context/ring-handler       (handler/ui-handler)
                       ::websockets/enabled?        true
                       ::security/handler           :oidc
                       ::oidc/issuer                "http://localhost:8080/realms/master"
                       ::oidc/client-id             "https://slipway.io/demo-web"
                       ::oidc/client-secret         "81a0d6ea-1468-4b20-b115-fa68a8df9cf8"
                       ::oidc.jwt/user-id-path      ["name"]
                       ::oidc.jwt/user-roles-path   ["realm_access" "roles"]
                       ::oidc/oidc-redirect-success "/oauth2/openid/callback"
                       ::oidc/oidc-redirect-error   "/login-error"
                       ::oidc/oidc-redirect-logout  "/logout-success"
                       ::hash/constraint-mappings   app/constraints}
      handler-api     {::context/path                       "/api"
                       ::context/virtual-hosts              ["@connector-3443"]
                       ::context/ring-handler               (app/api-handler)
                       ::security/handler                   :oidc
                       ::session/enabled?                   false
                       ::oidc/authorization-flow            :client-credentials
                       ::oidc.jwks/endpoint                 "http://localhost:8080/realms/master/protocol/openid-connect/certs"
                       ::oidc.jwt.at.verification/exact-iss "http://localhost:8080/realms/master"
                       ::oidc.jwt.at.verification/exact-aud "https://slipway.io/demo-api"
                       ::oidc.jwt/user-id-path              ["preferred_username"]
                       ::oidc.jwt/user-roles-path           ["realm_access" "roles"]
                       ::oidc/constraint-mappings           app/constraints}
      handler-otel    {::context/path             "/otel"
                       ::context/virtual-hosts    ["@connector-3000"]
                       ::context/ring-handler     (handler/otel-handler)
                       ::security/handler         :hash
                       ::hash/realm               "slipway"
                       ::hash/users               [["prometheus" "password" ["metrics"]]]
                       ::hash/authenticator       (BasicAuthenticator.)
                       ::hash/constraint-mappings app/constraints}]
  (slipway/start
   #::server{:connectors    [connector-http connector-https]
             :handler       {::server/handler-type ::context/handler-collection
                             ::context/handlers    [handler-ui handler-api handler-otel]}
             :error-handler app/server-error-handler}))
```

## License

Distributed under the Apache 2.0 License.

Copyright (c) [Factor House](https://factorhouse.io)
