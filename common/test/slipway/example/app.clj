(ns slipway.example.app
  (:refer-clojure :exclude [error-handler])
  (:require [clojure.string :as str]
            [clojure.tools.logging :as log]
            [hiccup.core :as hiccup]
            [hiccup.page :as hiccup.page]
            [reitit.ring :as reitit]
            [ring.middleware.anti-forgery :as ring.forgery]
            [ring.middleware.keyword-params :as ring.params.kw]
            [ring.middleware.params :as ring.params]
            [ring.middleware.session :as ring.session]
            [ring.middleware.session.memory :as ring.session.memory]
            [slipway.sente :as sente]
            [slipway.user :as user]))

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
     [:link {:rel "icon" :type "image/png" :sizes "64x64" :href "/img/sw-icon-zinc.png"}]
     [:link {:href "css/tailwind.min.css" :rel "stylesheet" :type "text/css"}]]
    [:body.h-full
     [:div.min-h-full.flex.items-center.justify-center.py-12.px-4.sm:px-6.lg:px-8
      [:div.max-w-md.w-full.space-y-8
       [:div.mb-16 [:img.mx-auto.h-24.w-auto {:src "img/sw-logo-zinc.png" :alt "Factor House"}]]
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
         [:button.group.relative.w-full.flex.justify-center.mt-12.py-2.px-4.border.border-transparent.text-sm.font-medium.rounded-md.text-white.bg-indigo-600.hover:bg-indigo-700.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500
          {:type "submit"}
          [:span.absolute.left-0.inset-y-0.flex.items-center.pl-3
           [:svg.h-5.w-5.text-indigo-500.group-hover:text-indigo-400 {:xmlns "http://www.w3.org/2000/svg" :viewBox "0 0 20 20" :fill "currentColor" :aria-hidden "true"}
            [:path {:fill-rule "evenodd" :d "M10 1a4.5 4.5 0 00-4.5 4.5V9H5a2 2 0 00-2 2v6a2 2 0 002 2h10a2 2 0 002-2v-6a2 2 0 00-2-2h-.5V5.5A4.5 4.5 0 0010 1zm3 8V5.5a3 3 0 10-6 0V9h6z" :clip-rule "evenodd"}]]] "Sign in"]]]]]])))

(defn menu-item
  [href text selected?]
  (if selected?
    [:a.border-indigo-500.text-gray-900.inline-flex.items-center.px-1.pt-1.border-b-2.text-sm.font-medium {:aria-current "page" :href href} text]
    [:a.border-transparent.text-gray-500.hover:text-gray-700.hover:border-gray-300.inline-flex.items-center.px-1.pt-1.border-b-2.text-sm.font-medium {:href href} text]))

(defn menu
  [selected]
  [:nav.bg-white.shadow-sm
   [:div.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
    [:div.flex.justify-between.h-16
     [:div.flex
      [:div.flex-shrink-0.flex.items-center
       [:img.block.lg:hidden.h-8.w-auto {:src "img/sw-icon-zinc.png" :alt "Factor House"}]
       [:img.hidden.lg:block.h-8.w-auto {:src "img/sw-icon-zinc.png" :alt "Factor House"}]]
      [:div.hidden.sm:-my-px.sm:ml-6.sm:flex.sm:space-x-8
       (menu-item "/" "Home" (= :home selected))
       (menu-item "/user" "User" (= :user selected))
       (menu-item "/404" "404" false)
       (menu-item "/405" "405" false)
       (menu-item "/406" "406" false)
       (menu-item "/500" "500" false)
       (menu-item "/logout" "Logout" false)]]]]])

(defn home-html
  []
  (hiccup/html
   (hiccup.page/html5
    {:class "h-full bg-gray-50" :lang "en"}
    [:head
     [:title "Home | Slipway Demo"]
     [:meta {:charset "UTF-8"}]
     [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
     [:meta {:name "description" :content "A Clojure companion for Jetty by Factor House"}]
     [:link {:rel "icon" :type "image/png" :sizes "64x64" :href "/img/sw-icon-zinc.png"}]
     [:link {:href "css/tailwind.min.css" :rel "stylesheet" :type "text/css"}]]
    [:body.h-full
     ;; Note, this HTML must be dynamically produced within the scope of the wrap-anti-forgery middleware
     ;;       as otherwise this anti-forgery-token is unbound (ie. this cannot be static hiccup)
     [:div#sente-csrf-token {:data-csrf-token (force ring.forgery/*anti-forgery-token*)}]
     [:div.min-h-full
      (menu :home)
      [:div.py-10
       [:header
        [:div.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
         [:h1.text-3xl.tracking-tight.font-bold.leading-tight.text-gray-900 "Home"]]]
       [:main
        [:div.max-w-7xl.mx-auto.sm:px-6.lg:px-8
         [:div.px-4.py-8.sm:px-0
          [:div.border-4.border-dashed.border-gray-200.rounded-lg.h-96]]]]]]])))

(defn user-html
  [req]
  (hiccup/html
   (hiccup.page/html5
    {:class "h-full bg-gray-50" :lang "en"}
    [:head
     [:title "User | Slipway Demo"]
     [:meta {:charset "UTF-8"}]
     [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
     [:meta {:name "description" :content "A Clojure companion for Jetty by Factor House"}]
     [:link {:rel "icon" :type "image/png" :sizes "64x64" :href "/img/sw-icon-zinc.png"}]
     [:link {:href "css/tailwind.min.css" :rel "stylesheet" :type "text/css"}]]
    [:body.h-full
     [:div.min-h-full
      (menu :user)
      [:div.py-10
       [:header
        [:div.max-w-7xl.mx-auto.px-4.sm:px-6.lg:px-8
         [:h1.text-3xl.tracking-tight.font-bold.leading-tight.text-gray-900 "User"]]]
       [:main
        [:div.max-w-7xl.mx-auto.sm:px-6.lg:px-8
         [:div.px-4.py-8.sm:px-0
          [:div.mt-5.border-t.border-gray-200
           [:dl.divide-y.divide-gray-200
            [:div.py-4.sm:py-5.sm:grid.sm:grid-cols-3.sm:gap-4
             [:dt.text-sm.font-medium.text-gray-500 "Username"]
             [:dd.mt-1.flex.text-sm.text-gray-900.sm:mt-0.sm:col-span-2
              [:span.flex-grow (user/name req)]]]
            [:div.py-4.sm:py-5.sm:grid.sm:grid-cols-3.sm:gap-4
             [:dt.text-sm.font-medium.text-gray-500 "Roles"]
             [:dd.mt-1.flex.text-sm.text-gray-900.sm:mt-0.sm:col-span-2
              [:span.flex-grow (str/join ", " (user/roles req))]]]]]]]]]]])))

(defn error-html
  [code text]
  (hiccup/html
   (hiccup.page/html5
    {:class "h-full bg-gray-50" :lang "en"}
    [:head
     [:title "Home | Slipway Demo"]
     [:meta {:charset "UTF-8"}]
     [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
     [:meta {:name "description" :content "A Clojure companion for Jetty by Factor House"}]
     [:link {:rel "icon" :type "image/png" :sizes "64x64" :href "/img/sw-icon-zinc.png"}]
     [:link {:href "css/tailwind.min.css" :rel "stylesheet" :type "text/css"}]]
    [:body.h-full
     [:div.min-h-full.px-4.py-16.sm:px-6.sm:py-24.md:grid.md:place-items-center.lg:px-8
      [:div.max-w-max.mx-auto
       [:main.sm:flex
        [:p.text-4xl.tracking-tight.font-bold.text-indigo-600.sm:text-5xl code]
        [:div.sm:ml-6
         [:div.sm:border-l.sm:border-gray-200.sm:pl-6
          [:h1.text-4xl.font-bold.text-gray-900.tracking-tight.sm:text-5xl text]]
         [:div.mt-10.flex.space-x-3.sm:border-l.sm:border-transparent.sm:pl-6
          [:a.inline-flex.items-center.px-4.py-2.border.border-transparent.text-sm.font-medium.rounded-md.shadow-sm.text-white.bg-indigo-600.hover:bg-indigo-700.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500 {:href "/"} "Home"]
          [:a.inline-flex.items-center.px-4.py-2.border.border-transparent.text-sm.font-medium.rounded-md.text-indigo-700.bg-indigo-100.hover:bg-indigo-200.focus:outline-none.focus:ring-2.focus:ring-offset-2.focus:ring-indigo-500 {:href "#"} "Contact Support"]]]]]]])))

(def up (constantly {:body "" :status 200 :headers {"Content-Type" "text/plain"}}))

(defn login-handler
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (login-html false)})

(defn login-retry-handler
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (login-html true)})

(defn logout-handler
  [req]
  (user/logout req)
  {:status  302
   :headers {"location" "/"}
   :session nil})

(defn home-handler
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (home-html)})

(defn user-handler
  [req]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (user-html req)})

(defn error-404-handler
  [_]
  {:status  404
   :headers {"Content-Type" "text/html"}
   :body    (error-html 404 "Page Not Found")})

(defn error-405-handler
  [_]
  {:status  405
   :headers {"Content-Type" "text/html"}
   :body    (error-html 405 "Method Not Allowed")})

(defn error-406-handler
  [_]
  {:status  406
   :headers {"Content-Type" "text/html"}
   :body    (error-html 406 "Not Acceptable")})

(defn error-handler
  [_]
  {:status  500
   :headers {"Content-Type" "text/html"}
   :body    (error-html 500 "Application Error")})

(defn error-route-handler
  [_]
  (throw (RuntimeException. "Error Route")))

(defn hello-handler
  [_]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    hello-html})

(defn routes
  [sente]
  [""
   ["/up" {:get {:handler up}}]
   ["/login" {:get {:handler login-handler}}]
   ["/login-retry" {:get {:handler login-retry-handler}}]
   ["/logout" {:get {:handler logout-handler}}]
   ["/" {:get {:handler home-handler}}]
   ["/user" {:get {:handler user-handler}}]
   ["/chsk" {:get {:handler (:ws-handshake sente)}}]
   ["/405" {:get {:handler error-405-handler}}]
   ["/406" {:get {:handler error-406-handler}}]
   ["/500" {:get {:handler error-route-handler}}]])

(def error-handlers
  {:not-found          error-404-handler
   :method-not-allowed error-405-handler
   :not-acceptable     error-406-handler})

(defn wrap-errors
  [handler]
  (fn [req]
    (try (handler req)
         (catch Throwable ex
           (log/errorf ex "application error %s" (:uri req))
           (error-handler ex)))))

(defn handler
  []
  (let [sente-config   {:allowed-origins #{"http://localhost:3000"}}
        session-config {:store (ring.session.memory/memory-store)}]
    (-> (reitit/ring-handler
         (reitit/router (routes (sente/start-server sente-config)))
         (reitit/routes
          (reitit/create-resource-handler {:path "/"})
          (reitit/create-default-handler error-handlers)))
        (wrap-errors)
        (ring.forgery/wrap-anti-forgery)
        (ring.session/wrap-session session-config)
        (ring.params.kw/wrap-keyword-params)
        (ring.params/wrap-params))))