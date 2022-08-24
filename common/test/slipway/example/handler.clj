(ns slipway.example.handler
  (:require [clojure.test :refer :all]
            [hiccup.core :as hiccup]
            [hiccup.page :as hiccup.page]))

(defn login-content
  [retry?]
  (hiccup/html
   (hiccup.page/html5
    {:lang "en"}
    [:head
     [:title "Login | Slipway Demo"]
     [:meta {:charset "UTF-8"}]
     [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
     [:meta {:name "description" :content "A Clojure companion for Jetty and Websockets by Factor House"}]
     [:link {:href "css/tailwind.min.css" :rel "stylesheet" :type "text/css"}]]
    [:body
     [:div.flex.items-center.justify-center.min-h-screen.bg-gray-100
      [:div.px-8.py-6.mt-4.text-left.bg-white.shadow-lg
       [:h3.text-2xl.font-bold.text-center (if retry? "Login failed, please retry" "Login to Slipway")]
       [:form {:method "POST" :action "j_security_check"}
        [:div.mt-4
         [:div
          [:label.block {:for "user"} "User"
           [:label [:input.w-full.px-4.py-2.mt-2.border.rounded-md.focus:outline-none.focus:ring-1.focus:ring-blue-600
                    {:name "j_username" :type "text" :autocomplete "off" :autocapitalize "off" :autocorrect "off" :spellcheck "false" :autofocus "autofocus"}]]]]
         [:div.mt-4
          [:label.block {:for "password"} "Password"
           [:label [:input.w-full.px-4.py-2.mt-2.border.rounded-md.focus:outline-none.focus:ring-1.focus:ring-blue-600
                    {:name "j_password" :type "password"}]]]]
         [:div.flex.items-baseline.justify-between
          [:button.px-6.py-2.mt-4.text-white.bg-blue-600.rounded-lg.hover:bg-blue-900 {:type "submit"} "Login"]]]]]]])))

(defn error-content
  [code text]
  (hiccup/html
   (hiccup.page/html5
    {:lang "en"}
    [:head
     [:title (str text " | Slipway Demo")]
     [:meta {:charset "UTF-8"}]
     [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
     [:meta {:name "description" :content "A Clojure companion for Jetty and Websockets by Factor House"}]
     [:link {:href "css/tailwind.min.css" :rel "stylesheet" :type "text/css"}]]
    [:body
     [:div.flex.items-center.justify-center.min-h-screen.bg-gray-100
      [:div.px-8.py-6.mt-4.text-left.bg-white.shadow-lg.divide-y
       [:div.pb-10 [:h3.text-2xl.font-bold.text-center "Slipway"]]
       [:div.pt-10 [:h3.text-2xl.font-bold.text-center (str code ": " text)]]]]])))

(def hello-html "<html><h1>Hello world</h1></html>")
(def login-html (login-content false))
(def login-retry-html (login-content true))
(def error-404-html (error-content 404 "Page Not Found"))
(def error-405-html (error-content 405 "Method Not Allowed"))
(def error-406-html (error-content 406 "Not Acceptable"))
(def error-application-html (error-content 500 "Application Error"))
(def error-server-html (error-content 500 "Server Error"))

(def up
  (constantly {:body "" :status 200 :headers {"Content-Type" "text/plain"}}))

(defn login
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    login-html})

(defn login-retry
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    login-retry-html})

(def logout
  (constantly {:status 302 :headers {"location" "/"} :session nil}))

(defn error-404
  [_]
  {:status  404
   :headers {"content-type" "text/html"}
   :body    error-404-html})

(defn error-405
  [_]
  {:status  405
   :headers {"content-type" "text/html"}
   :body    error-405-html})

(defn error-406
  [_]
  {:status  406
   :headers {"content-type" "text/html"}
   :body    error-406-html})

(defn error-application
  [_]
  {:status  500
   :headers {"content-type" "text/html"}
   :body    error-application-html})

(defn hello
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    hello-html})