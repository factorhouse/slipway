(ns slipway.client
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (java.net URLEncoder)))

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

(defn do-login
  [scheme host port user pass]
  (let [anonymous        (:cookies (client/request {:url               (format "%s://%s:%s/" scheme host port)
                                                    :method            "GET"
                                                    :redirect-strategy :none}))
        jetty-authed     (:cookies (client/request
                                    {:url               (format "%s://%s:%s/j_security_check" scheme host port)
                                     :method            "POST"
                                     :body              (format "j_username=%s&j_password=%s" user pass)
                                     :cookies           anonymous
                                     :headers           {"Referer"      (format "%s://%s:%s/login" scheme host port)
                                                         "Content-Type" "application/x-www-form-urlencoded"}
                                     :redirect-strategy :none}))
        ring-initialized (client/request {:url               (format "%s://%s:%s/" scheme host port)
                                          :method            "GET"
                                          :cookies           jetty-authed
                                          :redirect-strategy :none})
        csrf-token       (decode-csrf-token (:body ring-initialized))]
    (log/infof "logged in with jetty: %s, ring: %s, csrf-token: %s"
               (get-in ring-initialized [:cookies "JSESSIONID" :value])
               (get-in ring-initialized [:cookies "ring-session" :value])
               csrf-token)
    {:cookies    (:cookies ring-initialized)
     :body       (:body ring-initialized)
     :csrf-token (when csrf-token (URLEncoder/encode ^String csrf-token "UTF-8"))}))