(ns slipway.client
  (:require [clj-http.client :as client]
            [clojure.string :as str]
            [clojure.tools.logging :as log])
  (:import (java.net URLEncoder)))

(defn do-get
  ([location opts]
   (log/infof "GET %s with %s" location (or opts {}))
   (client/request (merge {:url               location
                           :method            "GET"
                           :redirect-strategy :none
                           :throw-exceptions  false}
                          opts)))
  ([scheme host port uri]
   (do-get scheme host port uri nil))
  ([scheme host port uri opts]
   (do-get (format "%s://%s:%s%s" scheme host port uri) opts)))

(defn decode-csrf-token
  [body]
  (when-let [start (some-> (str/index-of body "data-csrf-token=") (+ 17))]
    (subs body start (dec (str/index-of body " id=\"sente-csrf-token")))))

(defn do-login-post
  [scheme host port user pass opts]
  (let [url (format "%s://%s:%s/j_security_check" scheme host port)]
    (log/infof "POST %s" url)
    (client/request
     (merge
      {:url               url
       :method            "POST"
       :body              (format "j_username=%s&j_password=%s" user pass)
       :headers           {"Referer"      (format "%s://%s:%s/login" scheme host port)
                           "Content-Type" "application/x-www-form-urlencoded"}
       :redirect-strategy :none}
      opts))))

(defn do-login
  ([scheme host port uri user pass]
   (do-login scheme host port uri user pass nil))
  ([scheme host port uri user pass opts]
   (let [anonymous        (do-get scheme host port uri opts)
         jetty-authed     (do-login-post scheme host port user pass (merge opts (select-keys anonymous [:cookies])))
         ring-initialized (do-get (get-in jetty-authed [:headers "Location"]) (merge opts
                                                                                     (select-keys anonymous [:cookies])
                                                                                     (select-keys jetty-authed [:cookies])))
         csrf-token       (decode-csrf-token (:body ring-initialized))]
     (log/infof "logged in with jetty: %s, ring: %s, csrf-token: %s"
                (get-in jetty-authed [:cookies "JSESSIONID" :value])
                (get-in ring-initialized [:cookies "ring-session" :value])
                csrf-token)
     {:anon       (dissoc anonymous :http-client)
      :jetty      (dissoc jetty-authed :http-client)
      :ring       (dissoc ring-initialized :http-client)
      :csrf-token (when csrf-token (URLEncoder/encode ^String csrf-token "UTF-8"))
      :cookies    (merge (:cookies anonymous)
                         (:cookies jetty-authed)
                         (:cookies ring-initialized))})))