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
* [Full-Stack Development](#full-stack-development)
* [Configuration](#configuration)
    * [Example](#example)
    * [How is Slipway configured?](#how-is-slipway-configured)
    * [ns: slipway](#ns-slipway)
    * [ns: slipway.server](#ns-slipwayserver)
    * [ns: slipway.connector.http](#ns-slipwayconnectorhttp)
    * [ns: slipway.connector.https](#ns-slipwayconnectorhttps)
    * [ns: slipway.context](#ns-slipwaycontext)
    * [ns: slipway.compression](#ns-slipwaycompression)
    * [ns: slipway.session](#ns-slipwaysession)
    * [ns: slipway.security](#ns-slipwaysecurity)
        * [Security, Session Expiration, and Websockets](#security-session-expiry-and-websockets)
    * [ns: slipway.security.hash](#ns-slipwaysecurityhash)
    * [ns: slipway.security.jaas](#ns-slipwaysecurityjaas)
    * [ns: slipway.security.oidc](#ns-slipwaysecurityoidc)
* [Contributions](#contributions)
* [License](#license)

----

# Introduction

Slipway provides first-class support for embedded [Eclipse Jetty](https://www.eclipse.org/jetty/) 12.1
in [Clojure](https://clojure.org/).

The goal of this project is to provide access in Clojure to the full set of capabilities implemented by Jetty.

The term Slipway, in English, can almost be considered to mean "a small Jetty". They both allow access to water.

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

Implementations supporting Jetty 9, 10, and 11 are no longer maintained and preserved in the [`archive/`](archive/)
directory.

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

Slipway decodes incoming Jetty Request objects into a Clojure map that resembles
a [Ring](https://github.com/ring-clojure/ring)
map with additional request, response, and authentication information,
see: [src/slipway/request.clj](src/slipway/request.clj):

```Clojure
(defn request-map
  [^Request request ^Response response]
  (merge (ring-like-map request)
         {::request       request
          ::response      response
          ::user/identity (authenticated-user request)}))
```

Maintaining compatibility with Ring is not a goal of this project and future request-maps may not be 'Ring-like'.

Read more about our intention to remove Ring and Sente from this project [here](docs/ring-and-sente.md).

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

### Example

Here is an example of a server with multiples connectors and multiple handlers, where handlers are pinned to specific
connectors with virtual-host configuration, and where each handler has a different authentication mode.

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

### How is Slipway configured?

Slipway is configured with namespaced maps, each of those namespaced maps corresponds to a Slipway namespace that
provides the implementation of the Jetty concepts described by the configuration.

In some cases maps of many namespaces are merged, in particular this is the case for handlers. A handler may be
configured with many different Jetty concepts, including authentication, compression, session, etc. Each of those
concepts has a namespace, and merging maps (or just providing a map of mixed namespaces as above) results in the
handler being created with all of the capabilities configured.

Slipway is extensible, many of the underlying implementations are driven by multimethods that dispatch on the values
within those namespaced maps. Because Slipway uses multimethods, Slipway is open for extension by you.

Slipway contains sensible defaults and in will always fall-back to whatever Jetty provides as default.

Each namespace in Slipway contains a comment describing its configuration, each of those comments is also copied into
the `slipway.clj` namespace, so there is a single point of reference in code.

Each of those namespaces are described below, the title links to the source code in each case.

### [ns: slipway](src/slipway.clj)

The root namespace containing `start` and `stop` functions. They do what you would expect with a map of config.

`slipway/start` takes a map of `slipway.server` configuration, and returns a Jetty server.

```clojure 
(slipway/start #:slipway.server{})
```

`slipway/stop` takes a Jetty server, and stops it.

#### slipway configuration

```clojure
#:slipway{:join? "join the Jetty threadpool, blocks the calling thread until jetty exits, default false"} )
```

### [ns: slipway.server](src/slipway/server.clj)

Server configuration must include at least one `connector` or multiple `connectors`, and one `handler`.

The type of handler used can vary depending on the dispatch value of `::handler-type`. Slipway provides two
handlers by default, a `:slipway.context/handler` (the default), and a `:slipway.context/handler-collection` which
allows for multi-handler implementations. The example (above) is configured with a handler-collection.

As always, these implementations represent Jetty `ContextHandler` or `ContextHandlerCollection` objects.

This is a good example of Slipway representing Jetty concepts, rather than trying to support `:handlers` plural.

#### slipway.server configuration

```clojure
#:slipway.server
        {:connector     "the connector supported by this server"
         :connectors    "the connectors supported by this server (when many connectors supported)"
         :handler       "the handler for this server"
         :handler-type  "the dispatch value for the handler implementation :default is slipway.context/handler"
         :thread-pool   "the thread-pool used by this server (nil for default behaviour)"
         :scheduler     "the scheduler used by this server (nil for default behaviour)"
         :buffer-pool   "the buffer-pool used by this server (nil for default behaviour)"
         :error-handler "the error-handler used by this server for Jetty level errors (nil for default behaviour)"}
```                   

### [ns: slipway.connector.http](src/slipway/connector/http.clj)

Configure a [HTTP ServerConnector](https://jetty.org/docs/jetty/12.1/programming-guide/server/http.html#connector).

The `:name` field can be used to bind one or more handlers to ths connector
via [Jetty Virtual Hosts](https://jetty.org/docs/jetty/12.1/operations-guide/deploy/index.html#virtual-hosts).

The `:http-forwarded` field enables
Jetty's [Forwarded Module](https://jetty.org/docs/jetty/12.1/operations-guide/modules/standard.html#forwarded) which can
be critical for proxied deployments, specifically when authentication is configured and url-redirects need to be aware
of the originating request context.

#### slipway.connector.http configuration

```clojure
#:slipway.connector.http
        {:name                       "the name of this connector (useful for VirtualHosts configuration)"
         :host                       "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces"
         :port                       "port this connector listens on. If set to 0 a random port is assigned which may be obtained with getLocalPort(), default 80"
         :idle-timeout-ms            "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 30000 ms"
         :http-forwarded?            "if true, add the ForwardRequestCustomizer. See Jetty Forward HTTP docs"
         :proxy-protocol?            "if true, add the ProxyConnectionFactory. See Jetty Proxy Protocol docs"
         :http-config                "a concrete HttpConfiguration object to replace the default config entirely"
         :configurator               "a fn taking the final connector as argument, allowing further configuration"
         :send-server-version?       "if true, send the Server header in responses"
         :send-date-header?          "if true, send the Date header in responses"
         :relative-redirect-allowed? "if true, allow relative redirects, default false"
         :http-compliance            "set the HttpCompliance mode, defaults to HttpCompliance/RFC9110"}
```

### [ns: slipway.connector.https](src/slipway/connector/https.clj)

Configure a [HTTPS ServerConnector](https://jetty.org/docs/jetty/12.1/programming-guide/server/http.html#connector).

The same rules about `:name` and `:http-forwarded` apply as to the `slipway.connector.http` configuration.

#### slipway.connector.https configuration

```clojure
#:slipway.connector.https
        {:name                       "the name of this connector (useful for VirtualHosts configuration)"
         :host                       "the network interface this connector binds to as an IP address or a hostname.  If null or 0.0.0.0, then bind to all interfaces. Default null/all interfaces"
         :port                       "port this connector listens on. If set to 0 a random port is assigned which may be obtained with getLocalPort(). default 443"
         :idle-timeout-ms            "max idle time for a connection, roughly translates to the Socket.setSoTimeout. Default 30000 ms"
         :http-forwarded?            "if true, add the ForwardRequestCustomizer. See Jetty Forward HTTP docs"
         :proxy-protocol?            "if true, add the ProxyConnectionFactory. See Jetty Proxy Protocol docs"
         :http-config                "a concrete HttpConfiguration object to replace the default config entirely"
         :configurator               "a fn taking the final connector as argument, allowing further configuration"
         :keystore                   "keystore to use, either path (String) or concrete KeyStore"
         :keystore-type              "type of keystore, e.g. JKS"
         :keystore-password          "password of the keystore"
         :key-manager-password       "password for the specific key within the keystore"
         :truststore                 "truststore to use, either path (String) or concrete KeyStore"
         :truststore-password        "password of the truststore"
         :truststore-type            "type of the truststore, eg. JKS"
         :include-protocols          "a list of protocol name patterns to include in SSLEngine"
         :exclude-protocols          "a list of protocol name patterns to exclude from SSLEngine"
         :replace-exclude-protocols? "if true will replace existing exclude-protocols, otherwise will add them"
         :exclude-ciphers            "a list of cipher suite names to exclude from SSLEngine"
         :replace-exclude-ciphers?   "if true will replace existing exclude-ciphers, otherwise will add them"
         :security-provider          "the security provider name"
         :client-auth                "either :need or :want to set the corresponding need/wantClientAuth field"
         :ssl-context                "a concrete pre-configured SslContext"
         :sni-required?              "if true SNI is required, else requests will be rejected with 400 response, default false"
         :sni-host-check?            "if true the SNI Host name must match when there is an SNI certificate, default false"
         :sts-max-age-s              "set the Strict-Transport-Security max age in seconds, default -1"
         :sts-include-subdomains?    "true if a include subdomain property is sent with any Strict-Transport-Security header"
         :send-server-version?       "if true, send the Server header in responses"
         :send-date-header?          "if true, send the Date header in responses"
         :relative-redirect-allowed? "if true, allow relative redirects, default false"
         :http-compliance            "set the HttpCompliance mode, defaults to HttpCompliance/RFC9110"}
```

### [ns: slipway.context](src/slipway/context.clj)

Configure a
Jetty [ContextHandler](https://jetty.org/docs/jetty/12.1/programming-guide/server/http.html#handler-use-context)
scoped to a context-path, all requests to that path will be handled by this context handler, and ultimately the
ring-handler
that it is configured with.

The default implementation of ContextHandler can be configured with compression, session, security, and websocket
support.
These features are configured by providing a map of mxied namespaces, see the examples configuration for more.

#### slipway.context configuration

```clojure
#:slipway.context
        {:path            "the context path, default '/'"
         :ring-handler    "the ring-handler descendant of this context-handler"
         :null-path-info? "true if /path is not redirected to /path/, default true"
         :virtual-hosts   "a list of ^String virtual hosts for the context"
         :error-handler   "the error-handler used by this context-handler for context level errors"
         :handlers        "a sequence of [:slipway.context], when used with ::server/handler of ::context/handler-collection"}
```

### [ns: slipway.compression](src/slipway/compression.clj)

Configured within a handler, enables compression for that handler. Is enabled with `:gzip` compression by default.

Jetty provides support for `brotli` and `zstd` compression, but we have currently only implemented `gzip` because that
is all we need. If you need either of the other compression formats please just raise a bug ticket.

#### slipway.compression configuration

```clojure
#:slipway.compression
        {:enabled?           "is a compression handler enabled? default true"
         :path-spec          "the compression path-spec, default '/*'"
         :format             "compression format, defaults to :gzip"
         :compress-min-bytes "min response size to trigger compression (default 1024 bytes)"
         :compression-config "a concrete Jetty CompressionConfig instance (nil for default configuration)"}
```

### [ns: slipway.session](src/slipway/session.clj)

Configured within a handler, enables sessions for that handler. Is enabled by default.

#### slipway.session configuration

```clojure
#:slipway.session
        {:enabled?                "are sessions enabled for this server? Default true"
         :secure-request-only?    "set the secure flag on session cookies"
         :http-only?              "set the http-only flag on session cookies"
         :same-site               "set session cookie same-site policy to :none, :lax, or :strict"
         :max-inactive-interval-s "max session idle time (in s)"
         :cookie-name             "the name of the session cookie"
         :session-id-manager      "the meta manager used for cross context session management"
         :refresh-cookie-age-s    "max time before a session cookie is re-set (in s)"
         :using-cookies           "true if cookies are used to track sessions (default true)"
         :using-uri-parameters    "true if uri parameters are used to track sessions (default false)"
         :path-parameter-name     "name of path parameter used for URL session tracking"}
```

### [ns: slipway.websockets](src/slipway/websockets.clj)

Configured within a handler, enables websockets within that handler. Is disabled by default.

#### slipway.websockets configuration

```clojure
#:slipway.websockets
        {:enabled?                 "are websockets enabled? default false"
         :path-spec                "the websocket path-spec, default '/chsk'"
         :idle-timeout-ms          "max websocket idle time, default 300000"
         :input-buffer-bytes       "max websocket input buffer size"
         :output-buffer-bytes      "max websocket output buffer size"
         :max-text-message-bytes   "max websocket text message size that can be received"
         :max-binary-message-bytes "max websocket binary message size that can be received"
         :max-frame-bytes          "max websocket frame size"
         :max-outgoing-frames      "max websocket frames waiting to be sent per session, default -1"
         :auto-fragment            "websocket auto fragment (boolean), default true"}
```

### [ns: slipway.security](src/slipway/security.clj)

#### slipway.security configuration

Configured within a handler, enables auth within that handler.

Slipway supports `jaas`, `hash`, and `oidc` out of the box.

#### Security, Session Expiry, and Websockets

```clojure
#:slipway.security{:handler "identifies a SecurityHandler impl, :jaas', :hash, and :oidc supported by default"}
```

Of the three auth implementation provided by Slipway, only OIDC has any concept of session expiry.

Jaas (including LDAP) and Hash auth both consider the user 'logged-in' until they choose to log-out, or until
you deliberately invalidate the user session; see `slipway.request/invalidate-session`.

OIDC has a concept of session expiry that is complected with the type of connection your user has with the underlying
Jetty server, that connection being either HTTP or Websocket. See the [OIDC](#ns-slipwaysecurityoidc) section for more
details.

### [ns: slipway.security.hash](src/slipway/security/hash.clj)

Configured within a handler,
enables [HashLoginService](https://jetty.org/docs/jetty/12.1/programming-guide/security/index.html#security-login-service)
auth.

#### slipway.sercurity.hash configuration

```clojure
#:slipway.security.hash
        {:realm                 "optional Jetty authentication realm"
         :user-file             "the path to a Jetty hash-user file"
         :hot-reload-interval-s "the period in seconds to scan :user-file for changes"
         :users                 "a sequence of [^String user-name, ^String credential, ^String[] [roles]]"
         :authenticator         "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
         :constraint-mappings   "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"
         :identity-service      "an (optional) concrete Jetty IdentityService"}
```

### [ns: slipway.security.jaas](src/slipway/security/jaas.clj)

Configured within a handler,
enables [JaasLoginService](https://jetty.org/docs/jetty/12.1/programming-guide/security/index.html#security-login-service)
auth.

JAAS enables a user to configure
Jetty's [LDAPLoginModule](https://jetty.org/docs/jetty/12.1/operations-guide/security/jaas-support.html#ldaploginmodule)
and [PropertyFileLoginModule](https://jetty.org/docs/jetty/12.1/operations-guide/security/jaas-support.html#ldaploginmodule),
which is effectively the same as the HashLoginService, above.

JAAS security requires extra configuration to be provided to the JVM at startup time like so:

```bash
-Djava.security.auth.login.config=/dev-resources/jaas/ldap-jaas.conf
```

See
the [Jetty JAAS documentation](https://jetty.org/docs/jetty/12.1/operations-guide/security/jaas-support.html#loginconf)
and Slipways example [ldap-jaas.conf](dev-resources/jaas/ldap-jaas.conf)
and [hash-jaas.conf](dev-resources/jaas/hash-jaas.conf)
for more information.

#### slipway.sercurity.jaas configuration

```clojure
#:slipway.security.jaas
        {:realm               "the Jetty authentication realm"
         :authenticator       "a concrete Jetty Authenticator (e.g. FormAuthenticator or BasicAuthenticator)"
         :constraint-mappings "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"
         :identity-service    "an (optional) concrete Jetty IdentityService"}
```

### [ns: slipway.security.oidc](src/slipway/security/oidc.clj)

Configured within a handler,
enables [OIDC](https://jetty.org/docs/jetty/12.1/programming-guide/security/openid-support.html) auth.

Slipway supports two OIDC flows out of the box, `Authorization Code Flow`, and `Client Credentials Flow`.

```clojure
#:slipway.security.oidc
        {:authorization-flow               ":authorization-code or :client-credentials (default :authorization-code)"
         :issuer                           "the URL of the OIDC provider"
         :client-id                        "OAuth 2.0 Client Identifier valid at the OIDC provider"
         :client-secret                    "the client secret known only by the Client and the OIDC provider"
         :authorization-endpoint           "the URL of the OIDC provider's authorization endpoint if configured"
         :token-endpoint                   "the URL of the OIDC provider's token endpoint if configured"
         :authentication-method            "authentication method to use with the Token Endpoint"
         :end-session-endpoint             "the URL of the OIDC provider's end session endpoint if configured"
         :jwks-endpoint                    "the URL of the OIDC provider's public cryptographic keys for verifying JWT token signatures"
         :http-client                      "the (optional) HttpClient instance to use"
         :scopes                           "a sequence of ^String scopes to request, included in addition to 'openid' scope which is always requested, default is ['profile' 'email']"
         :logout-when-id-token-is-expired? "whether to logout when the ID token is expired, default false"
         :oidc-redirect-success            "the path where the OIDC provider redirects back to Jetty"
         :oidc-redirect-error              "optional page where authentication errors are redirected"
         :oidc-redirect-logout             "optional page where the user is redirected to this page after logout"
         :identity-fn                      "optional Clojure function applied to user identity post-authentication, pre-user-identity creation"
         :identity-service                 "a concrete Jetty IdentityService"
         :constraint-mappings              "a vector of [^String pathSpec, org.eclipse.jetty.security.Constraint]"}
```

## Contributions

This library warmly accepts bugs and issues raised in the attached Github issue tracker.

This library is built by the team at Factor House and is not able to accept contributions from external parties beyond
bugs and issues raised in the attached github issue tracker.

The maintainer of this library enjoys the conviviality of discussing ideas and solving problems with humans.

The maintainer of this library does not accept or respond to correspondence from computer programs that mimic the
behaviour of humans.

## License

Distributed under the Apache 2.0 License.

Copyright (c) [Factor House](https://factorhouse.io)
