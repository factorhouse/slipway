(ns slipway.websockets-test
  (:require [clj-http.conn-mgr :as conn]
            [clojure.test :refer :all]
            [clojure.tools.logging :as log]
            [slipway.client :as client]
            [slipway.example :as example])
  (:import (java.security SecureRandom)
           (java.util Base64)))

(defn print-ws-upgrade-curl
  [{:keys [scheme host port client-id csrf-token cookies sec-ws-key]}]
  (log/info (format (str "\n\n-----WS Upgrade Curl-----\n\n"
                         "curl -v \\\n"
                         " --cookie \"JSESSIONID=%s; ring-session=%s\" \\\n"
                         " -H \"Connection: Upgrade\" \\\n"
                         " -H \"Upgrade: Websocket\" \\\n"
                         " -H \"Origin: %s://%s:%s\" \\\n"
                         " -H \"Sec-WebSocket-Version: 13\" \\\n"
                         " -H \"Sec-WebSocket-Key: %s\" \\\n"
                         " \"%s://%s:%s/chsk?client-id=%s&csrf-token=%s\""
                         "\n\n------------------------\n")
                    (get-in cookies ["JSESSIONID" :value])
                    (get-in cookies ["ring-session" :value])
                    scheme
                    host
                    port
                    sec-ws-key
                    scheme
                    host
                    port
                    client-id
                    csrf-token)))

(deftest ws-connection-upgrade-not-authorized

  (try
    (example/hash-server)

    ;; no-auth requests immediately bounced to a 303/see-login
    (is (= 303 (->> {:cookies            {}
                     :connection-manager (conn/make-reusable-conn-manager {})
                     :headers            {"Connection"            "Upgrade"
                                          "Upgrade"               "Websocket"
                                          "Origin"                "http://localhost:3000"
                                          "Sec-WebSocket-Version" "13"
                                          "Sec-WebSocket-Key"     (let [bytes (byte-array 16)]
                                                                    (.nextBytes (SecureRandom.) bytes)
                                                                    (String. (.encode (Base64/getEncoder)
                                                                                      bytes)))}}
                    (client/do-get "http" "localhost" 3000 (format "/chsk?client-id=%s&csrf-token=%s"
                                                                   (str (random-uuid))
                                                                   "WRONGCSRFTOKEN"))
                    (:status))))

    (finally (example/stop-server!))))

(deftest ws-connection-upgrade

  (try
    (example/hash-server)

    (let [{:keys [csrf-token cookies]} (client/do-login "http" "localhost" 3000 "/" "admin" "admin")
          client-id  (str (random-uuid))
          sec-ws-key (let [bytes (byte-array 16)]
                       (.nextBytes (SecureRandom.) bytes)
                       (String. (.encode (Base64/getEncoder) bytes)))
          ;; without a reusable conn-mgr we auto set Connection: close and that interferes with Connection: Upgrade.
          ;; particularly when testing with Jetty 9 (it doesn't seem to impact Jetty 10 cos is-ws-upgrade is diff..)
          ;; works with Jetty 10 / Java 11 impl as our ws-upgrade takes a predicate where the Connection: Upgrade
          ;; header happens to have clobbered the earlier Connection: close one. Jetty 9 / Java 8 behavior the opposite.
          conn-mgr   (conn/make-reusable-conn-manager {})]

      (is (not (nil? csrf-token)))
      (is (seq cookies))

      ;; full websocket upgrade (test hangs in the handshake/upgrade process as we switch from http to wss)
      ;(is (= 400 (client/do-get "http" "localhost" 3000
      ;                          (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
      ;                          {:cookies            cookies
      ;                           :connection-manager conn-mgr
      ;                           :headers            {"Connection" "Upgrade"
      ;                                                "Upgrade"    "Websocket"
      ;                                                "Origin"     "http://localhost:3000"
      ;                                                "Sec-WebSocket-Version" "13"
      ;                                                "Sec-WebSocket-Key"     sec-ws-key}})))

      ;; full websocket upgrade with lower case headers
      ;; jetty capitalizes headers prior to WebSocketServerfactory(j9) / RFC6455Negotiation(j10/11)
      ;; hangs on http->wss protocol switch as above
      ;(is (= 400 (client/do-get "http" "localhost" 3000
      ;                          (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
      ;                          {:cookies            cookies
      ;                           :connection-manager conn-mgr
      ;                           :headers            {"connection"            "upgrade"
      ;                                                "upgrade"               "websocket"
      ;                                                "origin"                "http://localhost:3000"
      ;                                                "sec-websocket-version" "13"
      ;                                                "sec-websocket-key"     sec-ws-key}})))

      ;; TODO: verify nil body / length between both impls
      ; wrong sec-websocket-version header value
      (is (= 400 (:status (client/do-get "http" "localhost" 3000
                                         (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                                         {:cookies            cookies
                                          :connection-manager conn-mgr
                                          :headers            {"connection"            "upgrade"
                                                               "upgrade"               "websocket"
                                                               "origin"                "http://localhost:3000"
                                                               "sec-websocket-version" "12"
                                                               "sec-websocket-key"     sec-ws-key}}))))

      ;; missing Sec-WebSocket-Version header
      (is (= 400 (:status (client/do-get "http" "localhost" 3000
                                         (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                                         {:cookies            cookies
                                          :connection-manager conn-mgr
                                          :headers            {"Connection"        "Upgrade"
                                                               "Upgrade"           "Websocket"
                                                               "Origin"            "http://localhost:3000"
                                                               "Sec-WebSocket-Key" sec-ws-key}}))))

      ;; missing Sec-WebSocket-Key header
      (is (= 400 (:status (client/do-get "http" "localhost" 3000
                                         (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                                         {:cookies            cookies
                                          :connection-manager conn-mgr
                                          :headers            {"Connection"        "Upgrade"
                                                               "Upgrade"           "Websocket"
                                                               "Origin"            "http://localhost:3000"
                                                               "Sec-WebSocket-Key" sec-ws-key}}))))

      ;; Missing both Sec-WebSocket-Version and Sec-WebSocket-Key headers
      (is (= 400 (:status (client/do-get "http" "localhost" 3000
                                         (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                                         {:cookies            cookies
                                          :connection-manager conn-mgr
                                          :headers            {"Connection" "Upgrade"
                                                               "Upgrade"    "Websocket"
                                                               "Origin"     "http://localhost:3000"}}))))

      ;; missing upgrade header
      (is (= 400 (:status (client/do-get "http" "localhost" 3000
                                         (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                                         {:cookies            cookies
                                          :connection-manager conn-mgr
                                          :headers            {"Connection" "Upgrade"
                                                               "Origin"     "http://localhost:3000"}}))))

      ; missing connection header
      (is (= 400 (:status (client/do-get "http" "localhost" 3000
                                         (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                                         {:cookies            cookies
                                          :connection-manager conn-mgr
                                          :headers            {"Upgrade" "websocket"
                                                               "Origin"  "http://localhost:3000"}}))))

      ;; missing csrf-token
      (is (= 403 (:status (client/do-get "http" "localhost" 3000
                                         (format "/chsk?client-id=%s" client-id)
                                         {:cookies            cookies
                                          :connection-manager conn-mgr
                                          :headers            {"Connection" "Upgrade"
                                                               "Upgrade"    "Websocket"
                                                               "Origin"     "http://localhost:3000"}}))))

      ;; Missing origin header
      (is (= 403 (:status (client/do-get "http" "localhost" 3000
                                         (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                                         {:cookies            cookies
                                          :connection-manager conn-mgr
                                          :headers            {"Connection" "Upgrade"
                                                               "Upgrade"    "Websocket"}}))))

      ;; Attacker origin header
      (is (= 403 (:status (client/do-get "http" "localhost" 3000
                                         (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                                         {:cookies            cookies
                                          :connection-manager conn-mgr
                                          :headers            {"Connection" "Upgrade"
                                                               "Upgrade"    "Websocket"
                                                               "Origin"     "http://attacker.site"}}))))
      ;; wrong scheme origin header
      (is (= 403 (:status (client/do-get "http" "localhost" 3000
                                         (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                                         {:cookies            cookies
                                          :connection-manager conn-mgr
                                          :headers            {"Connection" "Upgrade"
                                                               "Upgrade"    "Websocket"
                                                               "Origin"     "https://localhost:3000"}}))))

      ;; wrong port origin header
      (is (= 403 (:status (client/do-get "http" "localhost" 3000
                                         (format "/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                                         {:cookies            cookies
                                          :connection-manager conn-mgr
                                          :headers            {"Connection" "Upgrade"
                                                               "Upgrade"    "Websocket"
                                                               "Origin"     "https://localhost:2999"}}))))

      ;; set true to print curl command you can use to validate externally to this test ns
      (when false
        (print-ws-upgrade-curl {:scheme     "http"
                                :host       "localhost"
                                :port       3000
                                :client-id  client-id
                                :csrf-token csrf-token
                                :cookies    cookies
                                :sec-ws-key sec-ws-key})))

    (finally (example/stop-server!))))
