(ns slipway.test-server
  "This ns contains helper functions for stopping/starting test servers.
   Feel free to add any further configuration in the same style."
  (:require [slipway :as slipway]
            [slipway.sente]))

(def state (atom nil))

(defn stop!
  []
  (when-let [server @state]
    (slipway/stop server)))

(defn start!
  "To run a JAAS authenticated server, start a REPL with the following JVM JAAS parameter:
   - Hash User Auth  ->  -Djava.security.auth.login.config=/dev-resources/jaas/hash-jaas.conf
   - LDAP Auth       ->  -Djava.security.auth.login.config=/dev-resources/jaas/ldap-jaas.conf"
  [config]
  (stop!)
  (reset! state (slipway/start config)))