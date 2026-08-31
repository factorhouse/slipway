# Slipway: a Clojure Companion to Jetty 12.1

[![Slipway Test](https://github.com/factorhouse/slipway/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/factorhouse/slipway/actions/workflows/ci.yml)
[![Clojars Project](https://img.shields.io/clojars/v/io.factorhouse/slipway-jetty12.svg)](https://clojars.org/io.factorhouse/slipway-jetty12)

----

## Slipway by [Factor House](https://factorhouse.io)

* [Introduction](#introduction)
* [Using slipway](#using-slipway)
    * [Installation](#installation)
    * [JVM support](#jvm-support)
    * [Archived versions](#archived-versions)
* [Eclipse Jetty](#eclipse-jetty)
    * [Slipway requests](#slipway-requests)
* [Future goals](#future-goals)
* [Full-stack development](#full-stack-development)
* [Example system](#example-system)
* [Example](#example)
* [Configuration](#configuration)
    * [ns: slipway](#ns-slipway)
    * [ns: slipway.server](#ns-slipwayserver)
    * [ns: slipway.connector.http](#ns-slipwayconnectorhttp)
    * [ns: slipway.connector.https](#ns-slipwayconnectorhttps)
    * [ns: slipway.context](#ns-slipwaycontext)
    * [ns: slipway.compression](#ns-slipwaycompression)
    * [ns: slipway.session](#ns-slipwaysession)
    * [ns: slipway.security](#ns-slipwaysecurity)
        * [Security constraints](#security-constraints)
        * [Session expiration and websockets](#session-expiration-and-websockets)
        * [Proxied installations and redirects](#proxied-installations-and-redirects)
    * [ns: slipway.security.hash](#ns-slipwaysecurityhash)
    * [ns: slipway.security.jaas](#ns-slipwaysecurityjaas)
    * [ns: slipway.security.oidc](#ns-slipwaysecurityoidc)
        * [Authorization Code Flow](#authorization-code-flow)
        * [Client Credentials Flow](#client-credentials-flow)
    * [ns: slipway.security.oidc.jwt](#ns-slipwaysecurityoidcjwt)
    * [ns: slipway.security.oidc.jwks](#ns-slipwaysecurityoidcjwks)
    * [ns: slipway.security.oidc.jws](#ns-slipwaysecurityoidcjws)
    * [ns: slipway.security.oidc.jwk](#ns-slipwaysecurityoidcjwk)
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
* Multi-connector, multi-handler support with Virtual Hosts configuration.
* Full support for Jaas, LDAP, Hash, and OIDC authentication.
* Extended OIDC support including:
    * Authorization Code Flow with refresh token redemption.
    * Client Credentials Flow with token validation
      via [Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt).

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

### JVM support

Slipway (and Jetty 12.1) Requires Java 17+. Archived version support Java 8 and Java 11.

### Archived versions

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

### Slipway requests

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

## Future goals

* Remove `org.ring-clojure/ring-core-protocols` dependency.
* Replace Sente with a Slipway-like [Socket.IO](https://socket.io/) implementation.
* Advance Slipway OIDC capabilities to support OAuth 2.1 by either:
    * Contributing to Jetty (see: https://github.com/jetty/jetty.project/discussions/15611), or;
    * Adoption and integration of [pac4j](https://www.pac4j.org/), augmenting existing Jetty-provided security model.

## Full-stack development

Slipway is constrained to the stated goals of this project, and those goals exclude any interest beyond Jetty.

[Factor House](https://factorhouse.io) has a commmon product architecture which is a conservative, security-conscious
web-server deployment with websocket support and a rich, data-oriented UI.

To meet the needs of that architecture we leverage [HSX](https://github.com/factorhouse/hsx) and
[RFX](https://github.com/factorhouse/rfx) among other libraries.

This project's [integration tests](test/integration/slipway) contain many example server deployments, but those examples
have a very simple non-websocket architecture due to the constraints of this library.

It is our desire to provide a full-stack reference architecture in [Shortcut](https://github.com/factorhouse/shortcut),
however that has been a desire for quite some time and we have lots of other priorites.

## Example System

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

## Configuration

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
of the originating request context. See further explanation in the security section.

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

Read the [Jetty Security](https://jetty.org/docs/jetty/12.1/programming-guide/security/index.html) documentation to
better understand concepts like SecurityHandlers, Constraints, and AuthenticationState.

#### slipway.security configuration

Configured within a handler, enables auth within that handler.

Slipway supports `jaas`, `hash`, and `oidc` out of the box.

```clojure
#:slipway.security{:handler "identifies a SecurityHandler impl, :jaas', :hash, and :oidc supported by default"}
```

#### Security constraints

Security implementations within Jetty detect if a request is being made for resource that has
a [Constraint](https://javadoc.jetty.org/jetty-12.1/org/eclipse/jetty/security/class-use/Constraint.html)
that requires authentication, and then if the request contains
[AuthenticationState](https://javadoc.jetty.org/jetty-12.1/org/eclipse/jetty/server/Request.AuthenticationState.html)
representing an authenticated user.

If the resource is protected, and the request contains no AuthenticationState, the server will respond with a redirect,
sending the user to some sort of `/login` flow. This flow differs per implementation.

Jetty constraints are easily configured in Slipway with simple Clojure vectors:

```clojure
(def constraints
  [["/up" Constraint/ALLOWED]
   ["/css/*" Constraint/ALLOWED]
   ["/img/*" Constraint/ALLOWED]
   ["/logout-success" Constraint/ALLOWED]
   ["/*" Constraint/ANY_USER]])
```

#### Session expiration and websockets

Of the three auth implementation provided by Slipway, only OIDC has any concept of session expiry.

Jaas (including LDAP) and Hash auth both consider the user 'logged-in' until they choose to log-out, or until
you deliberately invalidate the user session; see `slipway.request/invalidate-session`.

OIDC has a concept of session expiry that is complected with the type of connection your user has with the underlying
Jetty server, that connection being either HTTP or Websocket. OIDC also has the concept of session refresh, which is
very useful if you provide a Single Page Application (SPA), however it makes things slightly more complicated again.

Jetty is primarily for the business of serving Http traffic, and contains many mature and common features for the
purpose of serving that traffic, HttpSessions, Cookies, AuthenticationState on Requests, and so on.

Jetty will check your AuthenticationState on each HttpRequest and invalidate your session if your AuthenticationState
has expired (optionally, and by default this check is not enabled).

However once you have upgraded your connection to Websocket from Http, those HTTP features are discarded. There is
normally no further HttpRequest, Jetty does not monitor and act on Websocket traffic in the same way, the users
HttpSession will expire and be scavenged, all while the Websockets channel remains active.

This means that you have to implement your own expiration check of user sessions once you have moved the user connection
into Websocket world. You also have to implement your own Constraint system, as Jetty Constraints only apply to Http.

#### Proxied installations and redirects

Imagine you are running a Slipway-based system behind a reverse-proxy, where the proxy terminates HTTPS, and further
your instance is running at a sub-path that it is unaware of.

E.g. your instance thinks it is running inside a Docker container and is simply binding to:

```
http://localhost:3000/
```

Your users are accessing that system via the URL exposed by the reverse-proxy:

```
https://devtools.zcorp.com/kafka/kpow
```

Regardless if your implementation requires relative or absolute redirect URLs, there's no way of performing that
redirect from the Slipway server without any further context.

One solution is to encode the 'true' base URL in configuration to your server, in each instance, and then rely on that
information to calculate the redirects.

Jetty has a better solution, which is to implement as broad a range possible of the different request-header
based solutions that have been implemented by teams at scale trying to solve this problem already.

This solution is presented as the
Jetty [Http Forwarded](https://jetty.org/docs/jetty/12.1/operations-guide/modules/standard.html#forwarded) module, and
when configured Jetty will detect and use a range of standard forwarded-headers provided by the proxy to the server,
and adjust redirects accordingly.

This can be very important specifically with Jetty Security implementations. Set the `http-forwarded?` option on
your [slipway.connector.http](#ns-slipwayconnectorhttp) or [slipway.connector.https](#ns-slipwayconnectorhttps)
connectors to enable the module.

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

#### [Authorization Code Flow](https://openid.net/specs/openid-connect-core-1_0.html#CodeFlowAuth)

This is the flow you are probably most familiar with, it is commonly used to authenticate human users who are redirected
through the IdP authentication flow, and after authenticating the RP retrieves tokens (id, access, and refresh) for that
user from the IdP token endpoint.

When using Authorization Code Flow, you can configure only the `client-id`, `client-secret`, and `issuer`. If your IdP
respects the `/.well-known/openid-configuration` OIDC format the rest of the configuration is discovered.

#### [Client Credentials Flow](https://oauth.net/2/grant-types/client-credentials/)

This flow is is for machine-to-machine communication.

A user manually obtains an access token from their IdP and configures it to be sent to a Slipway server encoded as
a bearer token header in the request, e.g. `Bearer: token-here`. When implementing Client Credentials it is required
to configure the `jwks-endpoint`, as that endpoint provides the public certificates that are used to validate the
provenance of the bearer token.

The example system in this readme demonstrates both flows for your reference.

#### slipway.security.oidc configuration

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

### [ns: slipway.security.oidc.jwt](src/slipway/security/oidc/jwt.clj)

Configured within a handler, determines how token-state for a user is interpreted.

Vendor IdP (e.g Microsoft Entra-ID, Okta, Keycloak) often encode user information differently in the auth tokens, then
client-side implementations tend to differ again.

This configuration gives you the ability to decode id, roles, and expiration information with some flexibility.

#### slipway.security.oidc.jwt configuration

```clojure
#:slipway.security.oidc.jwt
        {:user-roles-source      "the token containing user roles, either 'access_token' or 'id_token' (default is 'access_token')"
         :user-roles-path        "the path within the source token to find user roles, default is ['roles']"
         :user-id-source         "the token containing user id, either 'access_token' or 'id_token' (default is 'id_token')"
         :user-id-path           "the path within the source token to find user name, default is ['sub']"
         :user-expiration-source "the token used for session expiration, either 'access_token' or 'id_token' (default is 'access_token')"}
```

### [ns: slipway.security.oidc.jwks](src/slipway/security/oidc/jwks.clj)

When implementing `Client Credentials Flow` for OIDC, it is necessary to validate the provenance of the access-token
provided as a `Bearer: your-token-here` request header.

This library integrates the widely-used [Nimbus JOSE + JWT](https://connect2id.com/products/nimbus-jose-jwt) library to
perform that provenance validation.

In short, the JOSE library will retrieve public keys from the configured `jwks` endpoint, cache them, and use those
keys to perform a cryptographic validation that the JWT has been signed by the configured source.

#### slipway.security.oidc.jwks configuration

```clojure
;; This configuration is interesting because many of them are required to be input as pairs.
;; The user should familiarise themselves with the underlying builder implementation.
#:slipway.security.oidc.jwks
        {:endpoint                  "the jwks endpoint url"
         :cache?                    "enable caching of the jwks set"
         :cache-ttl                 "the time to live of the cached JWK set, in milliseconds"
         :cache-refresh-timeout     "the cache refresh timeout, in milliseconds."
         :cache-forever?            "enable caching of the jwks set without expiration"
         :refresh-ahead-cache?      "enable refresh-ahead caching of the JWK set"
         :refresh-ahead-time        "the refresh ahead time, in milliseconds"
         :scheduled?                "refresh in a scheduled manner, regardless of requests"
         :rate-limited?             "rate limit the JWK set retrieval"
         :rate-limited-min-interval "the minimum allowed time interval between two JWK set retrievals"
         :retrying?                 "enables single retrial to retrieve the JWK set to work around transient network issues"
         :outage-tolerant?          "enable outage tolerance by serving a cached JWK set in case of outage"
         :outage-tolerant-forever?  "enable outage tolerance without expiration"
         :outage-tolerant-ttl       "the time to live of the cached JWK set to cover outages, in milliseconds"}
```

### [ns: slipway.security.oidc.jws](src/slipway/security/oidc/jws.clj)

When implementing `Client Credentials Flow` for OIDC, it may be necessary to specify the Json Web Signature (JWS)
algorithm or algorithms that can apply when validating the provided access-token.

#### slipway.security.oidc.jws configuration

```clojure
#:slipway.security.oidc.jws{:algorithm  "JSON Web Signature (JWS) algorithm name, represents the alg header parameter in JWS objects. Default is RS256"
                            :algorithms "a sequence of :algorithm if accepting multiple JWS algorithms"}
```

### [ns: slipway.security.oidc.jwt.at.verification](src/slipway/security/oidc/jwt/at/verification.clj)

When implementing `Client Credentials Flow` for OIDC, it is necessary to specify how the access-token should be
verified. This configuration gives you the flexibility required to ensure the access-token isl reliable.

#### slipway.security.oidc.jwt.at.verification configuration

```clojure
#:slipway.security.oidc.jwt.at.verification
        {::exact-typ       "a sequence of acceptable 'typ' fields, default is ['at+jwt' 'application/at+jwt']"
         ::exact-iss       "required: the URL of the OIDC provider"
         ::exact-aud       "required: the audience of this service to match the 'aud' field in the jwt"
         ::required-claims "set of required JWTClaimNames. Default #{JWTClaimNames/JWT_ID JWTClaimNames/SUBJECT JWTClaimNames/ISSUED_AT JWTClaimNames/EXPIRATION_TIME}"}
```

### [ns: slipway.security.oidc.jwk](src/slipway/security/oidc/jwks.clj)

It is not expected that you will use this configuration, however it is possible to swap out the `jwks` implementation
for a different one if you prefer when implementing `Client Credentials Flow` for OIDC,

For instance, in our tests we use an `ImmutableJWKSet` of a single key, rather than a full `jwks` source for testing
purposes. See [slipway.security.oidc.jwk.rsa](src/slipway/security/oidc/jwk/rsa.clj)
and [the test that uses that source](test/integration/slipway/security/oidc_client_credentials_test.clj).

#### slipway.security.oidc.jwk configuration

```clojure
#:slipway.security.oidc.jwk{::source "configurable JWT key source, leave empty for default (JWKS)"}
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
