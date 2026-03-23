(ns slipway.websockets-https-test
  (:require [clj-http.conn-mgr :as conn]
            [clojure.test :refer [deftest is testing]]
            [clojure.tools.logging :as log]
            [slipway.test-client :as client]
            [slipway.test-server :as example])
  (:import (java.security SecureRandom)
           (java.util Base64)))

;; use this output to run a server and validate via curl (see commented sexp below)
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

(comment

  ;; Uncomment above and below as necessary to test full websocket negotiation with no auth

  (deftest full-connection-with-no-auth

    (try
      (example/start! [:https])

      (let [{:keys [csrf-token cookies]} (client/do-get-csrf "https" "localhost" 3443 {:insecure? true})
            client-id  (str (random-uuid))
            sec-ws-key (let [bytes (byte-array 16)]
                         (.nextBytes (SecureRandom.) bytes)
                         (String. (.encode (Base64/getEncoder) bytes)))
            conn-mgr   (conn/make-reusable-conn-manager {:insecure? true})]

        (is (not (nil? csrf-token)))
        (is (seq cookies))

        (comment
          ;; print curl command you can use to validate externally to this test at this point
          ;; if running this as an individual test to use with this curl, alter the finally to not shut the server
          ;;
          ;; when running this test with https you will also need to:
          ;;  - add the '-k' switch to the curl command to enable permissive ssl (ignore the self signed certs)
          ;;  - adjust example.app/handler to have https for allowed origins rather than http
          (print-ws-upgrade-curl {:scheme     "https"
                                  :host       "localhost"
                                  :port       3443
                                  :client-id  client-id
                                  :csrf-token csrf-token
                                  :cookies    cookies
                                  :sec-ws-key sec-ws-key}))

        (comment

          ; full websocket upgrade (test hangs in the handshake/upgrade process as we switch from http to wss)
          (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                         (client/do-get {:cookies            cookies
                                         :connection-manager conn-mgr
                                         :headers            {"Connection"            "Upgrade"
                                                              "Upgrade"               "Websocket"
                                                              "Origin"                "http://localhost:3443"
                                                              "Sec-WebSocket-Version" "13"
                                                              "Sec-WebSocket-Key"     sec-ws-key}})))))

        (comment
          ; full websocket upgrade with lower case headers, jetty capitalizes headers prior to negotiation
          ; hangs on http->wss protocol switch as above
          (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                         (client/do-get {:cookies            cookies
                                         :connection-manager conn-mgr
                                         :headers            {"connection"            "upgrade"
                                                              "upgrade"               "websocket"
                                                              "origin"                "http://localhost:3443"
                                                              "sec-websocket-version" "13"
                                                              "sec-websocket-key"     sec-ws-key}}))))))

      (finally (example/stop!)))))

(comment

  ;; Uncomment above and below as necessary to test full websocket negotiation with form auth

  (deftest full-connection-with-form-auth

    (try
      (example/start! [:https] :hash-auth)

      (let [{:keys [csrf-token cookies]} (client/do-login "https" "localhost" 3443 "/" "admin" "admin" {:insecure? true})
            client-id  (str (random-uuid))
            sec-ws-key (let [bytes (byte-array 16)]
                         (.nextBytes (SecureRandom.) bytes)
                         (String. (.encode (Base64/getEncoder) bytes)))
            conn-mgr   (conn/make-reusable-conn-manager {:insecure? true})] ;; required for https certificates

        (is (not (nil? csrf-token)))
        (is (seq cookies))

        ;; uncomment these tests to part-verify right up to protocol switching (tests hang)
        (comment

          ; full websocket upgrade (test hangs in the handshake/upgrade process as we switch from http to wss)
          (is (client/do-get (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                             {:cookies            cookies
                              :connection-manager conn-mgr
                              :headers            {"Connection"            "Upgrade"
                                                   "Upgrade"               "Websocket"
                                                   "Origin"                "http://localhost:3443"
                                                   "Sec-WebSocket-Version" "13"
                                                   "Sec-WebSocket-Key"     sec-ws-key}})))

        (comment
          ; full websocket upgrade with lower case headers, jetty capitalizes headers prior to negotiation
          ; hangs on http->wss protocol switch as above
          (is (client/do-get (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                             {:cookies            cookies
                              :connection-manager conn-mgr
                              :headers            {"connection"            "upgrade"
                                                   "upgrade"               "websocket"
                                                   "origin"                "http://localhost:3443"
                                                   "sec-websocket-version" "13"
                                                   "sec-websocket-key"     sec-ws-key}}))))

      (finally (example/stop!)))))

(comment

  ;; Uncomment above and below as necessary to test full websocket negotiation with form auth

  (deftest full-connection-with-basic-auth

    (try
      (example/start! [:https] :basic-auth)

      (let [{:keys [csrf-token cookies]} (client/do-get-csrf "https" "admin:admin@localhost" 3443 {:insecure? true})
            client-id  (str (random-uuid))
            sec-ws-key (let [bytes (byte-array 16)]
                         (.nextBytes (SecureRandom.) bytes)
                         (String. (.encode (Base64/getEncoder) bytes)))
            conn-mgr   (conn/make-reusable-conn-manager {:insecure? true})] ;; required for https certificates

        (is (not (nil? csrf-token)))
        (is (seq cookies))

        ;; uncomment these tests to part-verify right up to protocol switching (tests hang)
        (comment

          ; full websocket upgrade (test hangs in the handshake/upgrade process as we switch from http to wss)
          (is (client/do-get
               (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
               {:cookies            cookies
                :connection-manager conn-mgr
                :headers            {"Connection"            "Upgrade"
                                     "Upgrade"               "Websocket"
                                     "Origin"                "http://localhost:3443"
                                     "Sec-WebSocket-Version" "13"
                                     "Sec-WebSocket-Key"     sec-ws-key}})))

        (comment
          ; full websocket upgrade with lower case headers, jetty capitalizes headers prior to negotiation
          ; hangs on http->wss protocol switch as above
          (is (client/do-get
               (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
               {:cookies            cookies
                :connection-manager conn-mgr
                :headers            {"connection"            "upgrade"
                                     "upgrade"               "websocket"
                                     "origin"                "http://localhost:3443"
                                     "sec-websocket-version" "13"
                                     "sec-websocket-key"     sec-ws-key}}))))

      (finally (example/stop!)))))

(deftest ws-connection-upgrade-with-no-auth

  (try
    (example/start! [:https])

    (let [{:keys [csrf-token cookies]} (client/do-get-csrf "https" "localhost" 3443 {:insecure? true})
          client-id  (str (random-uuid))
          sec-ws-key (let [bytes (byte-array 16)]
                       (.nextBytes (SecureRandom.) bytes)
                       (String. (.encode (Base64/getEncoder) bytes)))
          conn-mgr   (conn/make-reusable-conn-manager {:insecure? true})] ;; required for https certificates

      (is (not (nil? csrf-token)))
      (is (seq cookies))

      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"connection"            "upgrade"
                                                          "upgrade"               "websocket"
                                                          "origin"                "http://localhost:3443"
                                                          "sec-websocket-version" "12"
                                                          "sec-websocket-key"     sec-ws-key}})
                     :status)))

      ; missing Sec-WebSocket-Version header
      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection"        "Upgrade"
                                                          "Upgrade"           "Websocket"
                                                          "Origin"            "http://localhost:3443"
                                                          "Sec-WebSocket-Key" sec-ws-key}})
                     :status)))

      ;; missing Sec-WebSocket-Key header
      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection"        "Upgrade"
                                                          "Upgrade"           "Websocket"
                                                          "Origin"            "http://localhost:3443"
                                                          "Sec-WebSocket-Key" sec-ws-key}})
                     :status)))

      ;; Missing both Sec-WebSocket-Version and Sec-WebSocket-Key headers
      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "http://localhost:3443"}})
                     :status)))

      ;; missing upgrade header
      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Origin"     "http://localhost:3443"}})
                     :status)))

      ; missing connection header
      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Upgrade" "websocket"
                                                          "Origin"  "http://localhost:3443"}})
                     :status)))

      ;; missing csrf-token
      (is (= 403 (-> (format "https://localhost:3443/chsk?client-id=%s&" client-id)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "http://localhost:3443"}})
                     :status)))

      ;; Missing origin header
      (is (= 403 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"}})
                     :status)))

      ;; Attacker origin header
      (is (= 403 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "http://attacker.site"}})
                     :status)))
      ;; wrong scheme origin header
      (is (= 403 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "https://localhost:3443"}})
                     :status)))

      ;; wrong port origin header
      (is (= 403 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "https://localhost:2999"}})
                     :status))))

    (finally (example/stop!))))

(deftest ws-connection-upgrade-with-form-auth

  (try
    (example/start! [:https] :hash-auth)

    (let [{:keys [csrf-token cookies]} (client/do-login "https" "localhost" 3443 "/" "admin" "admin" {:insecure? true})
          client-id  (str (random-uuid))
          sec-ws-key (let [bytes (byte-array 16)]
                       (.nextBytes (SecureRandom.) bytes)
                       (String. (.encode (Base64/getEncoder) bytes)))
          conn-mgr   (conn/make-reusable-conn-manager {:insecure? true})] ;; required for https certificates

      (is (not (nil? csrf-token)))
      (is (seq cookies))

      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"connection"            "upgrade"
                                                          "upgrade"               "websocket"
                                                          "origin"                "http://localhost:3443"
                                                          "sec-websocket-version" "12"
                                                          "sec-websocket-key"     sec-ws-key}})
                     :status)))

      ; missing Sec-WebSocket-Version header
      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection"        "Upgrade"
                                                          "Upgrade"           "Websocket"
                                                          "Origin"            "http://localhost:3443"
                                                          "Sec-WebSocket-Key" sec-ws-key}})
                     :status)))

      ;; missing Sec-WebSocket-Key header
      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection"        "Upgrade"
                                                          "Upgrade"           "Websocket"
                                                          "Origin"            "http://localhost:3443"
                                                          "Sec-WebSocket-Key" sec-ws-key}})
                     :status)))

      ;; Missing both Sec-WebSocket-Version and Sec-WebSocket-Key headers
      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "http://localhost:3443"}})
                     :status)))

      ;; missing upgrade header
      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Origin"     "http://localhost:3443"}})
                     :status)))

      ; missing connection header
      (is (= 400 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Upgrade" "websocket"
                                                          "Origin"  "http://localhost:3443"}})
                     :status)))

      ;; missing csrf-token
      (is (= 403 (-> (format "https://localhost:3443/chsk?client-id=%s" client-id)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "http://localhost:3443"}})
                     :status)))

      ;; Missing origin header
      (is (= 403 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"}})
                     :status)))

      ;; Attacker origin header
      (is (= 403 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "http://attacker.site"}})
                     :status)))
      ;; wrong scheme origin header
      (is (= 403 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "https://localhost:3443"}})
                     :status)))

      ;; wrong port origin header
      (is (= 403 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "https://localhost:2999"}})
                     :status))))

    (testing "require login to negotiate websocket upgrade"

      (is (= 302 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" (str (random-uuid)) "WRONGCSRFTK")
                     (client/do-get {:insecure? true
                                     :cookies   {}
                                     :headers   {"Connection"            "Upgrade"
                                                 "Upgrade"               "Websocket"
                                                 "Origin"                "http://localhost:3443"
                                                 "Sec-WebSocket-Version" "13"
                                                 "Sec-WebSocket-Key"     (let [bytes (byte-array 16)]
                                                                           (.nextBytes (SecureRandom.) bytes)
                                                                           (String. (.encode
                                                                                     (Base64/getEncoder)
                                                                                     bytes)))}})
                     :status))))

    (finally (example/stop!))))

(deftest ws-connection-upgrade-with-basic-auth

  (try
    (example/start! [:https] :basic-auth)

    (let [{:keys [csrf-token cookies]} (client/do-get-csrf "https" "admin:admin@localhost" 3443 {:insecure? true})
          client-id  (str (random-uuid))
          sec-ws-key (let [bytes (byte-array 16)]
                       (.nextBytes (SecureRandom.) bytes)
                       (String. (.encode (Base64/getEncoder) bytes)))
          conn-mgr   (conn/make-reusable-conn-manager {:insecure? true})] ;; required for https certificates

      (is (not (nil? csrf-token)))
      (is (seq cookies))

      ;; uncomment these tests to part-verify right up to protocol switching (tests hang)
      (comment

        ; full websocket upgrade (test hangs in the handshake/upgrade process as we switch from http to wss)
        (is (client/do-get
             (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
             {:cookies            cookies
              :connection-manager conn-mgr
              :headers            {"Connection"            "Upgrade"
                                   "Upgrade"               "Websocket"
                                   "Origin"                "http://localhost:3443"
                                   "Sec-WebSocket-Version" "13"
                                   "Sec-WebSocket-Key"     sec-ws-key}}))

        ; full websocket upgrade with lower case headers
        ; jetty capitalizes headers prior to WebSocketServerfactory(j9) / RFC6455Negotiation(j10/11)
        ; hangs on http->wss protocol switch as above
        (is (client/do-get
             (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
             {:cookies            cookies
              :connection-manager conn-mgr
              :headers            {"connection"            "upgrade"
                                   "upgrade"               "websocket"
                                   "origin"                "http://localhost:3443"
                                   "sec-websocket-version" "13"
                                   "sec-websocket-key"     sec-ws-key}})))

      (is (= 400 (-> (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"connection"            "upgrade"
                                                          "upgrade"               "websocket"
                                                          "origin"                "http://localhost:3443"
                                                          "sec-websocket-version" "12"
                                                          "sec-websocket-key"     sec-ws-key}})
                     :status)))

      ; missing Sec-WebSocket-Version header
      (is (= 400 (-> (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection"        "Upgrade"
                                                          "Upgrade"           "Websocket"
                                                          "Origin"            "http://localhost:3443"
                                                          "Sec-WebSocket-Key" sec-ws-key}})
                     :status)))

      ;; missing Sec-WebSocket-Key header
      (is (= 400 (-> (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection"        "Upgrade"
                                                          "Upgrade"           "Websocket"
                                                          "Origin"            "http://localhost:3443"
                                                          "Sec-WebSocket-Key" sec-ws-key}})
                     :status)))

      ;; Missing both Sec-WebSocket-Version and Sec-WebSocket-Key headers
      (is (= 400 (-> (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "http://localhost:3443"}})
                     :status)))

      ;; missing upgrade header
      (is (= 400 (-> (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Origin"     "http://localhost:3443"}})
                     :status)))

      ; missing connection header
      (is (= 400 (-> (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Upgrade" "websocket"
                                                          "Origin"  "http://localhost:3443"}})
                     :status)))

      ;; missing csrf-token
      (is (= 403 (-> (format "https://admin:admin@localhost:3443/chsk?client-id=%s" client-id)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "http://localhost:3443"}})
                     :status)))

      ;; Missing origin header
      (is (= 403 (-> (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"}})
                     :status)))

      ;; Attacker origin header
      (is (= 403 (-> (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "http://attacker.site"}})
                     :status)))
      ;; wrong scheme origin header
      (is (= 403 (-> (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "https://localhost:3443"}})
                     :status)))

      ;; wrong port origin header
      (is (= 403 (-> (format "https://admin:admin@localhost:3443/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies            cookies
                                     :connection-manager conn-mgr
                                     :headers            {"Connection" "Upgrade"
                                                          "Upgrade"    "Websocket"
                                                          "Origin"     "https://localhost:2999"}})
                     :status))))

    (testing "require login to negotiate websocket upgrade"

      (is (= 401 (-> (format "https://localhost:3443/chsk?client-id=%s&csrf-token=%s" (str (random-uuid)) "WRONGCSRFTK")
                     (client/do-get {:insecure? true
                                     :cookies   {}
                                     :headers   {"Connection"            "Upgrade"
                                                 "Upgrade"               "Websocket"
                                                 "Origin"                "http://localhost:3443"
                                                 "Sec-WebSocket-Version" "13"
                                                 "Sec-WebSocket-Key"     (let [bytes (byte-array 16)]
                                                                           (.nextBytes (SecureRandom.) bytes)
                                                                           (String. (.encode
                                                                                     (Base64/getEncoder)
                                                                                     bytes)))}})
                     :status))))

    (finally (example/stop!))))
