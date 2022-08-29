(ns slipway.example.handler
  (:require [clojure.test :refer :all]
            [hiccup.core :as hiccup]
            [hiccup.page :as hiccup.page]))

(defn login-html
  [retry?]
  (hiccup/html
   (hiccup.page/html5
    {:class "h-full bg-gray-50" :lang "en"}
    [:head
     [:title "Login | Slipway Demo"]
     [:meta {:charset "UTF-8"}]
     [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
     [:meta {:name "description" :content "A Clojure companion for Jetty by Factor House"}]
     [:link {:href "css/tailwind.min.css" :rel "stylesheet" :type "text/css"}]]
    [:body.h-full
     [:div.min-h-full.flex.items-center.justify-center.py-12.px-4.sm:px-6.lg:px-8
      [:div.max-w-md.w-full.space-y-8
       [:div
        [:a {:href "https://factorhouse.io" :target "_blank"}
         [:img.mx-auto.h-64.w-auto {:src "img/fh-logo.png" :alt "Factor House"}]]]
       [:form.mt-8.space-y-6 {:method "POST" :action "j_security_check"}
        [:div.rounded-md.shadow-sm.-space-y-px
         [:div
          [:label.sr-only {:for "username"} "User"]
          [:input#username.appearance-none.rounded-none.relative.block.w-full.px-3.py-2.border.border-gray-300.placeholder-gray-500.text-gray-900.rounded-t-md.focus:outline-none.focus:ring-indigo-500.focus:border-indigo-500.focus:z-10.sm:text-sm
           {:name "j_username" :type "text" :autocomplete "off" :autocapitalize "off" :autocorrect "off" :spellcheck "false" :autofocus "autofocus" :placeholder "User"}]]
         [:div
          [:label.sr-only {:for "password"} "Password"]
          [:input#password.appearance-none.rounded-none.relative.block.w-full.px-3.py-2.border.border-gray-300.placeholder-gray-500.text-gray-900.rounded-b-md.focus:outline-none.focus:ring-indigo-500.focus:border-indigo-500.focus:z-10.sm:text-sm
           {:name "j_password" :type "password" :placeholder "Password"}]]]
        [:div.flex.items-center.justify-between
         [:div.text-sm.items (when retry? [:span.font-small.text.text-red-900 "Login failed, please retry."])]
         [:div.text-sm.items
          [:a.font-medium.text-indigo-600.hover:text-indigo-500 {:href "#"} "Forgot your password?"]]]
        [:div
         [:button.group.relative.w-full.flex.justify-center.py-2.px-4.border.border-transparent.text-sm.font-medium.rounded-md.text-white.bg-indigo-600.hover:bg-indigo-700.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500
          {:type "submit"}
          [:span.absolute.left-0.inset-y-0.flex.items-center.pl-3
           [:svg.h-5.w-5.text-indigo-500.group-hover:text-indigo-400 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :aria-hidden "true"}
            [:path {:fill-rule "evenodd" :d "M10 1a4.5 4.5 0 00-4.5 4.5V9H5a2 2 0 00-2 2v6a2 2 0 002 2h10a2 2 0 002-2v-6a2 2 0 00-2-2h-.5V5.5A4.5 4.5 0 0010 1zm3 8V5.5a3 3 0 10-6 0V9h6z" :clip-rule "evenodd"}]]] "Sign in"]]]]]])))

(defn error-html
  [code text]
  (hiccup/html
   (hiccup.page/html5 {:lang "en"}
                      [:head
                       [:title (str text " | Slipway Demo")]
                       [:meta {:charset "UTF-8"}]
                       [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
                       [:meta {:name "description" :content "A Clojure companion for Jetty by Factor House"}]
                       [:link {:href "css/tailwind.min.css" :rel "stylesheet" :type "text/css"}]]
                      [:body
                       [:div.flex.items-center.justify-center.min-h-screen.bg-gray-100
                        [:div.px-8.py-6.mt-4.text-left.bg-white.shadow-lg.divide-y
                         [:div.pb-12 [:h3.text-2xl.font-bold.text-center "Slipway"]]
                         [:div.pt-12 [:h3.text-2xl.font-bold.text-center (str code ": " text)]]]]])))

(def home-html (hiccup/html
                (hiccup.page/html5
                 {:lang "en"}
                 [:head
                  [:title "Home | Slipway Demo"]
                  [:meta {:charset "UTF-8"}]
                  [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
                  [:meta {:name "description" :content "A Clojure companion for Jetty by Factor House"}]
                  [:link {:href "css/tailwind.min.css" :rel "stylesheet" :type "text/css"}]]
                 [:body
                  [:div.flex.items-center.justify-center.min-h-screen.bg-gray-400
                   [:nav.flex.items-center.justify-between.flex-wrap.bg-teal-500.p-6
                    [:div.flex.items-center.flex-shrink-0.text-white.mr-6
                     [:svg.fill-current.h-8.w-8.mr-2 {:width "54" :height "54" :viewBox "0 0 54 54" :xmlns "http://www.w3.org/2000/svg"} [:path {:d "M13.5 22.1c1.8-7.2 6.3-10.8 13.5-10.8 10.8 0 12.15 8.1 17.55 9.45 3.6.9 6.75-.45 9.45-4.05-1.8 7.2-6.3 10.8-13.5 10.8-10.8 0-12.15-8.1-17.55-9.45-3.6-.9-6.75.45-9.45 4.05zM0 38.3c1.8-7.2 6.3-10.8 13.5-10.8 10.8 0 12.15 8.1 17.55 9.45 3.6.9 6.75-.45 9.45-4.05-1.8 7.2-6.3 10.8-13.5 10.8-10.8 0-12.15-8.1-17.55-9.45-3.6-.9-6.75.45-9.45 4.05z"}]]
                     [:span.font-semibold.text-xl.tracking-tight "Tailwind CSS"]]
                    [:div.block.lg:hidden
                     [:button.flex.items-center.px-3.py-2.border.rounded.text-teal-200.border-teal-400.hover:text-white.hover:border-white
                      [:svg.fill-current.h-3.w-3 {:viewBox "0 0 20 20" :xmlns "http://www.w3.org/2000/svg"} [:title "Menu"] [:path {:d "M0 3h20v2H0V3zm0 6h20v2H0V9zm0 6h20v2H0v-2z"}]]]]
                    [:div.w-full.block.flex-grow.lg:flex.lg:items-center.lg:w-auto
                     [:div.text-sm.lg:flex-grow
                      [:a.block.mt-4.lg:inline-block.lg:mt-0.text-teal-200.hover:text-white.mr-4 {:href "#responsive-header"} "Docs"]
                      [:a.block.mt-4.lg:inline-block.lg:mt-0.text-teal-200.hover:text-white.mr-4 {:href "#responsive-header"} "Examples"]
                      [:a.block.mt-4.lg:inline-block.lg:mt-0.text-teal-200.hover:text-white {:href "logout"} "Logout"]]
                     [:div
                      [:a.inline-block.text-sm.px-4.py-2.leading-none.border.rounded.text-white.border-white.hover:border-transparent.hover:text-teal-500.hover:bg-teal-400.mt-4.lg:mt-0 {:href "logout"} "logout"]]]]]])))

(def hello-html "<html><h1>Hello world</h1></html>")
(def error-404-html (error-html 404 "Page Not Found"))
(def error-405-html (error-html 405 "Method Not Allowed"))
(def error-406-html (error-html 406 "Not Acceptable"))
(def error-application-html (error-html 500 "Application Error"))
(def error-server-html (error-html 500 "Server Error"))

(def up (constantly {:body "" :status 200 :headers {"Content-Type" "text/plain"}}))
(def logout (constantly {:status 302 :headers {"location" "/"} :session nil}))

(defn login
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (login-html false)})

(defn login-retry
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (login-html true)})

(defn home
  [_]
  {:status  200
   :headers {"content-type" "text/html"}
   :body    home-html})

(defn error-404
  [_]
  {:status  404
   :headers {"content-type" "text/html"}
   :body    (error-html 404 "Page Not Found")})

(defn error-405
  [_]
  {:status  405
   :headers {"content-type" "text/html"}
   :body    (error-html 405 "Method Not Allowed")})

(defn error-406
  [_]
  {:status  406
   :headers {"content-type" "text/html"}
   :body    (error-html 406 "Not Acceptable")})

(defn error-application
  [_]
  {:status  500
   :headers {"content-type" "text/html"}
   :body    (error-html 500 "Application Error")})

(defn error-server
  [_]
  {:status  500
   :headers {"content-type" "text/html"}
   :body    (error-html 500 "Server Error")})

(defn hello
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    hello-html})