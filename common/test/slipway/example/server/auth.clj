(ns slipway.example.server.auth
  (:require [clojure.test :refer :all]
            [hiccup.core :as hiccup]
            [hiccup.page :as hiccup.page]
            [reitit.ring :as reitit.ring]
            [slipway.common.auth.constraints :as constraints]
            [slipway.example.handler :as handler]
            [slipway.server :as slipway]))

(defn login-page
  [retry?]
  {:status  200
   :headers {"Content-Type" "text/html"}
   :body    (hiccup/html
             (hiccup.page/html5
              {:lang "en"}
              [:head
               [:title "Login | Slipway Demo"]
               [:meta {:charset "UTF-8"}]
               [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
               [:meta {:name "description" :content "A Jetty Ring adapter by the team at Factor House"}]
               [:link {:href "css/tailwind.min.css" :rel "stylesheet" :type "text/css"}]]
              [:body
               [:div.flex.items-center.justify-center.min-h-screen.bg-gray-100
                [:div.px-8.py-6.mt-4.text-left.bg-white.shadow-lg
                 [:h3.text-2xl.font-bold.text-center (if retry? "Login failed, please retry" "Login to Slipway")]
                 [:form {:method "POST" :action "j_security_check"}
                  [:div.mt-4
                   [:div
                    [:label.block {:for "user"} "User"
                     [:label [:input.w-full.px-4.py-2.mt-2.border.rounded-md.focus:outline-none.focus:ring-1.focus:ring-blue-600 {:name "j_username" :type "text" :autocomplete "off" :autocapitalize "off" :autocorrect "off" :spellcheck "false" :autofocus "autofocus"}]]]]
                   [:div.mt-4
                    [:label.block {:for "password"} "Password"
                     [:label [:input.w-full.px-4.py-2.mt-2.border.rounded-md.focus:outline-none.focus:ring-1.focus:ring-blue-600 {:name "j_password" :type "password"}]]]]
                   [:div.flex.items-baseline.justify-between
                    [:button.px-6.py-2.mt-4.text-white.bg-blue-600.rounded-lg.hover:bg-blue-900 {:type "submit"} "Login"]]]]]]]))})

(defn error-body
  [code]
  (hiccup/html
   (hiccup.page/html5
    {:lang "en"}
    [:head
     [:title (str (case code
                    404 "Page Not Found"
                    405 "Method Not Allowed"
                    406 "Not Acceptable"
                    500 "System Error"
                    "Error") " | Slipway Demo")]
     [:meta {:charset "UTF-8"}]
     [:meta {:content "width=device-width, initial-scale=1, shrink-to-fit=no" :name "viewport"}]
     [:meta {:name "description" :content "A Jetty Ring adapter by the team at Factor House"}]]
    [:body
     [:p (case code
           404 "404: Page Not Found"
           405 "405: Method Not Allowed"
           406 "406: Not Acceptable"
           500 "500: System Error"
           "Error")]])))

(defn error-page
  [code _]
  {:status  code
   :headers {"content-type" "text/html"}
   :body    (error-body code)})

(def routes
  [""
   ["/up" {:get {:handler (constantly {:body "" :status 200 :headers {"Content-Type" "text/plain"}})}}]
   ["/login" {:get {:handler (fn [_] (login-page false))}}]
   ["/login-retry" {:get {:handler (fn [_] (login-page true))}}]
   ["/logout" {:get {:handler (constantly {:status 302 :headers {"location" "/"} :session nil})}}]
   ["/" {:get {:handler handler/hello}}]])

;; Start a REPL with the following JVM opt:
;;  Hash User Auth: -Djava.security.auth.login.config=common/dev-resources/jaas/hash-jaas.conf or
;;  LDAP Auth:      -Djava.security.auth.login.config=common/dev-resources/jaas/ldap-jaas.conf
(def opts
  {:auth {:realm               "slipway"
          :login-uri           "/login"
          :logout-uri          "/logout"
          :login-retry-uri     "/login-retry"
          :post-login-uri-attr "org.eclipse.jetty.security.form_URI"
          :auth-method         "form"
          :auth-type           "jaas"
          :constraint-mappings (constraints/constraint-mappings
                                ["/up" (constraints/no-auth)]
                                ["/css/*" (constraints/no-auth)]
                                ["/img/*" (constraints/no-auth)]
                                ["/favicon.ico" (constraints/no-auth)]
                                ["/*" (constraints/form-auth-any-constraint)])}}) ;; first constraint wins, so this applies auth to anything not explicitly listed above

(defn handler
  []
  (reitit.ring/ring-handler
   (reitit.ring/router routes)
   (reitit.ring/routes
    (reitit.ring/create-resource-handler {:path "/"})
    (reitit.ring/create-default-handler
     {:not-found          (partial error-page 404)
      :method-not-allowed (partial error-page 405)
      :not-acceptable     (partial error-page 406)}))))

(defn server
  []
  (slipway/run-jetty (handler) opts))