(ns slipway
  (:require [clojure.tools.logging :as log]
            [slipway.authz :as authz]
            [slipway.server :as server])
  (:import (org.eclipse.jetty.server Handler Server)))

;;; server
;:port - the port to listen on (defaults to 3000)
;:host - the hostname to listen on
;:http? - allow connections over HTTP
;: ssl? - allow connections over HTTPS
;:http-forwarded? - support for X-Forwarded-For header (defaults to false)
;:gzip? - enable Gzip compression on the server (defaults to true)
;:gzip-content-types - contents types to apply Gzip compression to (defaults to ["text/css" "text/plain" "text/javascript" "application/javascript" "image/svg+xml"])
;:gzip-min-size - the minimum size (in bytes) to apply Gzip compression to the response body. Default 1024
;:error-handler - sets an error handlers on the server for catch-all Jetty errors (something that extends the `org.eclipse.jetty.server.handler.ErrorHandler` class)
;:daemon? - use daemon threads (defaults to false)
;:send-server-version? - whether to send the Server header in responses (default false)
;:send-date-header? - whether to send Date header in responses (default false)
;:output-buffer-size - set the size of the buffer into which response content is aggregated before being sent to the client. A larger buffer can improve performance by allowing a content producer to run without blocking, however larger buffers consume more memory and may induce some latency before a client starts processing the content. (default 32768)
;:request-header-size - sets the maximum allowed size in bytes for the HTTP request line and HTTP request headers (default 8192)
;:response-header-size -  the maximum size in bytes of the response header (default 8192)
;:header-cache-size - the size of the header field cache, in terms of unique characters (default 512)
;:thread-pool - the thread pool for Jetty workload
;:max-threads - the maximum number of threads to use (default 50), ignored if `:thread-pool` provided
;:min-threads - the minimum number of threads to use (default 8), ignored if `:thread-pool` provided
;:threadpool-idle-timeout - the maximum idle time in milliseconds for a thread (default 60000), ignored if `:thread-pool` provided
;:job-queue - the job queue to be used by the Jetty threadpool (default is unbounded), ignored if `:thread-pool` provided
;:max-idle-time  - the maximum idle time in milliseconds for a connection (default 200000)
;:client-auth - SSL client certificate authenticate, may be set to :need, :want or :none (defaults to :none)
;:proxy? - enable the proxy protocol on plain socket port (see http://www.eclipse.org/jetty/documentation/9.4.x/configuring-connectors.html#_proxy_protocol)
;:sni-required? - require sni for secure connection, default to false
;:sni-host-check? - enable host check for secure connection, default to true

;:join? - blocks the thread until server ends (defaults to false)

;;; ssl

;:ssl-port - the SSL port to listen on (defaults to 443, implies :ssl?)
;:ssl-context - an optional SSLContext to use for SSL connections
;:ssl-protocols - the ssl protocols to use, default to ["TLSv1.3" "TLSv1.2"]
;:ssl-provider - the ssl provider
;:exclude-ciphers      - when :ssl? is true, additionally exclude these cipher suites
;:exclude-protocols    - when :ssl? is true, additionally exclude these protocols
;:keystore - the keystore to use for SSL connections
;:keystore-type - the format of keystore
;:key-password - the password to the keystore
;:key-manager-password - the password for key manager
;:truststore - a truststore to use for SSL connections
;:truststore-type - the format of trust store
;:trust-password - the password to the truststore
;:replace-exclude-ciphers?   - when true, :exclude-ciphers will replace rather than add to the cipher exclusion list (defaults to false)
;:replace-exclude-protocols? - when true, :exclude-protocols will replace rather than add to the protocols exclusion list (defaults to false)

;;; websockets
;:ws-max-idle-time  - the maximum idle time in milliseconds for a websocket connection (default 500000)
;:ws-max-text-message-size  - the maximum text message size in bytes for a websocket connection (default 65536)

;;; auth / session
;:auth - Map of auth opts. Configures Jetty JAAS auth, see JAAS Integration section of README
;;session

;;; handler
; context-path null-path-info? ws-path

(comment
 #:slipway.websockets {:idle-timeout          "max websocket idle time"
                       :max-text-message-size "max websocket text message size"}

 #:slipway.handler {:context-path    "the root context path, default '/'"
                    :ws-path         "the path serving the websocket upgrade handler, default '/chsk'"
                    :null-path-info? "true if /path is not redirected to /path/, default true"}

 #:slipway{:join? "join the Jetty threadpool, blocks the calling thread until jetty exits, default false"})

(defn start ^Server
  [ring-handler {::keys [join?] :as opts}]
  (let [server        (server/create-server opts)
        login-service (authz/login-service opts)]
    (.setHandler server ^Handler (server/handler ring-handler login-service opts))
    (some->> login-service (.addBean server))
    (.start server)
    (when join?
      (log/info "joining jetty thread")
      (.join server))
    server))

(defn stop
  [^Server server]
  (.stop server))

