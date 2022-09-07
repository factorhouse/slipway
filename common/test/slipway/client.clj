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
    (log/infof "POST %s with %s" url opts)
    (client/request
     (merge
      {:url               url
       :method            "POST"
       :body              (format "j_username=%s&j_password=%s" user pass)
       :headers           {"Referer"      (format "%s://%s:%s/login" scheme host port)
                           "Content-Type" "application/x-www-form-urlencoded"}
       :redirect-strategy :none
       :throw-exceptions  false}
      opts))))

(defn do-login
  ([scheme host port uri user pass]
   (do-login scheme host port uri user pass nil))
  ([scheme host port uri user pass opts]
   (let [anonymous        (do-get scheme host port uri opts)
         session-cookies  (select-keys anonymous [:cookies])
         _                (do-get (get-in anonymous [:headers "Location"]) (merge opts session-cookies))
         jetty-authed     (do-login-post scheme host port user pass (merge opts session-cookies))
         session-cookies  (merge session-cookies (select-keys jetty-authed [:cookies]))
         ring-initialized (do-get (get-in jetty-authed [:headers "Location"]) (merge opts session-cookies))
         session-cookies  (merge-with merge session-cookies (select-keys ring-initialized [:cookies]))
         csrf-token       (decode-csrf-token (:body ring-initialized))]
     (log/infof "logged in with csrf-token: %s, session: %s" csrf-token session-cookies)
     (merge {:anon       (dissoc anonymous :http-client)
             :jetty      (dissoc jetty-authed :http-client)
             :ring       (dissoc ring-initialized :http-client)
             :csrf-token (when csrf-token (URLEncoder/encode ^String csrf-token "UTF-8"))}
            session-cookies))))

(defn do-get-csrf
  ([scheme host port]
   (do-get-csrf scheme host port nil))
  ([scheme host port opts]
   (let [anonymous        (do-get scheme host port "/" opts)
         session-cookies  (select-keys anonymous [:cookies])
         ring-initialized (do-get scheme host port "/" (merge opts session-cookies))
         session-cookies  (merge-with merge session-cookies (select-keys ring-initialized [:cookies]))
         csrf-token       (decode-csrf-token (:body ring-initialized))]
     (log/infof "retrieved no-auth csrf-token: %s, session: %s" csrf-token session-cookies)
     (merge {:anon       (dissoc anonymous :http-client)
             :ring       (dissoc ring-initialized :http-client)
             :csrf-token (when csrf-token (URLEncoder/encode ^String csrf-token "UTF-8"))}
            session-cookies))))

(defn do-get-login-redirect
  ([scheme host port uri user pass]
   (do-get-login-redirect scheme host port uri user pass nil))
  ([scheme host port uri user pass opts]
   (let [anonymous       (do-get scheme host port uri opts)
         session-cookies (select-keys anonymous [:cookies])
         _               (do-get (get-in anonymous [:headers "Location"]) (merge opts session-cookies))
         jetty-authed    (do-login-post scheme host port user pass (merge opts session-cookies))
         redirect        (get-in jetty-authed [:headers "Location"])]
     (log/infof "login redirect: %s" redirect)
     redirect)))