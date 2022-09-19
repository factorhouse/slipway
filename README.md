# Slipway
[![Slipway Test](https://github.com/operatr-io/slipway/actions/workflows/ci.yml/badge.svg?branch=main)](https://github.com/operatr-io/slipway/actions/workflows/ci.yml)

[Eclipse Jetty](https://www.eclipse.org/jetty/) is the web server at the heart of our product, [Kpow for Apache Kafka®](https://kpow.io).

Slipway is our [Clojure](https://clojure.org/) companion to Embedded Jetty.

![Slipway Login](docs/img/slipway-login.png)

## Why Jetty?

Jetty is a mature, stable, commercially supported project with an [active, deeply experienced](https://github.com/eclipse/jetty.project/graphs/contributors) core team of contributors.

Ubiquitous in the enterprise Java world, Jetty has many eyes raising issues and driving improvments.

More than a simple web server, Jetty is battle-tested, performant, and feature rich.

## Our Requirements

Kpow is a secure web-application with a rich SPA UI served by websockets. 

Deployed in-cloud and on-premises Kpow has seemingly every possible Jetty configuration option in use by at least one end-user.

> User: Can I configure a custom CA certificate to secure my JAAS/LDAPS authentication?

> Kpow Team: Yes (thanks to Jetty).

We have a hard requirement to support customers on Java 8 and Java 11+ and incorporate feedback from external security teams.

## Primary Goals

Slipway aims to provide first-class, extensible support for: 

* HTTP 1.1
* HTTPS / SSL
* Synchronous handlers
* JAAS Authentication (LDAP, HashUser, etc)
* Form / basic authentication
* WebSockets
* Java 8 / 11+
* Jetty 9 / 10 / 11
* Session management
* Proxy protocol / http forwarded
* Common / sensible defaults (e.g. gzip compression)
* Configurable error handling
* Automated CVE scanning with NVD
* Comprehensive integration tests
* Ring compatibility

## Secondary Goals

* Broad support for general Jetty use-cases / configuration

## Future Goals

* Backport our SAML, OpenID and OAuth authentication implementations

## Currently Out Of Scope

* Http2/3
* Asynchronous Handlers

## Non-Goals

* A simplified DSL for Jetty

## Usage

### Installation

Slipway will shortly be available from Clojars. 

Add one of the version-specific dependencies to your project:

```clojure 
;; Jetty 10: Recommended for general use, requires Java 11+

[io.factorhouse/slipway-jetty10 "1.1.0"]
```

```clojure 
;; Jetty 9: If you require running with Java 8

[io.operatr/slipway-jetty9 "1.1.0"]
```

```clojure 
;; Jetty 11: If you want to run with Jakarta rather than Javax

[io.operatr/slipway-jetty9 "1.1.0"]
```

### Running a server

Slipway works like all other Ring adapters:

```clojure 
(require '[slipway :as slipway])
(require '[slipway.server :as server])
(require '[slipway.connector.http :as http])

(defn handler [_] {:status 200 :body "Hello world"})

(slipway/start handler #::server{:connectors #::http{:port 3000}})
```

#### Options

The third argument to `run-jetty` is the options map:

```
  :http? - allow connections over HTTP
  :port - the port to listen on (defaults to 3000)
  :host - the hostname to listen on
  :join? - blocks the thread until server ends (defaults to false)
  :auth - Map of auth opts. Configures Jetty JAAS auth, see JAAS Integration section of README
  :http-forwarded? - support for X-Forwarded-For header (defaults to false)
  :gzip? - enables Gzip compression on the server (defaults to true)
  :gzip-content-types - contents types to apply Gzip compression to (defaults to ["text/css" "text/plain" "text/javascript" "application/javascript" "image/svg+xml"])
  :gzip-min-size - the minimum size (in bytes) to apply Gzip compression to the response body. Default 1024
  :error-handler - sets an error handlers on the server for catch-all Jetty errors (something that extends the `org.eclipse.jetty.server.handler.ErrorHandler` class)
  :daemon? - use daemon threads (defaults to false)
  :send-server-version? - whether to send the Server header in responses (default false)
  :send-date-header? - whether to send Date header in responses (default false)
  :output-buffer-size - set the size of the buffer into which response content is aggregated before being sent to the client. A larger buffer can improve performance by allowing a content producer to run without blocking, however larger buffers consume more memory and may induce some latency before a client starts processing the content. (default 32768)
  :request-header-size - sets the maximum allowed size in bytes for the HTTP request line and HTTP request headers (default 8192) 
  :response-header-size -  the maximum size in bytes of the response header (default 8192)
  :header-cache-size - the size of the header field cache, in terms of unique characters (default 512)
  :ssl? - allow connections over HTTPS
  :ssl-port - the SSL port to listen on (defaults to 443, implies :ssl?)
  :ssl-context - an optional SSLContext to use for SSL connections
  :keystore - the keystore to use for SSL connections
  :keystore-type - the format of keystore
  :key-password - the password to the keystore
  :key-manager-password - the password for key manager
  :truststore - a truststore to use for SSL connections
  :truststore-type - the format of trust store
  :trust-password - the password to the truststore
  :ssl-protocols - the ssl protocols to use, default to ["TLSv1.3" "TLSv1.2"]
  :ssl-provider - the ssl provider
  :exclude-ciphers      - when :ssl? is true, additionally exclude these
                          cipher suites
  :exclude-protocols    - when :ssl? is true, additionally exclude these
                          protocols
  :replace-exclude-ciphers?   - when true, :exclude-ciphers will replace rather
                                than add to the cipher exclusion list (defaults
                                to false)
  :replace-exclude-protocols? - when true, :exclude-protocols will replace
                                rather than add to the protocols exclusion list
                                (defaults to false)
  :thread-pool - the thread pool for Jetty workload
  :max-threads - the maximum number of threads to use (default 50), ignored if `:thread-pool` provided
  :min-threads - the minimum number of threads to use (default 8), ignored if `:thread-pool` provided
  :threadpool-idle-timeout - the maximum idle time in milliseconds for a thread (default 60000), ignored if `:thread-pool` provided
  :job-queue - the job queue to be used by the Jetty threadpool (default is unbounded), ignored if `:thread-pool` provided
  :max-idle-time  - the maximum idle time in milliseconds for a connection (default 200000)
  :ws-max-idle-time  - the maximum idle time in milliseconds for a websocket connection (default 500000)
  :ws-max-text-message-size  - the maximum text message size in bytes for a websocket connection (default 65536)
  :client-auth - SSL client certificate authenticate, may be set to :need, :want or :none (defaults to :none)
  :proxy? - enable the proxy protocol on plain socket port (see http://www.eclipse.org/jetty/documentation/9.4.x/configuring-connectors.html#_proxy_protocol)
  :sni-required? - require sni for secure connection, default to false
  :sni-host-check? - enable host check for secure connection, default to true
```

### WebSockets

Slipway provides the same API as the [ring-jetty9-adapter](https://github.com/sunng87/ring-jetty9-adapter) for upgrading HTTP requests to WebSockets. 

```clojure 
(require '[slipway.websockets :as ws])
(require '[slipway.server :as slipway])

(def ws-handler {:on-connect (fn [ws] (ws/send! ws "Hello world"))
                 :on-error (fn [ws e])
                 :on-close (fn [ws status-code reason])
                 :on-text (fn [ws text-message])
                 :on-bytes (fn [ws bytes offset len])
                 :on-ping (fn [ws bytebuffer])
                 :on-pong (fn [ws bytebuffer])})

(defn handler [req]
  (if (ws/upgrade-request? req)
    (ws/upgrade-response ws-handler)
    {:status 406}))
    
(slipway/run-jetty handler {:port 3000 :join? false})
```

The `ws` object passed to each handler function implements the `slipway.websockets.WebSockets` protocol:

```clojure 
(defprotocol WebSockets
  (send! [this msg] [this msg callback])
  (ping! [this] [this msg])
  (close! [this] [this status-code reason])
  (remote-addr [this])
  (idle-timeout! [this ms])
  (connected? [this])
  (req-of [this]))
```

#### Sente integration

Slipway supports [Sente](https://github.com/ptaoussanis/sente) out-of-the box. 

Simply include Sente in your project's dependencies and follow Sente's [getting started guide](https://github.com/ptaoussanis/sente#getting-started), and use the slipway web-server adapter:

```clojure 
(require '[slipway.sente :refer [get-sch-adapter]])
```

### JAAS integration

JAAS implements a Java version of the standard Pluggable Authentication Module (PAM) framework.

JAAS can be used for two purposes:

* for authentication of users, to reliably and securely determine who is currently executing Java code, regardless of whether the code is running as an application, an applet, a bean, or a servlet; and
* for authorization of users to ensure they have the access control rights (permissions) required to do the actions performed.

JAAS implements a Java version of the standard Pluggable Authentication Module (PAM) framework. See Making Login Services Independent from Authentication Technologies for further information.

For more information visit the [Jetty documentation](https://www.eclipse.org/jetty/documentation/jetty-10/operations-guide/index.html#og-jaas).

Slipway is the only ring adapter that supports Jetty JAAS out of the box. Thus, one of the few ways to authenticate using LDAP in the Clojure world. Oftentimes a requirement for the enterprise.

#### Usage

Pass an `:auth` key to your `run-jetty` options map:

```clojure 
(require '[slipway.auth.constraints :as constraints])

{:auth-method         "basic"                               ;; either "basic" (basic authentication) or "form" (form based authencation, with a HTML login form served at :login-uri)
 :auth-type           "jaas"                                ;; either "jaas" or "hash"
 :login-uri           "/login"                              ;; the URI where the login form is hosted
 :login-retry-uri     "/login-retry"
 :realm               "my-app"
 :logout-uri          "/logout"
 :session             {:http-only?            true
                       :same-site             :strict       ;; can be :lax, :strict or :none
                       :tracking-modes        #{:cookie}    ;; can be :url, :cookie :ssl
                       :max-inactive-interval -1}           ;; set the max period of inactivity, after which the session is invalidated, in seconds.
 :constraint-mappings (constraints/constraint-mappings
                       ;; /css/* is not protected. Everyone (including unauthenticated users) can access
                       ["/css/*" (constraints/no-auth)]
                       ;; /api/* is protected. Any authenticated user can access
                       ["/api/*" (constraints/basic-auth-any-constraint)])}
```

Successfully authenticated users will have their details assoced into the Ring request map under the key `:slipway.auth/user` - it contains: 

```clojure
{:provider :jetty
 :name     "Jane"
 :roles    ["admin"]}
```

#### Constraints

[Constraints](https://www.eclipse.org/jetty/javadoc/jetty-10/org/eclipse/jetty/util/security/Constraint.html) describe an auth and/or data constraint. 

The `slipway.auth.constraints` namespace has a few useful helper functions for working with constraints. 

#### jaas.config

Start your application (JAR or REPL session) with the additional JVM opt `-Djava.security.auth.login.config=/some/path/to/jaas.config`

For example configurations refer to [this tutorial](https://wiki.eclipse.org/Jetty/Tutorial/JAAS#Configuring_a_JAASLoginService)

#### Hash realm authentication

The simplest JAAS authentication module. A static list of hashed users in a file. 

Example `jaas.config`: ('my-app' must be the same as the configured :realm)

``` 
my-app {
           org.eclipse.jetty.jaas.spi.PropertyFileLoginModule required
           debug="true"
           file="dev-resources/jaas/hash-realm.properties";
       };
```

Example `hash-realm.properties`:

```
# This file defines users passwords and roles for a HashUserRealm
#
# The format is
#  <username>: <password>[,<rolename> ...]
#
# Passwords may be clear text, obfuscated or checksummed.  The class
# org.eclipse.jetty.util.security.Password should be used to generate obfuscated
# passwords or password checksums
#
# If DIGEST Authentication is used, the password must be in a recoverable
# format, either plain text or OBF:.
#
jetty: MD5:164c88b302622e17050af52c89945d44,kafka-users,content-administrators
admin: CRYPT:adpexzg3FUZAk,server-administrators,content-administrators,kafka-admins
other: OBF:1xmk1w261u9r1w1c1xmq,kafka-admins,kafka-users
plain: plain,content-administrators
user: password,kafka-users
# This entry is for digest auth.  The credential is a MD5 hash of username:realmname:password
digest: MD5:6e120743ad67abfbc385bc2bb754e297,kafka-users
```

#### LDAP authentication

Example `jaas.config`:

``` 
ldaploginmodule {
   org.eclipse.jetty.plus.jaas.spi.LdapLoginModule required
   debug="true"
   contextFactory="com.sun.jndi.ldap.LdapCtxFactory"
   hostname="ldap.example.com"
   port="389"
   bindDn="cn=Directory Manager"
   bindPassword="directory"
   authenticationMethod="simple"
   forceBindingLogin="false"
   userBaseDn="ou=people,dc=alcatel"
   userRdnAttribute="uid"
   userIdAttribute="uid"
   userPasswordAttribute="userPassword"
   userObjectClass="inetOrgPerson"
   roleBaseDn="ou=groups,dc=example,dc=com"
   roleNameAttribute="cn"
   roleMemberAttribute="uniqueMember"
   roleObjectClass="groupOfUniqueNames";
   };
```

## Examples

Check back soon! 

Slipway is the first step towards us releasing [shortcut](https://github.com/operatr-io/shortcut): an opinionated template for enterprise Clojure development.

## License

Distributed under the MIT License.

Copyright (c) 2022 Factor House
