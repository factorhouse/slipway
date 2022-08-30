(ns slipway.client
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.tools.logging :as log]))

(defn do-get
  ([scheme host port uri]
   (do-get scheme host port uri nil))
  ([scheme host port uri opts]
   (let [url (format "%s://%s:%s%s" scheme host port uri)]
     (log/infof "GET %s" url)
     (client/request (merge {:url               (format "%s://%s:%s%s" scheme host port uri)
                             :method            "GET"
                             :redirect-strategy :none
                             :throw-exceptions  false}
                            opts)))))

(defn decode-csrf-token
  [body]
  (when-let [start (some-> (str/index-of body "data-csrf-token=") (+ 17))]
    (subs body start (dec (str/index-of body " id=\"sente-csrf-token")))))

(defn do-login-post
  [scheme host port user pass cookies]
  (client/request
   {:url               (format "%s://%s:%s/j_security_check" scheme host port)
    :method            "POST"
    :body              (format "j_username=%s&j_password=%s" user pass)
    :cookies           cookies
    :headers           {"Referer"      (format "%s://%s:%s/login" scheme host port)
                        "Content-Type" "application/x-www-form-urlencoded"}
    :redirect-strategy :none}))

(defn do-login-redirect
  [location cookies]
  (client/request {:url               location
                   :method            "GET"
                   :cookies           cookies
                   :redirect-strategy :none}))

(defn do-login
  [scheme host port uri user pass]
  (let [anonymous        (do-get scheme host port uri)
        jetty-authed     (do-login-post scheme host port user pass (:cookies anonymous))
        ring-initialized (do-login-redirect (get-in jetty-authed [:headers "Location"]) (:cookies jetty-authed))
        csrf-token       (decode-csrf-token (:body ring-initialized))]
    (log/infof "logged in with jetty: %s, ring: %s, csrf-token: %s"
               (get-in jetty-authed [:cookies "JSESSIONID" :value])
               (get-in ring-initialized [:cookies "ring-session" :value])
               csrf-token)
    {:anon  (dissoc anonymous :http-client)
     :jetty (dissoc jetty-authed :http-client)
     :ring  (dissoc ring-initialized :http-client)}))