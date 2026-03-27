(ns slipway.websockets-http-test
  (:require [clojure.test :refer [deftest is testing]]
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

  (deftest full-connection-no-auth

    (try
      (example/start! [:http])

      (let [{:keys [csrf-token cookies]} (client/do-get-csrf "http" "localhost" 3000)
            client-id  (str (random-uuid))
            sec-ws-key (let [bytes (byte-array 16)]
                         (.nextBytes (SecureRandom.) bytes)
                         (String. (.encode (Base64/getEncoder) bytes)))]

        (is (not (nil? csrf-token)))
        (is (seq cookies))

        (comment

          ;; print curl command you can use to validate externally to this test at this point
          ;; if running this as an individual test to use with this curl, alter the finally to not shut the server

          (print-ws-upgrade-curl {:scheme     "http"
                                  :host       "localhost"
                                  :port       3000
                                  :client-id  client-id
                                  :csrf-token csrf-token
                                  :cookies    cookies
                                  :sec-ws-key sec-ws-key}))

        (comment

          ; full websocket upgrade (test hangs in the handshake/upgrade process as we switch from http to wss)
          (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                         (client/do-get {:cookies cookies
                                         :headers {"Connection"            "Upgrade"
                                                   "Upgrade"               "Websocket"
                                                   "Origin"                "http://localhost:3000"
                                                   "Sec-WebSocket-Version" "13"
                                                   "Sec-WebSocket-Key"     sec-ws-key}})))))

        (comment
          ; full websocket upgrade with lower case headers, jetty capitalizes headers prior to negotiation
          ; hangs on http->wss protocol switch as above
          (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                         (client/do-get {:cookies cookies
                                         :headers {"connection"            "upgrade"
                                                   "upgrade"               "websocket"
                                                   "origin"                "http://localhost:3000"
                                                   "sec-websocket-version" "13"
                                                   "sec-websocket-key"     sec-ws-key}}))))))

      (finally (example/stop!)))))

(comment

  ;; Uncomment above and below as necessary to test full websocket negotiation with form auth

  (deftest full-connection-form-auth

    (try
      (example/start! [:http] :hash-auth)

      (let [{:keys [csrf-token cookies]} (client/do-login "http" "localhost" 3000 "/" "admin" "admin")
            client-id  (str (random-uuid))
            sec-ws-key (let [bytes (byte-array 16)]
                         (.nextBytes (SecureRandom.) bytes)
                         (String. (.encode (Base64/getEncoder) bytes)))]

        (is (not (nil? csrf-token)))
        (is (seq cookies))

        ;; uncomment these tests to part-verify right up to protocol switching (tests hang)
        (comment

          ; full websocket upgrade (test hangs in the handshake/upgrade process as we switch from http to wss)
          (is (client/do-get (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                             {:cookies cookies
                              :headers {"Connection"            "Upgrade"
                                        "Upgrade"               "Websocket"
                                        "Origin"                "http://localhost:3000"
                                        "Sec-WebSocket-Version" "13"
                                        "Sec-WebSocket-Key"     sec-ws-key}})))

        (comment
          ; full websocket upgrade with lower case headers, jetty capitalizes headers prior to negotiation
          ; hangs on http->wss protocol switch as above
          (is (client/do-get (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                             {:cookies cookies
                              :headers {"connection"            "upgrade"
                                        "upgrade"               "websocket"
                                        "origin"                "http://localhost:3000"
                                        "sec-websocket-version" "13"
                                        "sec-websocket-key"     sec-ws-key}}))))

      (finally (example/stop!)))))

(comment

  (deftest full-connection-basic-auth

    (try
      (example/start! [:http] :basic-auth)

      (let [{:keys [csrf-token cookies]} (client/do-get-csrf "http" "admin:admin@localhost" 3000)
            client-id  (str (random-uuid))
            sec-ws-key (let [bytes (byte-array 16)]
                         (.nextBytes (SecureRandom.) bytes)
                         (String. (.encode (Base64/getEncoder) bytes)))]

        (is (not (nil? csrf-token)))
        (is (seq cookies))

        (comment

          ; full websocket upgrade (test hangs in the handshake/upgrade process as we switch from http to wss)
          (is (client/do-get
               (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
               {:cookies cookies
                :headers {"Connection"            "Upgrade"
                          "Upgrade"               "Websocket"
                          "Origin"                "http://localhost:3000"
                          "Sec-WebSocket-Version" "13"
                          "Sec-WebSocket-Key"     sec-ws-key}})))

        (comment
          ; full websocket upgrade with lower case headers, jetty capitalizes headers prior to negotiation
          ; hangs on http->wss protocol switch as above
          (is (client/do-get
               (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
               {:cookies cookies
                :headers {"connection"            "upgrade"
                          "upgrade"               "websocket"
                          "origin"                "http://localhost:3000"
                          "sec-websocket-version" "13"
                          "sec-websocket-key"     sec-ws-key}}))))

      (finally (example/stop!)))))

(deftest ws-connection-upgrade-with-no-auth

  (try
    (example/start! [:http :websockets])

    (let [{:keys [csrf-token cookies]} (client/do-get-csrf "http" "localhost" 3000)
          client-id  (str (random-uuid))
          sec-ws-key (let [bytes (byte-array 16)]
                       (.nextBytes (SecureRandom.) bytes)
                       (String. (.encode (Base64/getEncoder) bytes)))]

      (is (not (nil? csrf-token)))
      (is (seq cookies))

      ;; wrong sec-websocket-version header value
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"connection"            "upgrade"
                                               "upgrade"               "websocket"
                                               "origin"                "http://localhost:3000"
                                               "sec-websocket-version" "12"
                                               "sec-websocket-key"     sec-ws-key}})
                     :status)))

      ;; missing sec-websocket-version header
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"        "Upgrade"
                                               "Upgrade"           "Websocket"
                                               "Origin"            "http://localhost:3000"
                                               "Sec-WebSocket-Key" sec-ws-key}})
                     :status)))

      ;; missing Sec-WebSocket-Key header
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"            "Upgrade"
                                               "Upgrade"               "Websocket"
                                               "Origin"                "http://localhost:3000"
                                               "Sec-WebSocket-Version" "13"}})
                     :status)))

      ;; Missing both Sec-WebSocket-Version and Sec-WebSocket-Key headers
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"
                                               "Origin"     "http://localhost:3000"}})
                     :status)))

      ;; missing upgrade header
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"            "Upgrade"
                                               "Origin"                "http://localhost:3000"
                                               "Sec-WebSocket-Version" "13"
                                               "Sec-WebSocket-Key"     sec-ws-key}})
                     :status)))

      ;; missing connection header
      ;; note: Jetty intentionally throws an exception in this case (Upgrade header without Connection)
      ;;       and thats the error you can see in the test logs
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Upgrade"               "Websocket"
                                               "Origin"                "http://localhost:3000"
                                               "Sec-WebSocket-Version" "13"
                                               "Sec-WebSocket-Key"     sec-ws-key}})
                     :status)))

      ;; missing csrf-token
      (is (= 403 (-> (format "http://localhost:3000/chsk?client-id=%s&" client-id)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"            "Upgrade"
                                               "Upgrade"               "Websocket"
                                               "Origin"                "http://localhost:3000"
                                               "Sec-WebSocket-Version" "13"
                                               "Sec-WebSocket-Key"     sec-ws-key}})
                     :status)))

      ; Missing origin header
      (is (= 403 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"            "Upgrade"
                                               "Upgrade"               "Websocket"
                                               "Sec-WebSocket-Version" "13"
                                               "Sec-WebSocket-Key"     sec-ws-key}})
                     :status)))

      ;; Attacker origin header
      (is (= 403 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"            "Upgrade"
                                               "Upgrade"               "Websocket"
                                               "Origin"                "http://attacker.site"
                                               "sec-websocket-version" "13"
                                               "sec-websocket-key"     sec-ws-key}})
                     :status)))

      ;; wrong scheme origin header
      (is (= 403 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"            "Upgrade"
                                               "Upgrade"               "Websocket"
                                               "Origin"                "https://localhost:3000"
                                               "sec-websocket-version" "13"
                                               "sec-websocket-key"     sec-ws-key}})
                     :status)))

      ;; wrong port origin header
      (is (= 403 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"            "Upgrade"
                                               "Upgrade"               "Websocket"
                                               "Origin"                "https://localhost:2999"
                                               "sec-websocket-version" "13"
                                               "sec-websocket-key"     sec-ws-key}})
                     :status))))

    (finally (example/stop!))))

(deftest ws-connection-upgrade-with-form-auth

  (try
    (example/start! [:http :websockets] :hash-auth)

    (let [{:keys [csrf-token cookies]} (client/do-login "http" "localhost" 3000 "/" "admin" "admin")
          client-id  (str (random-uuid))
          sec-ws-key (let [bytes (byte-array 16)]
                       (.nextBytes (SecureRandom.) bytes)
                       (String. (.encode (Base64/getEncoder) bytes)))]

      (is (not (nil? csrf-token)))
      (is (seq cookies))

      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"connection"            "upgrade"
                                               "upgrade"               "websocket"
                                               "origin"                "http://localhost:3000"
                                               "sec-websocket-version" "12"
                                               "sec-websocket-key"     sec-ws-key}})
                     :status)))

      ; missing Sec-WebSocket-Version header
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"        "Upgrade"
                                               "Upgrade"           "Websocket"
                                               "Origin"            "http://localhost:3000"
                                               "Sec-WebSocket-Key" sec-ws-key}})
                     :status)))

      ;; missing Sec-WebSocket-Key header
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"        "Upgrade"
                                               "Upgrade"           "Websocket"
                                               "Origin"            "http://localhost:3000"
                                               "Sec-WebSocket-Key" sec-ws-key}})
                     :status)))

      ;; Missing both Sec-WebSocket-Version and Sec-WebSocket-Key headers
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"
                                               "Origin"     "http://localhost:3000"}})
                     :status)))

      ;; missing upgrade header
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Origin"     "http://localhost:3000"}})
                     :status)))

      ; missing connection header
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Upgrade" "websocket"
                                               "Origin"  "http://localhost:3000"}})
                     :status)))

      ;; missing csrf-token
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s" client-id)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"
                                               "Origin"     "http://localhost:3000"}})
                     :status)))

      ;; Missing origin header
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"}})
                     :status)))

      ;; Attacker origin header
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"
                                               "Origin"     "http://attacker.site"}})
                     :status)))
      ;; wrong scheme origin header
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"
                                               "Origin"     "https://localhost:3000"}})
                     :status)))

      ;; wrong port origin header
      (is (= 400 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"
                                               "Origin"     "https://localhost:2999"}})
                     :status)))

      (testing "require login to negotiate websocket upgrade"

        (is (= 302 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" (str (random-uuid)) csrf-token)
                       (client/do-get {:cookies {}
                                       :headers {"Connection"            "Upgrade"
                                                 "Upgrade"               "Websocket"
                                                 "Origin"                "http://localhost:3000"
                                                 "Sec-WebSocket-Version" "13"
                                                 "Sec-WebSocket-Key"     (let [bytes (byte-array 16)]
                                                                           (.nextBytes (SecureRandom.) bytes)
                                                                           (String. (.encode
                                                                                     (Base64/getEncoder)
                                                                                     bytes)))}})
                       :status)))))

    (finally (example/stop!))))

(deftest ws-connection-upgrade-with-basic-auth

  (try
    (example/start! [:http :websockets] :basic-auth)

    (let [{:keys [csrf-token cookies]} (client/do-get-csrf "http" "admin:admin@localhost" 3000)
          client-id  (str (random-uuid))
          sec-ws-key (let [bytes (byte-array 16)]
                       (.nextBytes (SecureRandom.) bytes)
                       (String. (.encode (Base64/getEncoder) bytes)))]

      (is (not (nil? csrf-token)))
      (is (seq cookies))

      ; wrong sec-websocket-version header value
      (is (= 400 (-> (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"connection"            "upgrade"
                                               "upgrade"               "websocket"
                                               "origin"                "http://localhost:3000"
                                               "sec-websocket-version" "12"
                                               "sec-websocket-key"     sec-ws-key}})
                     :status)))

      ; missing Sec-WebSocket-Version header
      (is (= 400 (-> (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"        "Upgrade"
                                               "Upgrade"           "Websocket"
                                               "Origin"            "http://localhost:3000"
                                               "Sec-WebSocket-Key" sec-ws-key}})
                     :status)))

      ;; missing Sec-WebSocket-Key header
      (is (= 400 (-> (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection"        "Upgrade"
                                               "Upgrade"           "Websocket"
                                               "Origin"            "http://localhost:3000"
                                               "Sec-WebSocket-Key" sec-ws-key}})
                     :status)))

      ;; Missing both Sec-WebSocket-Version and Sec-WebSocket-Key headers
      (is (= 400 (-> (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"
                                               "Origin"     "http://localhost:3000"}})
                     :status)))

      ;; missing upgrade header
      (is (= 400 (-> (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Origin"     "http://localhost:3000"}})
                     :status)))

      ; missing connection header
      (is (= 400 (-> (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Upgrade" "websocket"
                                               "Origin"  "http://localhost:3000"}})
                     :status)))

      ;; missing csrf-token
      (is (= 400 (-> (format "http://admin:admin@localhost:3000/chsk?client-id=%s" client-id)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"
                                               "Origin"     "http://localhost:3000"}})
                     :status)))

      ;; Missing origin header
      (is (= 400 (-> (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"}})
                     :status)))

      ;; Attacker origin header
      (is (= 400 (-> (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"
                                               "Origin"     "http://attacker.site"}})
                     :status)))
      ;; wrong scheme origin header
      (is (= 400 (-> (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"
                                               "Origin"     "https://localhost:3000"}})
                     :status)))

      ;; wrong port origin header
      (is (= 400 (-> (format "http://admin:admin@localhost:3000/chsk?client-id=%s&csrf-token=%s" client-id csrf-token)
                     (client/do-get {:cookies cookies
                                     :headers {"Connection" "Upgrade"
                                               "Upgrade"    "Websocket"
                                               "Origin"     "https://localhost:2999"}})
                     :status))))

    (testing "require login to negotiate websocket upgrade"

      (is (= 401 (-> (format "http://localhost:3000/chsk?client-id=%s&csrf-token=%s" (str (random-uuid)) "WRONGCSRFTK")
                     (client/do-get {:cookies {}
                                     :headers {"Connection"            "Upgrade"
                                               "Upgrade"               "Websocket"
                                               "Origin"                "http://localhost:3000"
                                               "Sec-WebSocket-Version" "13"
                                               "Sec-WebSocket-Key"     (let [bytes (byte-array 16)]
                                                                         (.nextBytes (SecureRandom.) bytes)
                                                                         (String. (.encode
                                                                                   (Base64/getEncoder)
                                                                                   bytes)))}})
                     :status))))

    (finally (example/stop!))))
