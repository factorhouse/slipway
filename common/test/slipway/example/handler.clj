(ns slipway.example.handler
  (:require [clojure.test :refer :all]
            [hiccup.core :as hiccup]
            [hiccup.page :as hiccup.page]))

(def hello-html "<html><h1>Hello world</h1></html>")

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

(defn page-html
  [selected]
  (hiccup/html
   (hiccup.page/html5
    {:class "h-full bg-gray-50" :lang "en"}
    [:head
     [:title "Home | Slipway Demo"]
     [:meta {:charset "UTF-8"}]
     [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
     [:meta {:name "description" :content "A Clojure companion for Jetty by Factor House"}]
     [:link {:href "css/tailwind.min.css" :rel "stylesheet" :type "text/css"}]]
    [:body/h-full
     [:div.min-h-full
      [:nav.bg-white.shadow-sm
       [:div.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
        [:div.flex.justify-between.h-16
         [:div.flex
          [:div.flex-shrink-0.flex.items-center
           [:img.block.lg:hidden.h-8.w-auto {:src "img/fh-icon.png" :alt "Workflow"}]
           [:img.hidden.lg:block.h-8.w-auto {:src "img/fh-icon.png" :alt "Workflow"}]]
          [:div.hidden.sm:-my-px.sm:ml-6.sm:flex.sm:space-x-8
           [:a.border-indigo-500.text-gray-900.inline-flex.items-center.px-1.pt-1.border-b-2.text-sm.font-medium (cond-> {:href "#"} (= :home selected) (assoc :aria-current "page")) "Home"]
           [:a.border-transparent.text-gray-500.hover:text-gray-700.hover:border-gray-300.inline-flex.items-center.px-1.pt-1.border-b-2.text-sm.font-medium {:href "#"} "404"]
           [:a.border-transparent.text-gray-500.hover:text-gray-700.hover:border-gray-300.inline-flex.items-center.px-1.pt-1.border-b-2.text-sm.font-medium {:href "#"} "405"]
           [:a.border-transparent.text-gray-500.hover:text-gray-700.hover:border-gray-300.inline-flex.items-center.px-1.pt-1.border-b-2.text-sm.font-medium {:href "#"} "406"]
           [:a.border-transparent.text-gray-500.hover:text-gray-700.hover:border-gray-300.inline-flex.items-center.px-1.pt-1.border-b-2.text-sm.font-medium {:href "#"} "500"]]]
         [:div.hidden.sm:ml-6.sm:flex.sm:items-center
          [:button.bg-white.p-1.rounded-full.text-gray-400.hover:text-gray-500.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500 {:type "button"}
           [:span.sr-only "View notifications"]
           [:svg.h-6.w-6 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor" :aria-hidden "true"}
            [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M14.857 17.082a23.848 23.848 0 005.454-1.31A8.967 8.967 0 0118 9.75v-.7V9A6 6 0 006 9v.75a8.967 8.967 0 01-2.312 6.022c1.733.64 3.56 1.085 5.455 1.31m5.714 0a24.255 24.255 0 01-5.714 0m5.714 0a3 3 0 11-5.714 0"}]]]
          [:div.ml-3.relative
           [:div
            [:button#user-menu-button.max-w-xs.bg-white.flex.items-center.text-sm.rounded-full.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500 {:type "button" :aria-expanded "false" :aria-haspopup "true"}
             [:span.sr-only "Open user menu"]
             [:svg.w-6.h-6 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor"}
              [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M15.75 6a3.75 3.75 0 11-7.5 0 3.75 3.75 0 017.5 0zM4.501 20.118a7.5 7.5 0 0114.998 0A17.933 17.933 0 0112 21.75c-2.676 0-5.216-.584-7.499-1.632z"}]]]]]]
         [:div.-mr-2.flex.items-center.sm:hidden
          [:button.bg-white.inline-flex.items-center.justify-center.p-2.rounded-md.text-gray-400.hover:text-gray-500.hover:bg-gray-100.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500 \
           {:type "button" :aria-controls "mobile-menu" :aria-expanded "false"}
           [:span.sr-only "Open main menu"]
           [:svg.block.h-6.w-6 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor" :aria-hidden "true"}
            [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M3.75 6.75h16.5M3.75 12h16.5m-16.5 5.25h16.5"}]]
           [:svg.hidden.h-6.w-6 {:xmlns "http://www.w3.org/2000/svg" :fill "none" :viewBox "0 0 24 24" :stroke-width "1.5" :stroke "currentColor" :aria-hidden "true"}
            [:path {:stroke-linecap "round" :stroke-linejoin "round" :d "M6 18L18 6M6 6l12 12"}]]]]]]]
      [:div.py-10
       [:header
        [:div.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
         [:h1.text-3xl.tracking-tight.font-bold.leading-tight.text-gray-900 "Home"]]]
       [:main
        [:div.max-w-7xl.mx-auto.sm:px-6.lg:px-8
         [:div.px-4.py-8.sm:px-0
          [:div.border-4.border-dashed.border-gray-200.rounded-lg.h-96]]]]]]])))

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
   :body    (page-html :home)})

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